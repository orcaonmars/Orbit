package com.hydrogen.ai.network

import com.hydrogen.ai.data.FileConversationStore
import com.hydrogen.ai.data.InMemoryRelayCredentialStore
import com.hydrogen.ai.data.RelayConfigStore
import com.hydrogen.ai.model.MessageStatus
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import com.hydrogen.ai.model.VerificationState
import com.hydrogen.ai.ui.ChatViewModel
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Nov 26 bug: every key "reported no remaining usage". A live OpenRouter key had 49 of 50 free requests left, yet a
 * free model answered 429 because the provider behind it was throttled for everyone. These bodies are the shapes
 * OpenRouter actually returns (the 429 one captured live, with the account id removed).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenRouterLimitDiagnosisTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(testDispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val sharedPool429 = """{"error":{"message":"Provider returned error","code":429,"metadata":{"raw":""" +
        """"google/gemma-4-31b-it:free is temporarily rate-limited upstream. Please retry shortly, or add your own """ +
        """provider key to use your own limits: https://openrouter.ai/workspaces/default/byok","provider_name":""" +
        """"Google AI Studio","is_byok":false,"provider_error_code":"429","limit_source":"upstream_provider_shared_pool"}}}"""

    private fun openRouter(code: Int, body: String, retryAfter: String? = null) =
        RelayErrorClassifier.classifyHttpError(code, body, retryAfter, ProviderRoute.GEMINI_HYDROGEN_RELAY, "OpenRouter")

    @Test fun sharedUpstreamThrottleIsNotReportedAsYourQuota() {
        val error = openRouter(429, sharedPool429)
        assertEquals(ProviderErrorCategory.OVERLOADED, error.category)
        assertEquals(RelayErrorClassifier.LAYER_UPSTREAM_SHARED, error.sourceLayer)
        assertTrue(error.userMessage.contains("not your key or your quota"))
        assertTrue(error.userMessage.contains("openrouter/free"))
        assertFalse(error.userMessage.contains("daily", ignoreCase = true))
        assertTrue(RelayErrorClassifier.isTransientError(error.category))
        // The provider's own words reach Details, not just "Provider returned error".
        assertTrue(error.technicalDetail.contains("Google AI Studio: google/gemma-4-31b-it:free is temporarily rate-limited"))
    }

    @Test fun sharedUpstreamThrottleInsideAStreamIsClassifiedTheSame() {
        val inStream = """{"error":{"code":429,"message":"Provider returned error","metadata":{"raw":""" +
            """"x/model:free is temporarily rate-limited upstream. Please retry shortly.","provider_name":"Chutes"}}}"""
        val error = RelayErrorClassifier.classifySseErrorPayload(inStream, ProviderRoute.GEMINI_HYDROGEN_RELAY, "OpenRouter")
        assertEquals(ProviderErrorCategory.OVERLOADED, error?.category)
        assertEquals(429, error?.httpStatus)
    }

    @Test fun genericOtherServiceThrottleDoesNotMentionOpenRouter() {
        val error = RelayErrorClassifier.classifyHttpError(429, sharedPool429, null, ProviderRoute.GEMINI_HYDROGEN_RELAY, "Groq")
        assertEquals(ProviderErrorCategory.OVERLOADED, error.category)
        assertFalse(error.userMessage.contains("openrouter"))
    }

    @Test fun openRouterFreeModelsPerDayIsAGenuineDailyLimit() {
        val error = openRouter(429, """{"error":{"message":"Rate limit exceeded: free-models-per-day. Add 10 credits """ +
            """to unlock 1000 free model requests per day","code":429,"metadata":{"headers":{"X-RateLimit-Limit":"50",""" +
            """"X-RateLimit-Remaining":"0","X-RateLimit-Reset":"1795795200000"}}}}""")
        assertEquals(ProviderErrorCategory.QUOTA_DAILY_429, error.category)
    }

    @Test fun openRouterFreeModelsPerMinuteIsATemporaryRateLimit() {
        val error = openRouter(429, """{"error":{"message":"Rate limit exceeded: free-models-per-min.","code":429}}""", "20")
        assertEquals(ProviderErrorCategory.QUOTA_RATE_TPM_RPM_429, error.category)
        assertEquals("upstream_quota_rate", error.sourceLayer)
        assertEquals(20, error.retryAfterSeconds)
        assertTrue(error.userMessage.contains("Too many requests to OpenRouter"))
    }

    @Test fun unexplained429SaysTheQuotaWasNotConfirmedUsedUp() {
        val error = openRouter(429, """{"error":{"message":"Too busy","code":429}}""")
        assertEquals(ProviderErrorCategory.QUOTA_RATE_TPM_RPM_429, error.category)
        assertTrue(error.userMessage.startsWith("OpenRouter is limiting requests right now."))
        assertTrue(error.userMessage.contains("has not confirmed that your quota is used up"))
    }

    @Test fun insufficientCredits402IsOutOfCredits() {
        val error = openRouter(402, """{"error":{"message":"Insufficient credits. Add more using https://openrouter.ai/settings/credits","code":402}}""")
        assertEquals(ProviderErrorCategory.OUT_OF_CREDITS, error.category)
    }

    @Test fun invalidKey401IsAKeyProblemNotALimit() {
        for (body in listOf(
            """{"error":{"message":"No auth credentials found","code":401}}""",
            """{"error":{"message":"User not found.","code":401}}"""
        )) {
            val error = openRouter(401, body)
            assertEquals(ProviderErrorCategory.UNAUTHORIZED_UPSTREAM_401, error.category)
            assertTrue(error.userMessage.startsWith("OpenRouter didn't accept your API key"))
        }
    }

    @Test fun unknownOrUnroutableModelIsModelNotFound() {
        assertEquals(ProviderErrorCategory.MODEL_NOT_FOUND,
            openRouter(400, """{"error":{"message":"made/up:free is not a valid model ID","code":400}}""").category)
        assertEquals(ProviderErrorCategory.MODEL_NOT_FOUND,
            openRouter(404, """{"error":{"message":"No endpoints found for made/up:free.","code":404}}""").category)
    }

    @Test fun privacyPolicyMismatchExplainsTheSettingNotALimit() {
        val error = openRouter(404, """{"error":{"message":"No endpoints found matching your data policy """ +
            """(Free model publication). Configure: https://openrouter.ai/settings/privacy","code":404}}""")
        assertEquals(ProviderErrorCategory.MODEL_NOT_FOUND, error.category)
        assertTrue(error.userMessage.contains("privacy settings"))
        assertTrue(error.userMessage.contains("This is not a limit."))
    }

    @Test fun moderation403IsAccessDeniedNotALimit() {
        val error = openRouter(403, """{"error":{"message":"Your input was flagged","code":403}}""")
        assertEquals(ProviderErrorCategory.FORBIDDEN_403, error.category)
    }

    @Test fun googleZeroQuotaModelIsAPlanMismatchNotAUsedUpDailyQuota() {
        val body = """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED","message":"You exceeded your current quota. """ +
            """Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, """ +
            """limit: 0, model: gemini-2.5-pro","details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure",""" +
            """"violations":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier"}]}]}}"""
        val error = RelayErrorClassifier.classifyHttpError(429, body, null, ProviderRoute.GOOGLE_AI_STUDIO_DIRECT)
        assertEquals(ProviderErrorCategory.FORBIDDEN_403, error.category)
        assertEquals(RelayErrorClassifier.LAYER_PLAN, error.sourceLayer)
        assertTrue(error.userMessage.contains("limit is 0"))
        assertFalse(RelayErrorClassifier.isTransientError(error.category))
    }

    @Test fun googleRealDailyQuotaStaysDaily() {
        val body = """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED","message":"Quota exceeded for metric: """ +
            """generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 50, model: gemini-2.5-flash",""" +
            """"details":[{"violations":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier"}]}]}}"""
        val error = RelayErrorClassifier.classifyHttpError(429, body, null, ProviderRoute.GOOGLE_AI_STUDIO_DIRECT)
        assertEquals(ProviderErrorCategory.QUOTA_DAILY_429, error.category)
    }

    @Test fun networkFailureIsNotALimit() {
        val error = NetworkErrorClassifier.classify(UnknownHostException("openrouter.ai"), "https://openrouter.ai/api", "OpenRouter", forChat = true)
        assertEquals(ProviderErrorCategory.NETWORK_TIMEOUT, error.category)
    }

    private class FakeConfigStore(initial: RelayConfig) : RelayConfigStore {
        private var config = initial
        override fun loadConfig(): RelayConfig = config
        override fun saveConfig(config: RelayConfig) { this.config = config }
    }

    private suspend fun TestScope.awaitBackendIdle(vm: ChatViewModel) {
        advanceUntilIdle()
        repeat(200) {
            if (!vm.uiState.value.isSending) return
            withContext(Dispatchers.IO) { Thread.sleep(15) }
            advanceUntilIdle()
        }
    }

    /**
     * Over real HTTP: a shared-pool 429 keeps the main key, never spends the backup keys, says nothing about the
     * quota, and the next try with the same key succeeds and clears the throttle status.
     */
    @Test
    fun throttledSendKeepsKeysAndRetryWithSameKeySucceeds() = runTest(testDispatcher) {
        val tempDir = Files.createTempDirectory("orbit-openrouter-throttle").toFile()
        val seenAuth = mutableListOf<String?>()
        var lastModel: String? = null
        var lastStream = false
        var throttled = true
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newSingleThreadExecutor()
        server.createContext("/v1/chat/completions") { ex ->
            seenAuth += ex.requestHeaders.getFirst("Authorization")
            val req = Json.parseToJsonElement(ex.requestBody.bufferedReader().readText()).jsonObject
            lastModel = req["model"]?.jsonPrimitive?.contentOrNull
            lastStream = req["stream"]?.jsonPrimitive?.booleanOrNull == true
            val (code, type, body) = if (throttled) {
                Triple(429, "application/json", sharedPool429)
            } else {
                Triple(200, "text/event-stream",
                    "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"ok\"},\"finish_reason\":null}]}\n\n" +
                        "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n")
            }
            val bytes = body.toByteArray()
            ex.responseHeaders.add("Content-Type", type)
            ex.sendResponseHeaders(code, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val config = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, "http://127.0.0.1:${server.address.port}",
                "openrouter/free", streamingEnabled = true)
            val creds = InMemoryRelayCredentialStore().apply {
                setToken(config, "qa-main-key")
                setBackupTokens(config, listOf("qa-backup-key"))
            }
            val vm = ChatViewModel(FileConversationStore(tempDir, testDispatcher), FakeConfigStore(config), creds,
                HttpGeminiHydrogenRelayBackend()) { 1_000L }
            advanceUntilIdle()

            vm.updateDraft("Reply with the single word ok.")
            vm.sendMessage()
            awaitBackendIdle(vm)

            val failed = vm.uiState.value.activeConversation!!.messages[1]
            assertEquals(MessageStatus.FAILED, failed.status)
            assertEquals(ProviderErrorCategory.OVERLOADED, failed.error?.category)
            assertFalse(failed.error!!.userMessage.contains("backup"))
            assertEquals(listOf<String?>("Bearer qa-main-key"), seenAuth)
            assertEquals("openrouter/free", lastModel)
            assertTrue(lastStream)
            assertEquals("qa-main-key", creds.getToken(config))
            assertEquals(listOf("qa-backup-key"), creds.getBackupTokens(config))

            throttled = false
            vm.retryMessage(failed.id)
            awaitBackendIdle(vm)

            val ok = vm.uiState.value.activeConversation!!.messages[1]
            assertEquals(MessageStatus.COMPLETED, ok.status)
            assertEquals("ok", ok.content)
            assertEquals(listOf<String?>("Bearer qa-main-key", "Bearer qa-main-key"), seenAuth)
            val readiness = vm.uiState.value.readiness
            assertEquals(VerificationState.PASS, readiness.textGeneration)
            assertTrue(readiness.statusDetail.startsWith("Verified"))
            assertFalse(readiness.statusDetail.contains("limit", ignoreCase = true))
            assertEquals("qa-main-key", creds.getToken(config))
        } finally {
            server.stop(0)
            tempDir.deleteRecursively()
        }
    }
}
