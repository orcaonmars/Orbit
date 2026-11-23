package com.hydrogen.ai.ui

import com.hydrogen.ai.data.ConversationStore
import com.hydrogen.ai.data.InMemoryRelayCredentialStore
import com.hydrogen.ai.data.RelayConfigStore
import com.hydrogen.ai.model.Conversation
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.MessageStatus
import com.hydrogen.ai.model.MessageVersions
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Regenerate and edit keep the old turn as a version, and the arrows flip between them. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelVersionsTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class MemoryStore : ConversationStore {
        var saved: List<Conversation> = emptyList()
        override suspend fun loadConversations(): List<Conversation> = saved
        override suspend fun saveConversations(conversations: List<Conversation>) {
            saved = conversations
        }
    }

    private class FixedConfig(private val config: RelayConfig) : RelayConfigStore {
        override fun loadConfig(): RelayConfig = config
        override fun saveConfig(config: RelayConfig) = Unit
    }

    private val config = RelayConfig(route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT, modelId = "gemini-2.5-flash")
    private val creds = InMemoryRelayCredentialStore(mapOf(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT to "test-key"))

    /** Answers "Answer 1", "Answer 2", ... and records what each request carried. */
    private class CountingBackend : ChatBackend {
        var calls = 0
        val requests = mutableListOf<List<String>>()
        override suspend fun checkHealth(config: RelayConfig, bearerToken: String?) =
            RelayHealthResult.Reachable(authenticated = true, detail = "ok")

        override suspend fun sendChat(
            request: ChatDispatchRequest,
            config: RelayConfig,
            bearerToken: String?,
            onStreamDelta: suspend (StreamDelta) -> Unit
        ): ChatDispatchResult {
            calls++
            requests += request.messages.filter { it.role != MessageRole.SYSTEM }.map { it.content }
            return ChatDispatchResult.Success(
                assistantText = "Answer $calls",
                modelId = config.modelId,
                turnMetadata = null,
                usedVision = false,
                wasStreamed = false
            )
        }
    }

    private fun shown(vm: ChatViewModel) = vm.uiState.value.activeConversation!!.messages

    @Test
    fun regenerateKeepsTheOldReplyAndTheArrowsFlipBetweenThem() = runTest(testDispatcher) {
        val clock = longArrayOf(1_000)
        val store = MemoryStore()
        val backend = CountingBackend()
        val vm = ChatViewModel(store, FixedConfig(config), creds, backend) { clock[0]++ }
        advanceUntilIdle()
        vm.updateDraft("Name a color")
        vm.sendMessage()
        advanceUntilIdle()
        val first = shown(vm).last()
        assertEquals("Answer 1", first.content)

        vm.retryMessage(first.id)
        advanceUntilIdle()
        val second = shown(vm).last()
        assertEquals("Answer 2", second.content)
        assertEquals(MessageStatus.COMPLETED, second.status)
        assertNotEquals(first.id, second.id)
        assertEquals(2, MessageVersions.count(second))
        assertEquals(1, MessageVersions.position(second))
        assertEquals(2, shown(vm).size)
        // The regenerate request carried the question only, not the old answer.
        assertEquals(listOf("Name a color"), backend.requests.last())

        vm.showVersion(second.id, 0)
        advanceUntilIdle()
        assertEquals("Answer 1", shown(vm).last().content)
        assertEquals(0, MessageVersions.position(shown(vm).last()))
        assertEquals("Answer 1", store.saved.first().messages.last().content)

        vm.showVersion(shown(vm).last().id, 1)
        advanceUntilIdle()
        assertEquals("Answer 2", shown(vm).last().content)
        assertEquals(2, backend.calls)
    }

    @Test
    fun editAndResendKeepsTheOldTurnAsAVersion() = runTest(testDispatcher) {
        val clock = longArrayOf(1_000)
        val store = MemoryStore()
        val backend = CountingBackend()
        val vm = ChatViewModel(store, FixedConfig(config), creds, backend) { clock[0]++ }
        advanceUntilIdle()
        vm.updateDraft("Plan a trip")
        vm.sendMessage()
        advanceUntilIdle()
        vm.updateDraft("Make it cheaper")
        vm.sendMessage()
        advanceUntilIdle()
        val firstQuestion = shown(vm)[0]

        vm.startEditingMessage(firstQuestion.id)
        vm.updateDraft("Plan a weekend trip")
        vm.sendMessage()
        advanceUntilIdle()

        val now = shown(vm)
        assertEquals(listOf("Plan a weekend trip", "Answer 3"), now.map { it.content })
        assertEquals(2, MessageVersions.count(now[0]))
        assertEquals(
            listOf("Plan a trip", "Answer 1", "Make it cheaper", "Answer 2"),
            now[0].otherVersions.single().messages.map { it.content }
        )
        // Only the new version went to the model.
        assertEquals(listOf("Plan a weekend trip"), backend.requests.last())

        vm.showVersion(now[0].id, 0)
        advanceUntilIdle()
        assertEquals(listOf("Plan a trip", "Answer 1", "Make it cheaper", "Answer 2"), shown(vm).map { it.content })
        val saved = store.saved.first().messages
        assertEquals(4, saved.size)
        assertEquals("Plan a weekend trip", saved[0].otherVersions.single().messages.first().content)
    }

    @Test
    fun theArrowsWaitWhileAReplyIsOnItsWayOrAMessageIsBeingEdited() = runTest(testDispatcher) {
        val clock = longArrayOf(1_000)
        val backend = CountingBackend()
        val vm = ChatViewModel(MemoryStore(), FixedConfig(config), creds, backend) { clock[0]++ }
        advanceUntilIdle()
        vm.updateDraft("Hi")
        vm.sendMessage()
        advanceUntilIdle()
        vm.retryMessage(shown(vm).last().id)
        advanceUntilIdle()
        val before = shown(vm)

        vm.startEditingMessage(before[0].id)
        vm.showVersion(before.last().id, 0)
        assertTrue(shown(vm) === before)
        vm.cancelEditing()

        vm.updateDraft("Another")
        vm.sendMessage()
        assertTrue(vm.uiState.value.isSending)
        val sending = shown(vm)
        vm.showVersion(before.last().id, 0)
        assertTrue(shown(vm) === sending)
        advanceUntilIdle()
    }
}
