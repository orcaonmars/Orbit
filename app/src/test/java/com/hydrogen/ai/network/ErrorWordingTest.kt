package com.hydrogen.ai.network

import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.ProviderError
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Error headlines and messages are read by people, not developers: no protocol jargon, no HTTP
 * codes, and always a next step. Raw detail belongs in technicalDetail (behind "Details").
 */
class ErrorWordingTest {

    private val jargon = listOf("fabricated", "bearer", "schema", "payload", "upstream", "HTTP", "relay", "Relay")

    private fun assertPlain(error: ProviderError) {
        for (word in jargon) {
            assertFalse("\"$word\" in: ${error.userMessage}", error.userMessage.contains(word))
        }
        assertTrue("message should end as a sentence: ${error.userMessage}", error.userMessage.trimEnd().endsWith("."))
    }

    @Test
    fun classifierMessagesArePlainAndKeepRawBodyInDetail() {
        val cases = listOf(
            400 to """{"error":{"message":"API key not valid","status":"API_KEY_INVALID"}}""",
            400 to """{"error":{"message":"Invalid JSON payload received"}}""",
            401 to """{"error":"google rejected key"}""",
            401 to """{"error":"bad bearer token"}""",
            403 to """{"error":"forbidden"}""",
            429 to """{"error":{"message":"Quota exceeded for requests per day","status":"RESOURCE_EXHAUSTED"}}""",
            429 to """{"error":{"message":"Too many requests per minute"}}""",
            500 to "internal",
            418 to "teapot"
        )
        for ((code, body) in cases) {
            val error = RelayErrorClassifier.classifyHttpError(code, body)
            assertPlain(error)
            assertEquals(code, error.httpStatus)
            assertTrue("raw body kept for Details", error.technicalDetail.orEmpty().isNotBlank())
        }
    }

    @Test
    fun rateLimitTellsYouHowLongToWait() {
        val one = RelayErrorClassifier.classifyHttpError(429, "rate limit", retryAfterHeader = "1")
        assertTrue(one.userMessage, one.userMessage.endsWith("Try again in 1 second."))
        val many = RelayErrorClassifier.classifyHttpError(429, "rate limit", retryAfterHeader = "30")
        assertTrue(many.userMessage, many.userMessage.endsWith("Try again in 30 seconds."))
        val unknown = RelayErrorClassifier.classifyHttpError(429, "rate limit")
        assertTrue(unknown.userMessage, unknown.userMessage.endsWith("Wait a minute, then try again."))
    }

    @Test
    fun dailyQuotaPromisesNoPaidFallback() {
        val error = RelayErrorClassifier.classifyHttpError(429, "free_tier requests per day exhausted")
        assertEquals(ProviderErrorCategory.QUOTA_DAILY_429, error.category)
        assertTrue(error.userMessage.contains("never switches to a paid service"))
    }

    @Test
    fun categoryTitlesHaveNoCodesOrSlashes() {
        for (category in ProviderErrorCategory.entries) {
            val title = category.title
            assertFalse("$category: $title", title.contains("(4") || title.contains("(5") || title.contains("/"))
            assertFalse("$category: $title", title.contains("HTTP"))
            assertTrue("$category has a title", title.isNotBlank())
        }
    }

    @Test
    fun notConnectedAndMissingTokenSayWhatToDo() = runBlocking {
        val backend = HttpGeminiHydrogenRelayBackend()
        val blank = backend.sendChat(request(), RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, "", "m", streamingEnabled = false), null) { }
        val blankError = (blank as ChatDispatchResult.Failure).error
        assertEquals(ProviderErrorCategory.UNCONFIGURED, blankError.category)
        assertEquals(HttpGeminiHydrogenRelayBackend.NOT_CONNECTED_MESSAGE, blankError.userMessage)
        assertPlain(blankError)

        val remote = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, "https://relay.example.com", "m", streamingEnabled = false)
        val noToken = backend.sendChat(request(), remote, null) { }
        val noTokenError = (noToken as ChatDispatchResult.Failure).error
        assertEquals(HttpGeminiHydrogenRelayBackend.NEEDS_TOKEN_MESSAGE, noTokenError.userMessage)
        assertPlain(noTokenError)

        val google = HttpGoogleAiStudioBackend().sendChat(
            request(),
            RelayConfig(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT, "https://generativelanguage.googleapis.com", "gemini", streamingEnabled = false),
            null
        ) { }
        val googleError = (google as ChatDispatchResult.Failure).error
        assertEquals(ProviderErrorCategory.UNCONFIGURED, googleError.category)
        assertTrue(googleError.userMessage.contains("Set up"))
        assertPlain(googleError)
    }

    @Test
    fun streamEdgeCasesUsePlainMessages() {
        for (message in listOf(SseStreamReader.FILTERED_MESSAGE, SseStreamReader.EMPTY_REPLY_MESSAGE)) {
            assertPlain(ProviderError(category = ProviderErrorCategory.UNKNOWN_PROTOCOL, userMessage = message, sourceLayer = "test"))
        }
    }

    private fun request() = ChatDispatchRequest(
        correlationId = "c1",
        modelId = "m",
        messages = listOf(
            ChatMessage(
                id = "u1",
                correlationId = "c1",
                role = MessageRole.USER,
                content = "Hi",
                createdAtEpochMs = 1L,
                attachments = emptyList()
            )
        ),
        attachments = emptyList(),
        stream = false
    )
}
