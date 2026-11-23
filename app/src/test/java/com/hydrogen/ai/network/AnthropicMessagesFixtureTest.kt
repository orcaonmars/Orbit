package com.hydrogen.ai.network

import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicMessagesFixtureTest {
    private fun msg(role: MessageRole, text: String) =
        ChatMessage(role.name, role.name, role, text, 1L)

    private fun request(stream: Boolean = false) = ChatDispatchRequest(
        correlationId = "fixture", modelId = "claude-fixture",
        messages = listOf(msg(MessageRole.SYSTEM, "Short answers"), msg(MessageRole.USER, "First"),
            msg(MessageRole.ASSISTANT, "Reply"), msg(MessageRole.USER, "Next")),
        attachments = emptyList(), stream = stream
    )

    @Test fun messages_payload_preserves_system_and_turns() = runBlocking {
        val built = HttpAnthropicMessagesBackend().buildPayload(request(), "claude-fixture", safe = true)
        assertEquals(null, built.error)
        val payload = built.body!!
        assertEquals("claude-fixture", payload["model"]!!.jsonPrimitive.content)
        assertEquals("Short answers", payload["system"]!!.jsonPrimitive.content)
        val turns = payload["messages"]!!.jsonArray
        assertEquals(3, turns.size)
        assertEquals("user", turns[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("assistant", turns[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("user", turns[2].jsonObject["role"]!!.jsonPrimitive.content)
        assertFalse(payload.containsKey("thinking"))
    }

    @Test fun complete_reply_parses_content_and_reason() {
        val result = HttpAnthropicMessagesBackend.parseCompleteReply(
            """{"content":[{"type":"text","text":"Hello "},{"type":"thinking","thinking":"Thought"},{"type":"text","text":"there"}],"stop_reason":"end_turn"}""", null
        )
        assertEquals("Hello there", result.text)
        assertEquals("Thought", result.thinking)
        assertEquals("end_turn", result.stopReason)
    }

    @Test fun stream_deltas_are_reassembled_and_malformed_events_ignored() = runBlocking {
        val fixture = """
            data: NOT_JSON

            data: {"type":"content_block_start","content_block":{"type":"text","text":"Hello"}}

            data: {"type":"content_block_delta","delta":{"type":"text_delta","text":" world"}}

            data: {"type":"message_delta","delta":{"stop_reason":"end_turn"}}

            data: {"type":"message_stop"}

        """.trimIndent()
        val deltas = mutableListOf<StreamDelta>()
        val result = HttpAnthropicMessagesBackend.readEventStream(
            ByteArrayInputStream(fixture.toByteArray()), null
        ) { deltas.add(it) }
        assertEquals("Hello world", result.text)
        assertEquals("end_turn", result.stopReason)
        assertEquals("Hello world", deltas.joinToString("") { it.textDelta })
    }

    @Test fun mock_messages_post_uses_selected_endpoint_and_key() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val header = AtomicReference<String?>()
        val body = AtomicReference<String?>()
        server.createContext("/v1/messages") { exchange ->
            header.set(exchange.requestHeaders.getFirst("x-api-key"))
            body.set(exchange.requestBody.bufferedReader().use { it.readText() })
            val payload = """{"content":[{"type":"text","text":"Fixture answer"}],"stop_reason":"end_turn"}""".toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        try {
            val config = RelayConfig(ProviderRoute.ANTHROPIC_MESSAGES,
                "http://127.0.0.1:${server.address.port}", "claude-fixture")
            val reply = runBlocking { HttpAnthropicMessagesBackend().sendChat(request(), config, "mock-key") {} }
            assertTrue(reply is ChatDispatchResult.Success)
            assertEquals("Fixture answer", (reply as ChatDispatchResult.Success).assistantText)
            assertEquals("mock-key", header.get())
            assertTrue(body.get().orEmpty().contains("Short answers"))
        } finally {
            server.stop(0)
        }
    }
}
