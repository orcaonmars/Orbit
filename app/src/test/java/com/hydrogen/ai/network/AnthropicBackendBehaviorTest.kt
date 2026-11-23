package com.hydrogen.ai.network

import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.util.Collections
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicBackendBehaviorTest {
    private val jargon = listOf("fabricated", "bearer", "schema", "payload", "upstream", "HTTP", "relay", "Relay")

    private fun msg(id: String, role: MessageRole, text: String) = ChatMessage(id, id, role, text, 1L)

    private fun assertPlain(message: String) {
        jargon.forEach { assertFalse("\"$it\" in: $message", message.contains(it)) }
        assertTrue(message, message.trimEnd().endsWith("."))
    }

    @Test fun adaptiveThinkingOnlyForModelsThatTakeIt() {
        listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5", "claude-fable-5-2", "claude-sonnet-4-6",
            "claude-opus-4-7", "claude-opus-5").forEach {
            assertTrue(it, HttpAnthropicMessagesBackend.supportsAdaptiveThinking(it))
        }
        listOf("claude-opus-4-20250514", "claude-sonnet-4-5", "claude-haiku-4-5", "claude-3-7-sonnet-20250219",
            "claude-3-haiku-20240307", "gpt-4o", "").forEach {
            assertFalse(it, HttpAnthropicMessagesBackend.supportsAdaptiveThinking(it))
        }
    }

    @Test fun replyLengthFitsOlderModels() {
        assertEquals(4_096, HttpAnthropicMessagesBackend.maxTokensFor("claude-3-haiku-20240307"))
        assertEquals(16_000, HttpAnthropicMessagesBackend.maxTokensFor("claude-3-7-sonnet-20250219"))
        assertEquals(16_000, HttpAnthropicMessagesBackend.maxTokensFor("claude-opus-5-5"))
    }

    @Test fun imageTypesTheFormatReads() {
        assertEquals("image/jpeg", HttpAnthropicMessagesBackend.normalizeImageType("image/jpg"))
        assertEquals("image/webp", HttpAnthropicMessagesBackend.normalizeImageType("IMAGE/WEBP"))
        assertNull(HttpAnthropicMessagesBackend.normalizeImageType("image/heic"))
    }

    @Test fun keyNeverGoesOverPlainHttpOrMissing() {
        val insecure = HttpAnthropicMessagesBackend.preflight("http://192.168.1.20:8080", "k", null)
        assertNotNull(insecure)
        assertPlain(insecure!!.userMessage)
        val noKey = HttpAnthropicMessagesBackend.preflight("https://api.anthropic.com", "", "Anthropic")
        assertEquals(ProviderErrorCategory.UNCONFIGURED, noKey!!.category)
        assertTrue(noKey.userMessage.contains("Anthropic"))
        assertPlain(noKey.userMessage)
        assertPlain(HttpAnthropicMessagesBackend.preflight("not a url", "k", null)!!.userMessage)
        assertNull(HttpAnthropicMessagesBackend.preflight("http://127.0.0.1:8080", "", null))
        assertNull(HttpAnthropicMessagesBackend.preflight("https://api.anthropic.com", "k", "Anthropic"))
    }

    @Test fun payloadMergesTurnsAndAsksForThinkingOnNewModels() = runBlocking {
        val request = ChatDispatchRequest(
            correlationId = "c",
            messages = listOf(
                msg("a0", MessageRole.ASSISTANT, "Hi, how can I help?"),
                msg("u1", MessageRole.USER, "One"),
                msg("u2", MessageRole.USER, "Two"),
                msg("a1", MessageRole.ASSISTANT, "Answer   ")
            ),
            attachments = emptyList()
        )
        val built = HttpAnthropicMessagesBackend().buildPayload(request, "claude-opus-5-5", safe = false)
        val turns = built.body!!["messages"]!!.jsonArray
        assertEquals(1, turns.size)
        assertEquals("user", turns[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals(2, turns[0].jsonObject["content"]!!.jsonArray.size)
        val thinking = built.body!!["thinking"]!!.jsonObject
        assertEquals("adaptive", thinking["type"]!!.jsonPrimitive.content)
        assertFalse(thinking.containsKey("budget_tokens"))
        assertFalse(built.body!!.containsKey("temperature"))

        val empty = HttpAnthropicMessagesBackend().buildPayload(
            request.copy(messages = listOf(msg("a", MessageRole.ASSISTANT, "Only me"))), "claude-opus-5-5", safe = false
        )
        assertNotNull(empty.error)
        assertPlain(empty.error!!.userMessage)
    }

    @Test fun streamErrorAndRefusalBecomePlainFailures() = runBlocking {
        val overloaded = HttpAnthropicMessagesBackend.readEventStream(
            ByteArrayInputStream(
                ("data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"Hmm\"}}\n\n" +
                    "data: {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}\n\n").toByteArray()
            ),
            "Anthropic"
        ) {}
        assertEquals("Hmm", overloaded.thinking)
        val failure = overloaded.toResult(200, "m", false, true, "Anthropic") as ChatDispatchResult.Failure
        assertEquals(ProviderErrorCategory.OVERLOADED, failure.error.category)
        assertTrue(failure.error.userMessage.contains("Anthropic"))
        assertPlain(failure.error.userMessage)

        val refused = HttpAnthropicMessagesBackend.parseCompleteReply("""{"content":[],"stop_reason":"refusal"}""", "Anthropic")
        val refusal = refused.toResult(200, "m", false, false, "Anthropic") as ChatDispatchResult.Failure
        assertEquals(ProviderErrorCategory.CONTENT_FILTERED, refusal.error.category)
        assertPlain(refusal.error.userMessage)
    }

    @Test fun streamThatEndsEarlyIsLabelledInterruptedAndKeepsText() = runBlocking {
        val seen = StringBuilder()
        val cut = HttpAnthropicMessagesBackend.readEventStream(
            ByteArrayInputStream(
                ("data: {\"type\":\"message_start\",\"message\":{}}\n\n" +
                    "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"The first half\"}}\n\n")
                    .toByteArray()
            ),
            "Anthropic"
        ) { seen.append(it.textDelta) }
        assertEquals("The first half", cut.text)
        assertEquals("The first half", seen.toString())
        assertTrue(cut.interrupted)
        val failure = cut.toResult(200, "m", false, true, "Anthropic") as ChatDispatchResult.Failure
        assertEquals(ProviderErrorCategory.INTERRUPTED, failure.error.category)
        assertTrue(failure.error.userMessage.contains("cut off"))
        assertPlain(failure.error.userMessage)

        // A stream that finishes properly is still a success.
        val whole = HttpAnthropicMessagesBackend.readEventStream(
            ByteArrayInputStream(
                ("data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"All of it\"}}\n\n" +
                    "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}\n\n" +
                    "data: {\"type\":\"message_stop\"}\n\n").toByteArray()
            ),
            "Anthropic"
        ) {}
        assertTrue(!whole.interrupted)
        assertTrue(whole.toResult(200, "m", false, true, "Anthropic") is ChatDispatchResult.Success)
    }

    @Test fun successKeepsThinkingButNoSignature() {
        val reply = HttpAnthropicMessagesBackend.parseCompleteReply(
            """{"content":[{"type":"thinking","thinking":"Plan","signature":"sig"},{"type":"text","text":"Done"}],"stop_reason":"max_tokens"}""",
            null
        )
        val ok = reply.toResult(200, "m", false, false, null) as ChatDispatchResult.Success
        assertEquals("Done", ok.assistantText)
        assertEquals("Plan", ok.turnMetadata?.thinkingSummary)
        assertNull(ok.turnMetadata?.thoughtSignature)
        assertTrue(ok.turnMetadata?.isTruncated == true)
    }

    @Test fun turnedDownThinkingIsRetriedOnceWithoutIt() {
        val bodies = Collections.synchronizedList(mutableListOf<String>())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/messages") { exchange ->
            val sent = exchange.requestBody.bufferedReader().use { it.readText() }
            bodies += sent
            val (code, reply) = if (sent.contains("\"thinking\"")) {
                400 to """{"type":"error","error":{"type":"invalid_request_error","message":"thinking is not supported"}}"""
            } else {
                200 to """{"content":[{"type":"text","text":"Plain answer"}],"stop_reason":"end_turn"}"""
            }
            val bytes = reply.toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val config = RelayConfig(ProviderRoute.ANTHROPIC_MESSAGES, "http://127.0.0.1:${server.address.port}", "claude-opus-5-5")
            val request = ChatDispatchRequest(
                correlationId = "c", modelId = "claude-opus-5-5",
                messages = listOf(msg("u", MessageRole.USER, "Hello")), attachments = emptyList(), stream = false
            )
            val reply = runBlocking { HttpAnthropicMessagesBackend().sendChat(request, config, "test-only") {} }
            assertTrue(reply.toString(), reply is ChatDispatchResult.Success)
            assertEquals(2, bodies.size)
            assertFalse(bodies[1].contains("\"thinking\""))
        } finally {
            server.stop(0)
        }
    }

    @Test fun keyErrorNamesTheServiceInPlainWords() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/models") { exchange ->
            val bytes = """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""".toByteArray()
            exchange.sendResponseHeaders(401, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val config = RelayConfig(ProviderRoute.ANTHROPIC_MESSAGES, "http://127.0.0.1:${server.address.port}", "claude-opus-5-5")
            val health = runBlocking { HttpAnthropicMessagesBackend().checkHealth(config, "wrong-test-key") }
            val error = (health as RelayHealthResult.Failure).error
            assertTrue(error.category.name.startsWith("UNAUTHORIZED"))
            assertPlain(error.userMessage)
            assertFalse(error.technicalDetail.contains("wrong-test-key"))
            val models = runBlocking { HttpAnthropicMessagesBackend().listModels(config, "wrong-test-key") }
            assertTrue(models is ModelListResult.Failure)
        } finally {
            server.stop(0)
        }
    }
}
