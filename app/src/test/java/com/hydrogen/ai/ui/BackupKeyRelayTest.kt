package com.hydrogen.ai.ui

import com.hydrogen.ai.data.ConversationStore
import com.hydrogen.ai.data.InMemoryRelayCredentialStore
import com.hydrogen.ai.data.RelayConfigStore
import com.hydrogen.ai.data.cleanBackupTokens
import com.hydrogen.ai.model.Conversation
import com.hydrogen.ai.model.MessageStatus
import com.hydrogen.ai.model.ProviderError
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Backup keys: the same request goes out again with the next key of the same service, and only for key problems. */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupKeyRelayTest {

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

    private data class Attempt(val key: String?, val request: ChatDispatchRequest)

    /** Answers per key: a failure category, or null for a streamed success. */
    private class ScriptedBackend(
        private val answers: Map<String, ProviderErrorCategory?>,
        private val streamBeforeFailing: Boolean = false
    ) : ChatBackend {
        val attempts = mutableListOf<Attempt>()

        override suspend fun checkHealth(config: RelayConfig, bearerToken: String?) =
            RelayHealthResult.Reachable(authenticated = true, detail = "ok")

        override suspend fun sendChat(
            request: ChatDispatchRequest,
            config: RelayConfig,
            bearerToken: String?,
            onStreamDelta: suspend (StreamDelta) -> Unit
        ): ChatDispatchResult {
            attempts += Attempt(bearerToken, request)
            val category = answers[bearerToken]
            if (category == null) {
                onStreamDelta(StreamDelta(textDelta = "Answer from $bearerToken"))
                return ChatDispatchResult.Success(
                    assistantText = "Answer from $bearerToken",
                    modelId = config.modelId,
                    wasStreamed = true
                )
            }
            if (streamBeforeFailing) onStreamDelta(StreamDelta(textDelta = "Half"))
            return ChatDispatchResult.Failure(
                ProviderError(
                    category = category,
                    httpStatus = 429,
                    userMessage = "Google Gemini says this key reached its limit.",
                    sourceLayer = "upstream_google"
                )
            )
        }
    }

    private val google = RelayConfig(route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT, modelId = "gemini-2.5-flash")

    private fun storeWith(main: String, vararg backups: String) = InMemoryRelayCredentialStore().apply {
        setToken(google, main)
        setBackupTokens(google, backups.toList())
    }

    @Test
    fun limitOnMainKeyResendsSameChatWithNextKeyAndPromotesIt() = runTest(testDispatcher) {
        val creds = storeWith("key-a", "key-b", "key-c")
        val backend = ScriptedBackend(
            mapOf("key-a" to ProviderErrorCategory.QUOTA_DAILY_429, "key-b" to null, "key-c" to null)
        )
        val vm = ChatViewModel(FakeConversationStore(), FakeConfigStore(google), creds, backend) { 5_000L }
        advanceUntilIdle()
        vm.updateDraft("Plan my week")
        vm.sendMessage()
        advanceUntilIdle()

        assertEquals(listOf("key-a", "key-b"), backend.attempts.map { it.key })
        // The same request, with the same chat context, went out both times.
        assertEquals(backend.attempts[0].request, backend.attempts[1].request)
        val reply = vm.uiState.value.activeConversation?.messages?.lastOrNull()
        assertEquals(MessageStatus.COMPLETED, reply?.status)
        assertEquals("Answer from key-b", reply?.content)
        // The key that worked is now the main key; the one that failed is tried last.
        assertEquals("key-b", creds.getToken(google))
        assertEquals(listOf("key-c", "key-a"), creds.getBackupTokens(google))
        assertEquals(2, vm.uiState.value.backupKeyLabels.size)
        assertTrue(vm.uiState.value.notice.orEmpty().contains("backup key 1"))
    }

    @Test
    fun everyKeyAtItsLimitFailsOnceEachAndSaysSo() = runTest(testDispatcher) {
        val creds = storeWith("key-a", "key-b")
        val backend = ScriptedBackend(
            mapOf("key-a" to ProviderErrorCategory.QUOTA_RATE_TPM_RPM_429, "key-b" to ProviderErrorCategory.OUT_OF_CREDITS)
        )
        val vm = ChatViewModel(FakeConversationStore(), FakeConfigStore(google), creds, backend) { 5_000L }
        advanceUntilIdle()
        vm.updateDraft("Hello")
        vm.sendMessage()
        advanceUntilIdle()

        assertEquals(listOf("key-a", "key-b"), backend.attempts.map { it.key })
        val reply = vm.uiState.value.activeConversation?.messages?.lastOrNull()
        assertEquals(MessageStatus.FAILED, reply?.status)
        assertTrue(reply?.error?.userMessage.orEmpty().contains("also tried your 1 backup key"))
        assertEquals("key-a", creds.getToken(google))
        assertEquals(listOf("key-b"), creds.getBackupTokens(google))
    }

    @Test
    fun otherFailuresAndHalfStreamedRepliesAreNotResent() = runTest(testDispatcher) {
        val busy = ScriptedBackend(mapOf("key-a" to ProviderErrorCategory.SERVER_5XX, "key-b" to null))
        val vm = ChatViewModel(FakeConversationStore(), FakeConfigStore(google), storeWith("key-a", "key-b"), busy) { 5_000L }
        advanceUntilIdle()
        vm.updateDraft("Hello")
        vm.sendMessage()
        advanceUntilIdle()
        assertEquals(listOf("key-a"), busy.attempts.map { it.key })

        val halfway = ScriptedBackend(
            mapOf("key-a" to ProviderErrorCategory.QUOTA_RATE_TPM_RPM_429, "key-b" to null),
            streamBeforeFailing = true
        )
        val vm2 = ChatViewModel(FakeConversationStore(), FakeConfigStore(google), storeWith("key-a", "key-b"), halfway) { 5_000L }
        advanceUntilIdle()
        vm2.updateDraft("Hello")
        vm2.sendMessage()
        advanceUntilIdle()
        assertEquals(listOf("key-a"), halfway.attempts.map { it.key })
        assertEquals("Half", vm2.uiState.value.activeConversation?.messages?.lastOrNull()?.content)
    }

    @Test
    fun backupKeysStayWithTheirOwnService() {
        val creds = storeWith("key-a", "key-b")
        val anthropic = RelayConfig(route = ProviderRoute.ANTHROPIC_MESSAGES, modelId = "claude-opus-5-5")
        assertEquals(emptyList<String>(), creds.getBackupTokens(anthropic))
        assertEquals(listOf("key-b"), creds.getBackupTokens(google))
    }

    @Test
    fun cleaningDropsBlanksRepeatsSpacesAndTheMainKey() {
        assertEquals(
            listOf("b", "c"),
            cleanBackupTokens(listOf(" b ", "", "main", "b", "has space", "c"), mainKey = "main")
        )
    }

    @Test
    fun pastingSeveralKeysSavesTheFirstAsMainAndTheRestAsBackups() = runTest(testDispatcher) {
        val creds = InMemoryRelayCredentialStore()
        val backend = ScriptedBackend(emptyMap())
        val vm = ChatViewModel(FakeConversationStore(), FakeConfigStore(google), creds, backend) { 5_000L }
        advanceUntilIdle()
        vm.saveRelaySettings(
            route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
            baseUrl = "https://api.example.com/v1/chat/completions",
            modelId = "some-model",
            token = "\"key-aaaaaaaa\"\nBearer key-bbbbbbbb\nkey-cccccccc",
            maxContextTokens = 32_000,
            maxImages = 4,
            streamingEnabled = true,
            rememberTokenInKeystore = false,
            webSearchEnabled = false
        )
        val saved = vm.uiState.value.relayConfig
        assertEquals("https://api.example.com", saved.baseUrl)
        assertEquals("key-aaaaaaaa", creds.getToken(saved))
        assertEquals(listOf("key-bbbbbbbb", "key-cccccccc"), creds.getBackupTokens(saved))
        assertEquals(2, vm.uiState.value.backupKeyLabels.size)

        vm.addBackupKey("key-dddddddd\r\nkey-bbbbbbbb, key-eeeeeeee")
        assertEquals(
            listOf("key-bbbbbbbb", "key-cccccccc", "key-dddddddd", "key-eeeeeeee"),
            creds.getBackupTokens(saved)
        )
        // Backup keys stay with the service they were added for.
        assertEquals(emptyList<String>(), creds.getBackupTokens(google))
    }
}
