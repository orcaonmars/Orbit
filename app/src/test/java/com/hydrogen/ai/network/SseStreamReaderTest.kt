package com.hydrogen.ai.network

import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.ProviderRoute
import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
