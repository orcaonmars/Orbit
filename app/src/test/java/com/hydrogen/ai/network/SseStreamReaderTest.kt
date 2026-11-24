package com.hydrogen.ai.network

import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.ProviderRoute
import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SseStreamReaderTest {

    @Test
    fun accumulatesMultiChunkGeminiSseStreamWithMetadataAndDeltas() = runTest {
        val ssePayload = """
            data: {"candidates":[{"content":{"parts":[{"text":"Thinking...","thought":true},{"thoughtSignature":"sig-1","text":"Hello "}]}}]}

            data: {"candidates":[{"content":{"parts":[{"text":"world!"}]},"finishReason":"STOP"}]}

            data: [DONE]

        """.trimIndent()

        val deltas = mutableListOf<StreamDelta>()
        val result = SseStreamReader.readSseStream(
            inputStream = ByteArrayInputStream(ssePayload.toByteArray(Charsets.UTF_8)),
            onDelta = { deltas += it },
            route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT
        )

        assertNull(result.streamError)
        assertEquals("Hello world!", result.text)
        assertEquals("Thinking...", result.metadata?.thinkingSummary)
        assertEquals("sig-1", result.metadata?.thoughtSignature)
        assertEquals("STOP", result.metadata?.finishReason)
        assertEquals(2, deltas.size)
        assertEquals("Hello ", deltas[0].textDelta)
        assertEquals("world!", deltas[1].textDelta)
    }

    @Test
    fun embeddedSseErrorPayloadSurfacesClassifiedProviderError() = runTest {
        val ssePayload = """
            data: {"candidates":[{"content":{"parts":[{"text":"Partial "}]}}]}

            data: {"error":{"code":429,"message":"Requests per minute exceeded"}}

        """.trimIndent()

        val deltas = mutableListOf<StreamDelta>()
        val result = SseStreamReader.readSseStream(
            inputStream = ByteArrayInputStream(ssePayload.toByteArray(Charsets.UTF_8)),
            onDelta = { deltas += it },
            route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT
        )

        assertEquals("Partial ", result.text)
        assertEquals(1, deltas.size)
        assertNotNull(result.streamError)
        assertEquals(ProviderErrorCategory.QUOTA_RATE_TPM_RPM_429, result.streamError?.category)
    }

    @Test
    fun terminalDoneStopsReadingTrailingEvents() = runTest {
        val payload = """
            data: {"choices":[{"delta":{"content":"Complete"},"finish_reason":"stop"}]}

            data: [DONE]

            data: {"choices":[{"delta":{"content":" should not appear"}}]}

        """.trimIndent()
        val result = SseStreamReader.readSseStream(ByteArrayInputStream(payload.toByteArray()), {})
        assertEquals("Complete", result.text)
        assertNull(result.streamError)
    }

    @Test fun http200ErrorEnvelopeReturnsRealProviderFailure() = runTest {
        val body = """{"error":{"code":"invalid_api_key","message":"Invalid API key"}}"""
        val result = SseStreamReader.handleSuccessBody(
            200, "application/json", { ByteArrayInputStream(body.toByteArray()) },
            false, "test", false, "test", {}, serviceName = "Test provider"
        )
        assertEquals(ProviderErrorCategory.UNAUTHORIZED_UPSTREAM_401,
            (result as? ChatDispatchResult.Failure)?.error?.category)
    }

    @Test fun errorWithEmptyChoicesIsNotSilentlyIgnored() = runTest {
        val body = """data: {"choices":[],"error":{"code":"rate_limit_exceeded","message":"Requests per minute exceeded"}}

            """.trimIndent()
        val result = SseStreamReader.readSseStream(ByteArrayInputStream(body.toByteArray()), {})
        assertEquals(ProviderErrorCategory.QUOTA_RATE_TPM_RPM_429, result.streamError?.category)
    }

    @Test fun nullErrorFieldDoesNotPreventSuccessfulReply() = runTest {
        val body = """{"error":null,"choices":[{"message":{"content":"Answer"}}]}"""
        val result = SseStreamReader.handleSuccessBody(
            200, "application/json", { ByteArrayInputStream(body.toByteArray()) },
            false, "test", false, "test", {}
        )
        assertEquals("Answer", (result as? ChatDispatchResult.Success)?.assistantText)
    }

    @Test fun interleavedToolCallFragmentsKeepEveryArgumentAndDistinctIds() = runTest {
        val body = """
            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_a","function":{"name":"lookup","arguments":"{\"q\":\""}},{"index":1,"id":"call_b","function":{"name":"lookup","arguments":"{\"q\":\""}}]}}]}

            data: {"choices":[{"delta":{"tool_calls":[{"index":1,"function":{"arguments":"two\"}"}},{"index":0,"function":{"arguments":"one\"}"}}]}}]}

            data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}

            data: [DONE]
        """.trimIndent()
        val emitted = mutableListOf<StreamDelta>()
        val result = SseStreamReader.readSseStream(ByteArrayInputStream(body.toByteArray()), { emitted += it })
        val calls = result.metadata!!.toolCalls
        assertEquals(listOf("call_a", "call_b"), calls.map { it.id })
        assertEquals(listOf("""{"q":"one"}""", """{"q":"two"}"""), calls.map { it.argumentsJson })
        assertEquals(calls, emitted.flatMap { it.toolCalls })
    }

    @Test fun unnamedIncompleteToolFragmentsAreNotReportedAsRequests() = runTest {
        val body = """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{}"}}]}}]}

            """.trimIndent()
        val result = SseStreamReader.readSseStream(ByteArrayInputStream(body.toByteArray()), {})
        assertEquals(emptyList<String>(), result.metadata?.toolCalls.orEmpty().map { it.name })
    }

    @Test fun unexpectedEofPreservesPartialReplyAndMarksItIncomplete() = runTest {
        val payload = """data: {"choices":[{"delta":{"content":"Partial answer"}}]}"""
        val result = SseStreamReader.readSseStream(ByteArrayInputStream(payload.toByteArray()), {})
        assertEquals("Partial answer", result.text)
        assertEquals("stream_interrupted", result.metadata?.finishReason)
        assertEquals(true, result.metadata?.isTruncated)
    }
    @Test fun terminalMarkerWithoutFinishReasonIsStillComplete() = runTest {
        val payload = """
            data: {"choices":[{"delta":{"content":"Complete"}}]}

            data: [DONE]
        """.trimIndent()
        val result = SseStreamReader.readSseStream(ByteArrayInputStream(payload.toByteArray()), {})
        assertEquals("Complete", result.text)
        assertEquals(false, result.metadata?.isTruncated ?: false)
    }

    @Test fun namedToolWithoutTerminalEventIsNotReportedEvenWithValidJson() = runTest {
        val body = """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"lookup","arguments":"{}"}}]}}]}"""
        val emitted = mutableListOf<StreamDelta>()
        val result = SseStreamReader.readSseStream(ByteArrayInputStream(body.toByteArray()), { emitted += it })
        assertTrue(result.metadata?.toolCalls.orEmpty().isEmpty())
        assertTrue(emitted.flatMap { it.toolCalls }.isEmpty())
        assertEquals("tool_call_incomplete", result.metadata?.finishReason)
    }

    @Test fun malformedToolArgumentsAreNotReportedAfterTerminalEvent() = runTest {
        val body = """
            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"lookup","arguments":"{"}}]}}]}

            data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}

            data: [DONE]
        """.trimIndent()
        val result = SseStreamReader.readSseStream(ByteArrayInputStream(body.toByteArray()), {})
        assertTrue(result.metadata?.toolCalls.orEmpty().isEmpty())
        assertEquals("tool_call_incomplete", result.metadata?.finishReason)
    }
}
