package com.hydrogen.ai.ui

import com.hydrogen.ai.data.ConversationStore
import com.hydrogen.ai.data.InMemoryRelayCredentialStore
import com.hydrogen.ai.data.RelayConfigStore
import com.hydrogen.ai.model.Conversation
import com.hydrogen.ai.model.MessageStatus
import com.hydrogen.ai.model.ProviderError
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import com.hydrogen.ai.model.VerificationState
import com.hydrogen.ai.network.ChatBackend
import com.hydrogen.ai.network.ChatDispatchRequest
import com.hydrogen.ai.network.ChatDispatchResult
import com.hydrogen.ai.network.RelayHealthResult
import com.hydrogen.ai.network.StreamDelta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelReadinessTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeConversationStore : ConversationStore {
        var saved: List<Conversation> = emptyList()
        override suspend fun loadConversations(): List<Conversation> = saved
        override suspend fun saveConversations(conversations: List<Conversation>) {
            saved = conversations
        }
    }

    private class FakeConfigStore(initial: RelayConfig) : RelayConfigStore {
        private var config = initial
        override fun loadConfig(): RelayConfig = config
        override fun saveConfig(config: RelayConfig) {
            this.config = config
        }
    }

    @Test
    fun checkHealthDistinguishesReachableEndpointWithBadAuth() = runTest(testDispatcher) {
        val config = RelayConfig(
            route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT,
            modelId = "gemini-2.5-flash"
        )
        val creds = InMemoryRelayCredentialStore(mapOf(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT to "bad-key"))
        val backend = object : ChatBackend {
            override suspend fun checkHealth(config: RelayConfig, bearerToken: String?) =
                RelayHealthResult.Failure(
                    ProviderError(
                        category = ProviderErrorCategory.UNAUTHORIZED_UPSTREAM_401,
                        httpStatus = 400,
                        userMessage = "Upstream Gemini rejected the API key (HTTP 400).",
                        sourceLayer = "upstream_google"
                    )
                )

            override suspend fun sendChat(
                request: ChatDispatchRequest,
                config: RelayConfig,
                bearerToken: String?,
                onStreamDelta: suspend (StreamDelta) -> Unit
            ) = error("unused")
        }

        val vm = ChatViewModel(FakeConversationStore(), FakeConfigStore(config), creds, backend) { 1_000L }
        advanceUntilIdle()
        vm.checkRelayHealth()
        advanceUntilIdle()

        val readiness = vm.uiState.value.readiness
        assertEquals(true, readiness.configured)
        assertEquals(VerificationState.PASS, readiness.relayReachable)
        assertEquals(VerificationState.FAIL, readiness.authenticated)
    }

    @Test
    fun checkHealthQuota429MarksEndpointAndAuthVerified() = runTest(testDispatcher) {
        val config = RelayConfig(
            route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT,
            modelId = "gemini-2.5-flash"
        )
        val creds = InMemoryRelayCredentialStore(mapOf(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT to "valid-key"))
        val backend = object : ChatBackend {
            override suspend fun checkHealth(config: RelayConfig, bearerToken: String?) =
                RelayHealthResult.Failure(
                    ProviderError(
                        category = ProviderErrorCategory.QUOTA_RATE_TPM_RPM_429,
                        httpStatus = 429,
                        userMessage = "Per-minute rate or token quota reached (HTTP 429).",
                        sourceLayer = "upstream_quota_rate"
                    )
                )

            override suspend fun sendChat(
                request: ChatDispatchRequest,
                config: RelayConfig,
                bearerToken: String?,
                onStreamDelta: suspend (StreamDelta) -> Unit
            ) = error("unused")
        }

        val vm = ChatViewModel(FakeConversationStore(), FakeConfigStore(config), creds, backend) { 2_000L }
        advanceUntilIdle()
        vm.checkRelayHealth()
        advanceUntilIdle()

        val readiness = vm.uiState.value.readiness
        assertEquals(VerificationState.PASS, readiness.relayReachable)
        assertEquals(VerificationState.PASS, readiness.authenticated)
    }

    @Test
    fun streamingFailurePreservesPartialContentOnFailedAssistantMessage() = runTest(testDispatcher) {
        val config = RelayConfig(
            route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT,
            modelId = "gemini-2.5-flash"
        )
        val creds = InMemoryRelayCredentialStore(mapOf(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT to "valid-key"))
        val backend = object : ChatBackend {
            override suspend fun checkHealth(config: RelayConfig, bearerToken: String?) =
                RelayHealthResult.Reachable(authenticated = true, detail = "ok")

            override suspend fun sendChat(
                request: ChatDispatchRequest,
                config: RelayConfig,
                bearerToken: String?,
                onStreamDelta: suspend (StreamDelta) -> Unit
            ): ChatDispatchResult {
                onStreamDelta(StreamDelta(textDelta = "Partial streamed "))
                onStreamDelta(StreamDelta(textDelta = "reply"))
                return ChatDispatchResult.Failure(
                    ProviderError(
                        category = ProviderErrorCategory.QUOTA_RATE_TPM_RPM_429,
                        httpStatus = 429,
                        userMessage = "Rate limited mid-stream"
                    )
                )
            }
        }

        val vm = ChatViewModel(FakeConversationStore(), FakeConfigStore(config), creds, backend) { 3_000L }
        advanceUntilIdle()
        vm.updateDraft("Hello Gemini")
        vm.sendMessage()
        advanceUntilIdle()

        val assistant = vm.uiState.value.activeConversation?.messages?.lastOrNull()
        assertEquals(MessageStatus.FAILED, assistant?.status)
        assertEquals("Partial streamed reply", assistant?.content)
        assertEquals(ProviderErrorCategory.QUOTA_RATE_TPM_RPM_429, assistant?.error?.category)
    }

    @Test
    fun delayedHealthAnswerForOldServiceDoesNotOverwriteNewService() = runTest(testDispatcher) {
        val oldConfig = RelayConfig(
            route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT,
            modelId = "gemini-2.5-flash"
        )
        val creds = InMemoryRelayCredentialStore(mapOf(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT to "google-key"))
        val backend = object : ChatBackend {
            override suspend fun checkHealth(config: RelayConfig, bearerToken: String?): RelayHealthResult {
                kotlinx.coroutines.delay(5_000)
                return RelayHealthResult.Reachable(authenticated = true, detail = "old service answered late")
            }

            override suspend fun sendChat(
                request: ChatDispatchRequest,
                config: RelayConfig,
                bearerToken: String?,
                onStreamDelta: suspend (StreamDelta) -> Unit
            ) = error("unused")
        }

        val vm = ChatViewModel(FakeConversationStore(), FakeConfigStore(oldConfig), creds, backend) { 4_000L }
        advanceUntilIdle()
        vm.checkRelayHealth()
        testDispatcher.scheduler.advanceTimeBy(1_000)
        testDispatcher.scheduler.runCurrent()
        // The owner switches to another service while the old check is still waiting.
        vm.saveRelaySettings(
            route = ProviderRoute.ANTHROPIC_MESSAGES,
            baseUrl = "",
            modelId = "claude-opus-5-5",
            token = null,
            maxContextTokens = 32_000,
            maxImages = 4,
            streamingEnabled = true,
            rememberTokenInKeystore = false,
            webSearchEnabled = false
        )
        val afterSwitch = vm.uiState.value.readiness
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(ProviderRoute.ANTHROPIC_MESSAGES, state.relayConfig.route)
        assertEquals(false, state.isCheckingHealth)
        assertEquals(afterSwitch, state.readiness)
        assertEquals(false, state.readiness.relayReachable == VerificationState.PASS)
    }
}
