package com.hydrogen.ai.network

import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import com.hydrogen.ai.model.SystemPromptPolicy
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Local chat templates without a system role (Gemma 2 in LM Studio, older Mistral) refuse the whole request.
 * Orbit always sends one system message now, so the relay must fold it into the first user turn for them.
 */
class RelaySystemRoleFallbackTest {

    private val requests: MutableList<List<JsonObject>> = Collections.synchronizedList(mutableListOf())
    private var server: HttpServer? = null

    @After
    fun tearDown() {
        server?.stop(0)
    }

    private fun reply(ex: HttpExchange, status: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(status, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private val okReply = """{"choices":[{"message":{"role":"assistant","content":"Hello"},"finish_reason":"stop"}]}"""

    /** Starts a fake OpenAI-compatible server; [refuseSystem] makes it answer like a template with no system role. */
    private fun startServer(refuseSystem: Boolean, refusal: String = TEMPLATE_REFUSAL): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.executor = Executors.newSingleThreadExecutor()
        s.createContext("/v1/chat/completions") { ex ->
            val body = Json.parseToJsonElement(ex.requestBody.readBytes().toString(Charsets.UTF_8)).jsonObject
            val messages = body["messages"]!!.jsonArray.map { it.jsonObject }
            requests += messages
            val hasSystem = messages.any { it["role"]?.jsonPrimitive?.contentOrNull == "system" }
            if (refuseSystem && hasSystem) reply(ex, 400, refusal) else reply(ex, 200, okReply)
        }
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}"
    }

    private fun request(model: String) = ChatDispatchRequest(
        correlationId = "c1",
        modelId = model,
        messages = listOf(
            ChatMessage(
                id = SystemPromptPolicy.MESSAGE_ID,
                correlationId = SystemPromptPolicy.MESSAGE_ID,
                role = MessageRole.SYSTEM,
                content = "Be brief.",
                createdAtEpochMs = 1L
            ),
            ChatMessage(id = "u1", correlationId = "c1", role = MessageRole.USER, content = "Hi", createdAtEpochMs = 2L),
            ChatMessage(id = "a1", correlationId = "c1", role = MessageRole.ASSISTANT, content = "Hey", createdAtEpochMs = 3L),
            ChatMessage(id = "u2", correlationId = "c2", role = MessageRole.USER, content = "Again", createdAtEpochMs = 4L)
        ),
        attachments = emptyList(),
        stream = false
    )

    private fun send(backend: HttpGeminiHydrogenRelayBackend, baseUrl: String, model: String): ChatDispatchResult =
        runBlocking {
            backend.sendChat(
                request(model),
                RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, baseUrl, model, streamingEnabled = false),
                null
            ) { }
        }

    private fun roles(messages: List<JsonObject>) = messages.map { it["role"]!!.jsonPrimitive.content }
    private fun contentOf(message: JsonObject) = message["content"]!!.jsonPrimitive.content

    @Test
    fun modelThatRefusesASystemMessageGetsOneFoldedRetryAndIsRemembered() {
        val baseUrl = startServer(refuseSystem = true)
        val backend = HttpGeminiHydrogenRelayBackend()

        val first = send(backend, baseUrl, "mistral-7b-instruct-v0.1")
        assertTrue("the folded retry should succeed: $first", first is ChatDispatchResult.Success)
        assertEquals("one refused send, then one folded send", 2, requests.size)
        assertEquals(listOf("system", "user", "assistant", "user"), roles(requests[0]))
        assertEquals(listOf("user", "assistant", "user"), roles(requests[1]))
        assertEquals("Be brief.\n\nHi", contentOf(requests[1][0]))
        assertEquals("later turns are untouched", "Again", contentOf(requests[1][2]))

        // The same server and model now get the folded form straight away, with no refused round trip.
        val second = send(backend, baseUrl, "mistral-7b-instruct-v0.1")
        assertTrue(second is ChatDispatchResult.Success)
        assertEquals(3, requests.size)
        assertEquals(listOf("user", "assistant", "user"), roles(requests[2]))
    }

    @Test
    fun gemmaOnALocalServerIsFoldedUpFront() {
        val baseUrl = startServer(refuseSystem = true)
        val result = send(HttpGeminiHydrogenRelayBackend(), baseUrl, "google/gemma-2-9b-it")

        assertTrue(result is ChatDispatchResult.Success)
        assertEquals("no refused round trip for Gemma", 1, requests.size)
        assertEquals(listOf("user", "assistant", "user"), roles(requests[0]))
        assertEquals("Be brief.\n\nHi", contentOf(requests[0][0]))
    }

    @Test
    fun modelsThatAcceptSystemKeepIt() {
        val baseUrl = startServer(refuseSystem = false)
        val result = send(HttpGeminiHydrogenRelayBackend(), baseUrl, "qwen2.5-7b-instruct")

        assertTrue(result is ChatDispatchResult.Success)
        assertEquals(1, requests.size)
        assertEquals(listOf("system", "user", "assistant", "user"), roles(requests[0]))
        assertEquals("Hi", contentOf(requests[0][1]))
    }

    @Test
    fun otherBadRequestsAreNotRetried() {
        val baseUrl = startServer(refuseSystem = true, refusal = """{"error":"Context length exceeded"}""")
        val result = send(HttpGeminiHydrogenRelayBackend(), baseUrl, "qwen2.5-7b-instruct")

        assertTrue(result is ChatDispatchResult.Failure)
        assertEquals("a refusal that is not about the system role is shown, not retried", 1, requests.size)
    }

    @Test
    fun recognisesTemplateRefusals() {
        assertTrue(HttpGeminiHydrogenRelayBackend.rejectsSystemRole(400, TEMPLATE_REFUSAL))
        assertTrue(
            HttpGeminiHydrogenRelayBackend.rejectsSystemRole(
                500, """{"error":"Conversation roles must alternate user/assistant/user/assistant/..."}"""
            )
        )
        assertFalse(HttpGeminiHydrogenRelayBackend.rejectsSystemRole(400, """{"error":"Context length exceeded"}"""))
        assertFalse(HttpGeminiHydrogenRelayBackend.rejectsSystemRole(200, TEMPLATE_REFUSAL))
        assertFalse(HttpGeminiHydrogenRelayBackend.rejectsSystemRole(null, TEMPLATE_REFUSAL))
        assertFalse(HttpGeminiHydrogenRelayBackend.rejectsSystemRole(503, """{"error":"system overloaded"}"""))
    }

    @Test
    fun foldingHandlesBlankTextAndLeavesSystemFreeRequestsAlone() {
        val req = request("x").let { r ->
            r.copy(messages = r.messages.map { if (it.id == "u1") it.copy(content = "") else it })
        }
        val folded = HttpGeminiHydrogenRelayBackend.withInstructionsInFirstUserTurn(req)
        assertEquals(listOf("u1", "a1", "u2"), folded.messages.map { it.id })
        assertEquals("Be brief.", folded.messages.first().content)

        val noSystem = req.copy(messages = req.messages.filter { it.role != MessageRole.SYSTEM })
        assertEquals(noSystem, HttpGeminiHydrogenRelayBackend.withInstructionsInFirstUserTurn(noSystem))
    }

    private companion object {
        const val TEMPLATE_REFUSAL =
            """{"error":"Error rendering prompt with jinja template: System role not supported"}"""
    }
}
