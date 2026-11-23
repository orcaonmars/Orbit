package com.hydrogen.ai.ui

import com.hydrogen.ai.data.ConversationStore
import com.hydrogen.ai.data.InMemoryRelayCredentialStore
import com.hydrogen.ai.data.InMemoryUserMemoryStore
import com.hydrogen.ai.data.RelayConfigStore
import com.hydrogen.ai.data.UserMemoryState
import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.Conversation
import com.hydrogen.ai.model.MemoryItem
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.OrbitInstructions
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import com.hydrogen.ai.model.SystemPromptPolicy
import com.hydrogen.ai.model.UserMemory
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
class ChatViewModelMemoryTest {

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

    /** Records every dispatch so tests can inspect what would have been sent to the provider. */
    private class RecordingBackend : ChatBackend {
        val requests = mutableListOf<ChatDispatchRequest>()
        override suspend fun checkHealth(config: RelayConfig, bearerToken: String?) =
            RelayHealthResult.Reachable(authenticated = true, detail = "ok")

        override suspend fun sendChat(
            request: ChatDispatchRequest,
            config: RelayConfig,
            bearerToken: String?,
            onStreamDelta: suspend (StreamDelta) -> Unit
        ): ChatDispatchResult {
            requests += request
            return ChatDispatchResult.Success(assistantText = "ok", modelId = config.modelId, wasStreamed = false)
        }
    }

    private val config = RelayConfig(route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT, modelId = "gemini-2.5-flash")
    private val creds = InMemoryRelayCredentialStore(mapOf(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT to "test-key"))

    private fun systemOf(request: ChatDispatchRequest): ChatMessage? =
        request.messages.firstOrNull { it.id == SystemPromptPolicy.MESSAGE_ID }

    @Test
    fun loadsSavedMemoryOnStart() = runTest(testDispatcher) {
        val store = InMemoryUserMemoryStore(
            UserMemoryState(items = listOf(MemoryItem("a", "I live in Toronto", 1L)), enabled = false)
        )
        val vm = ChatViewModel(FakeConversationStore(), FakeConfigStore(config), creds, RecordingBackend(),
            memoryStore = store) { 1_000L }
        advanceUntilIdle()

        assertEquals(listOf("I live in Toronto"), vm.uiState.value.memoryItems.map { it.text })
        assertFalse(vm.uiState.value.memoryEnabled)
    }

    @Test
    fun rememberCommandSavesLocallyWithoutCallingProvider() = runTest(testDispatcher) {
        val store = InMemoryUserMemoryStore()
        val backend = RecordingBackend()
        val vm = ChatViewModel(FakeConversationStore(), FakeConfigStore(config), creds, backend,
            memoryStore = store) { 1_000L }
        advanceUntilIdle()

        vm.updateDraft("Remember that my dog is called Pixel.")
        vm.sendMessage()
        advanceUntilIdle()

        assertTrue(backend.requests.isEmpty())
        assertEquals("", vm.uiState.value.draftText)
        assertEquals(listOf("my dog is called Pixel"), vm.uiState.value.memoryItems.map { it.text })
        assertEquals("Saved to memory: my dog is called Pixel", vm.uiState.value.notice)
        assertEquals(listOf("my dog is called Pixel"), store.load().items.map { it.text })
        assertTrue(vm.uiState.value.activeConversation!!.messages.isEmpty())

        vm.clearNotice()
        assertNull(vm.uiState.value.notice)
    }

    @Test
    fun rememberingTheSameThingTwiceSaysAlreadySaved() = runTest(testDispatcher) {
        val vm = ChatViewModel(FakeConversationStore(), FakeConfigStore(config), creds, RecordingBackend()) { 1_000L }
        advanceUntilIdle()

        vm.updateDraft("remember: I use metric units")
        vm.sendMessage()
        vm.updateDraft("Remember that I use metric units")
        vm.sendMessage()
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.memoryItems.size)
        assertEquals("Already in memory: I use metric units", vm.uiState.value.notice)
    }

    @Test
    fun memoryIsSentInSystemMessageButNeverStoredInHistory() = runTest(testDispatcher) {
        val store = InMemoryUserMemoryStore(UserMemoryState(items = listOf(MemoryItem("a", "Call me Sam", 1L))))
        val backend = RecordingBackend()
        val conversations = FakeConversationStore()
        val vm = ChatViewModel(conversations, FakeConfigStore(config), creds, backend, memoryStore = store) { 1_000L }
        advanceUntilIdle()

        vm.updateDraft("Hello")
        vm.sendMessage()
        advanceUntilIdle()

        val system = systemOf(backend.requests.single())
        assertEquals(MessageRole.SYSTEM, system?.role)
        // Orbit's default style leads; memory follows it in the same message.
        assertTrue(system!!.content.startsWith(OrbitInstructions.DEFAULT_STYLE))
        assertTrue(system.content.contains(UserMemory.HEADER))
        assertTrue(system.content.contains("- Call me Sam"))
        val stored = conversations.saved.flatMap { it.messages }
        assertFalse(stored.any { it.content.contains("Call me Sam") })
    }

    @Test
    fun memoryComesBeforeChatInstructions() = runTest(testDispatcher) {
        val store = InMemoryUserMemoryStore(UserMemoryState(items = listOf(MemoryItem("a", "Call me Sam", 1L))))
        val backend = RecordingBackend()
        val vm = ChatViewModel(FakeConversationStore(), FakeConfigStore(config), creds, backend, memoryStore = store) { 1_000L }
        advanceUntilIdle()
        vm.setSystemPrompt(vm.uiState.value.activeConversationId!!, "Answer in French.")
        advanceUntilIdle()

        vm.updateDraft("Hello")
        vm.sendMessage()
        advanceUntilIdle()

        val content = systemOf(backend.requests.single())!!.content
        assertTrue(content.indexOf("Call me Sam") < content.indexOf("Answer in French."))
        assertEquals(1, backend.requests.single().messages.count { it.role == MessageRole.SYSTEM })
    }

    @Test
    fun switchingMemoryOffKeepsNotesButStopsSendingThem() = runTest(testDispatcher) {
        val store = InMemoryUserMemoryStore(UserMemoryState(items = listOf(MemoryItem("a", "Call me Sam", 1L))))
        val backend = RecordingBackend()
        val vm = ChatViewModel(FakeConversationStore(), FakeConfigStore(config), creds, backend, memoryStore = store) { 1_000L }
        advanceUntilIdle()

        vm.setMemoryEnabled(false)
        vm.updateDraft("Hello")
        vm.sendMessage()
        advanceUntilIdle()

        val system = systemOf(backend.requests.single())!!.content
        assertFalse(system.contains("Call me Sam"))
        assertFalse(system.contains(UserMemory.HEADER))
        assertFalse(store.load().enabled)
        assertEquals(1, store.load().items.size)
    }

    @Test
    fun addEditRemoveAndClearPersist() = runTest(testDispatcher) {
        val store = InMemoryUserMemoryStore()
        var now = 1_000L
        val vm = ChatViewModel(FakeConversationStore(), FakeConfigStore(config), creds, RecordingBackend(),
            memoryStore = store) { now++ }
        advanceUntilIdle()

        assertTrue(vm.addMemory("  I like   tea  "))
        assertFalse(vm.addMemory("i like tea"))
        assertFalse(vm.addMemory("   "))
        assertTrue(vm.addMemory("I work nights"))
        advanceUntilIdle()
        assertEquals(listOf("I work nights", "I like tea"), store.load().items.map { it.text })

        val teaId = vm.uiState.value.memoryItems.first { it.text == "I like tea" }.id
        assertTrue(vm.updateMemory(teaId, "I like green tea"))
        assertFalse(vm.updateMemory(teaId, "I work nights"))
        advanceUntilIdle()
        assertEquals(listOf("I work nights", "I like green tea"), store.load().items.map { it.text })

        vm.removeMemory(teaId)
        advanceUntilIdle()
        assertEquals(listOf("I work nights"), store.load().items.map { it.text })

        vm.clearMemory()
        advanceUntilIdle()
        assertTrue(store.load().items.isEmpty())
    }

    @Test
    fun rememberWhenIsNotAMemoryCommand() = runTest(testDispatcher) {
        val backend = RecordingBackend()
        val vm = ChatViewModel(FakeConversationStore(), FakeConfigStore(config), creds, backend) { 1_000L }
        advanceUntilIdle()

        vm.updateDraft("Remember when we met?")
        vm.sendMessage()
        advanceUntilIdle()

        // "Remember when…" is not a memory command, so it goes to the model as usual.
        assertEquals(1, backend.requests.size)
        assertTrue(vm.uiState.value.memoryItems.isEmpty())
    }
}
