package com.hydrogen.ai.network

import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Thinking from local reasoning models reaches the thought line, never the answer. */
class ThinkingStreamTest {

    private fun openAiChunk(content: String): String =
        """data: {"choices":[{"delta":{"content":${kotlinx.serialization.json.JsonPrimitive(content)}}}]}"""

    @Test
    fun inlineThinkTagsStreamAsThinkingDeltas() = runTest {
        val sse = listOf("<thi", "nk>Count the letters.", "</think>\n\n", "There are 3.")
            .joinToString("\n\n", postfix = "\n\ndata: [DONE]\n\n") { openAiChunk(it) }
        val deltas = mutableListOf<StreamDelta>()
        val result = SseStreamReader.readSseStream(ByteArrayInputStream(sse.toByteArray()), { deltas += it })

        assertNull(result.streamError)
        assertEquals("There are 3.", result.text)
        assertEquals("Count the letters.", result.metadata?.thinkingSummary)
        assertEquals("There are 3.", deltas.joinToString("") { it.textDelta })
        assertEquals("Count the letters.", deltas.joinToString("") { it.thinkingDelta })
        // The thinking arrives before any of the answer, so the thought line can time it.
        val firstAnswer = deltas.indexOfFirst { it.textDelta.isNotBlank() }
        val lastThinking = deltas.indexOfLast { it.thinkingDelta.isNotBlank() }
        assertTrue(lastThinking < firstAnswer)
    }

    @Test
    fun orphanClosingTagIsFixedInTheSavedReply() = runTest {
        val sse = listOf("Two plus two.", "\n</think>\n\n", "It is 4.")
            .joinToString("\n\n", postfix = "\n\n") { openAiChunk(it) }
        val result = SseStreamReader.readSseStream(ByteArrayInputStream(sse.toByteArray()), { })

        assertEquals("It is 4.", result.text)
        assertEquals("Two plus two.\n", result.metadata?.thinkingSummary)
    }

    @Test
    fun ollamaReasoningFieldIsReadAsThinking() = runTest {
        val sse = """
            data: {"choices":[{"delta":{"content":"","reasoning":"Check units."}}]}

            data: {"choices":[{"delta":{"content":"5 km."},"finish_reason":"stop"}]}

        """.trimIndent()
        val deltas = mutableListOf<StreamDelta>()
        val result = SseStreamReader.readSseStream(ByteArrayInputStream(sse.toByteArray()), { deltas += it })

        assertEquals("5 km.", result.text)
        assertEquals("Check units.", result.metadata?.thinkingSummary)
        assertEquals("Check units.", deltas.first().thinkingDelta)
    }

    @Test
    fun nonStreamedReplyWithThinkTagsIsSplit() = runTest {
        val body = """{"choices":[{"message":{"content":"<think>Plan.</think>\n\nDone."}}]}"""
        val result = SseStreamReader.handleSuccessBody(
            code = 200,
            contentType = "application/json",
            openBody = { ByteArrayInputStream(body.toByteArray()) },
            useStream = false,
            model = "qwen3",
            usedVision = false,
            sourceLayer = "relay",
            onStreamDelta = { }
        )
        val success = result as ChatDispatchResult.Success
        assertEquals("Done.", success.assistantText)
        assertEquals("Plan.", success.turnMetadata?.thinkingSummary)
    }

    @Test
    fun replyThatIsOnlyAnUnclosedThinkBlockCountsAsEmpty() = runTest {
        val body = """{"choices":[{"message":{"content":"<think>never finished"},"finish_reason":"length"}]}"""
        val result = SseStreamReader.handleSuccessBody(
            code = 200,
            contentType = "application/json",
            openBody = { ByteArrayInputStream(body.toByteArray()) },
            useStream = false,
            model = "qwen3",
            usedVision = false,
            sourceLayer = "relay",
            onStreamDelta = { }
        )
        assertTrue(result is ChatDispatchResult.Failure)
    }
}
