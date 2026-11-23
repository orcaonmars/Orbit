package com.hydrogen.ai.ui

import com.hydrogen.ai.data.ConversationStore
import com.hydrogen.ai.data.InMemoryRelayCredentialStore
import com.hydrogen.ai.data.InMemoryUserMemoryStore
import com.hydrogen.ai.data.RelayConfigStore
import com.hydrogen.ai.data.UserMemoryState
import com.hydrogen.ai.model.Conversation
import com.hydrogen.ai.model.MemoryItem
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelDeleteAllTest {

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
        var config = initial
        override fun loadConfig(): RelayConfig = config
        override fun saveConfig(config: RelayConfig) {
            this.config = config
        }
    }

    private class OkBackend : ChatBackend {
        override suspend fun checkHealth(config: RelayConfig, bearerToken: String?) =
            RelayHealthResult.Reachable(authenticated = true, detail = "ok")

        override suspend fun sendChat(
            request: ChatDispatchRequest,
            config: RelayConfig,
            bearerToken: String?,
            onStreamDelta: suspend (StreamDelta) -> Unit
        ): ChatDispatchResult = ChatDispatchResult.Success(assistantText = "ok", modelId = config.modelId, wasStreamed = false)
    }

    @Test
    fun deleteAllWipesChatsMemoryKeysAndSettings() = runTest(testDispatcher) {
        val conversations = FakeConversationStore()
        val configStore = FakeConfigStore(
            RelayConfig(route = ProviderRoute.GEMINI_HYDROGEN_RELAY, baseUrl = "http://127.0.0.1:11434/v1", modelId = "m")
        )
        val creds = InMemoryRelayCredentialStore(
            mapOf(ProviderRoute.GEMINI_HYDROGEN_RELAY to "relay-test", ProviderRoute.GOOGLE_AI_STUDIO_DIRECT to "google-test")
        )
        val memory = InMemoryUserMemoryStore(
            UserMemoryState(items = listOf(MemoryItem("a", "Call me Sam", 1L)), enabled = false)
        )
        val vm = ChatViewModel(conversations, configStore, creds, OkBackend(), memoryStore = memory) { 1_000L }
        advanceUntilIdle()
        vm.updateDraft("Hello")
        vm.sendMessage()
        advanceUntilIdle()
        assertTrue(conversations.saved.single().messages.isNotEmpty())

        vm.updateDraft("half-typed")
        vm.deleteAllLocalData()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.conversations.size)
        assertTrue(state.conversations.single().messages.isEmpty())
        assertTrue(conversations.saved.single().messages.isEmpty())
        assertEquals("", state.draftText)
        assertTrue(state.memoryItems.isEmpty())
        assertTrue(state.memoryEnabled)
        assertTrue(memory.load().items.isEmpty())
        assertNull(creds.getToken(ProviderRoute.GEMINI_HYDROGEN_RELAY))
        assertNull(creds.getToken(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT))
        assertFalse(state.hasToken)
        assertEquals("", configStore.config.baseUrl)
        assertEquals(RelayConfig(), state.relayConfig)
        assertEquals("All local data deleted.", state.notice)
        assertFalse(state.isSending)
    }
}
