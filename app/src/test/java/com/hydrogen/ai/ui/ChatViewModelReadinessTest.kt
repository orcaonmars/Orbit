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
    fun browsingDraftModelsKeepsActiveConnectionAndDoesNotSavePastedKey() = runTest(testDispatcher) {
        val first = RelayConfig(route = ProviderRoute.GEMINI_HYDROGEN_RELAY, baseUrl = "https://first.example", modelId = "first")
        val second = first.copy(baseUrl = "https://second.example", modelId = "second")
        val configStore = FakeConfigStore(first)
        val credentials = InMemoryRelayCredentialStore()
        credentials.setToken(first, "first-key")
        credentials.setToken(second, "saved-second")
        var usedKey: String? = null
        var usedConfig: RelayConfig? = null
        val backend = object : ChatBackend {
            override suspend fun checkHealth(config: RelayConfig, bearerToken: String?) = error("unused")
            override suspend fun sendChat(request: ChatDispatchRequest, config: RelayConfig, bearerToken: String?,
                onStreamDelta: suspend (StreamDelta) -> Unit): ChatDispatchResult = error("unused")
            override suspend fun listModels(config: RelayConfig, bearerToken: String?): com.hydrogen.ai.network.ModelListResult {
                usedKey = bearerToken
                usedConfig = config
                return com.hydrogen.ai.network.ModelListResult.Success(listOf("test-model"), setOf("test-model"))
            }
        }
        val vm = ChatViewModel(FakeConversationStore(), configStore, credentials, backend) { 1000L }
        advanceUntilIdle()
        val result = vm.browseDraftModels(second.route, second.baseUrl, "", "new-test-key\nbackup-key")
        assertEquals("new-test-key", usedKey)
        assertEquals(second.baseUrl, usedConfig?.baseUrl)
        assertEquals(listOf("test-model"), result.modelIds)
        assertEquals(setOf("test-model"), result.reasoningModelIds)
        assertEquals(first, configStore.loadConfig())
        assertEquals(first, vm.uiState.value.relayConfig)
        assertEquals("first-key", credentials.getToken(first))
        assertEquals("saved-second", credentials.getToken(second))
        vm.browseDraftModels(second.route, second.baseUrl, "", null)
        assertEquals("saved-second", usedKey)
        assertEquals(null, vm.savedDraftKeyLabel(second.route, "https://third.example"))
    }

    @Test
    fun returningToChatRestoresItsOwnServiceModelAndKey() = runTest(testDispatcher) {
        val initial = RelayConfig(route = ProviderRoute.GEMINI_HYDROGEN_RELAY, baseUrl = "https://first.example", modelId = "first-model")
        val store = FakeConversationStore()
        val creds = InMemoryRelayCredentialStore()
        creds.setToken(initial, "first-key")
        val backend = object : ChatBackend {
            override suspend fun checkHealth(config: RelayConfig, bearerToken: String?) = error("unused")
            override suspend fun sendChat(request: ChatDispatchRequest, config: RelayConfig, bearerToken: String?,
                onStreamDelta: suspend (StreamDelta) -> Unit): ChatDispatchResult = error("unused")
        }
        val vm = ChatViewModel(store, FakeConfigStore(initial), creds, backend) { 1000L }
        advanceUntilIdle()
        val first = vm.uiState.value.activeConversationId!!
        vm.createNewConversation()
        advanceUntilIdle()
        val second = vm.uiState.value.activeConversationId!!
        vm.saveRelaySettings(ProviderRoute.GEMINI_HYDROGEN_RELAY, "https://second.example", "second-model",
            "second-key", 4000, 4, true, true, false)
        advanceUntilIdle()
        vm.selectConversation(first)
        assertEquals("first-model", vm.uiState.value.relayConfig.modelId)
        assertEquals("https://first.example", vm.uiState.value.relayConfig.effectiveBaseUrl)
        assertEquals("first-key", creds.getToken(vm.uiState.value.relayConfig))
        vm.selectConversation(second)
        assertEquals("second-model", vm.uiState.value.relayConfig.modelId)
        assertEquals("second-key", creds.getToken(vm.uiState.value.relayConfig))
        assertEquals("second-model", store.saved.first { it.id == second }.preferredConnection?.modelId)
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
    fun lateReplyCannotVerifyNewProviderOrLoseChatEdits() = runTest(testDispatcher) {
        val config = RelayConfig(route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT, modelId = "gemini-2.5-flash")
        val store = FakeConversationStore()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val backend = object : ChatBackend {
            override suspend fun checkHealth(config: RelayConfig, bearerToken: String?) = error("unused")
            override suspend fun sendChat(request: ChatDispatchRequest, config: RelayConfig, bearerToken: String?,
                onStreamDelta: suspend (StreamDelta) -> Unit): ChatDispatchResult {
                gate.await()
                return ChatDispatchResult.Success(assistantText = "Old reply", modelId = config.modelId, wasStreamed = false)
            }
        }
        val vm = ChatViewModel(store, FakeConfigStore(config),
            InMemoryRelayCredentialStore(mapOf(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT to "key")), backend) { 1000L }
        advanceUntilIdle()
        val origin = vm.uiState.value.activeConversationId!!
        vm.updateDraft("Hello")
        vm.sendMessage()
        testDispatcher.scheduler.runCurrent()
        vm.createNewConversation()
        testDispatcher.scheduler.runCurrent()
        vm.saveRelaySettings(ProviderRoute.ANTHROPIC_MESSAGES, "", "claude-opus-5-5",
            null, 4000, 4, true, true, false)
        vm.renameConversation(origin, "Renamed while sending")
        testDispatcher.scheduler.runCurrent()
        val readiness = vm.uiState.value.readiness
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(readiness, vm.uiState.value.readiness)
        val old = vm.uiState.value.conversations.first { it.id == origin }
        assertEquals("Renamed while sending", old.title)
        assertEquals("Old reply", old.messages.last().content)
        assertEquals(MessageStatus.COMPLETED, old.messages.last().status)
        assertEquals(ProviderRoute.ANTHROPIC_MESSAGES, vm.uiState.value.relayConfig.route)
        assertEquals(false, vm.uiState.value.isSending)
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
