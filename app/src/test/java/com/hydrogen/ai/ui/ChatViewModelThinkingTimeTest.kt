package com.hydrogen.ai.ui

import com.hydrogen.ai.data.ConversationStore
import com.hydrogen.ai.data.InMemoryRelayCredentialStore
import com.hydrogen.ai.data.RelayConfigStore
import com.hydrogen.ai.model.Conversation
import com.hydrogen.ai.model.GeminiTurnMetadata
import com.hydrogen.ai.model.MessageStatus
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import com.hydrogen.ai.model.ThoughtLineText
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** "Thought for N seconds" is measured from the first thinking text to the answer's first word. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelThinkingTimeTest {

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

    private fun backend(clock: LongArray, thinks: Boolean) = object : ChatBackend {
        override suspend fun checkHealth(config: RelayConfig, bearerToken: String?) =
            RelayHealthResult.Reachable(authenticated = true, detail = "ok")

        override suspend fun sendChat(
            request: ChatDispatchRequest,
            config: RelayConfig,
            bearerToken: String?,
            onStreamDelta: suspend (StreamDelta) -> Unit
        ): ChatDispatchResult {
            // The model is never sent earlier thinking.
            assertTrue(request.messages.none { it.content.contains("Weighing") })
            clock[0] = 10_000
            if (thinks) onStreamDelta(StreamDelta(thinkingDelta = "Weighing "))
            clock[0] = 15_000
            if (thinks) onStreamDelta(StreamDelta(thinkingDelta = "the options."))
            clock[0] = 22_400
            onStreamDelta(StreamDelta(textDelta = "Pick B."))
            clock[0] = 30_000
            return ChatDispatchResult.Success(
                assistantText = "Pick B.",
                modelId = config.modelId,
                turnMetadata = if (thinks) GeminiTurnMetadata(thinkingSummary = "Weighing the options.") else null,
                usedVision = false,
                wasStreamed = true
            )
        }
    }

    @Test
    fun thinkingTimeRunsFromFirstThoughtToFirstWordAndIsSaved() = runTest(testDispatcher) {
        val clock = longArrayOf(1_000)
        val store = MemoryStore()
        val vm = ChatViewModel(store, FixedConfig(config), creds, backend(clock, thinks = true)) { clock[0] }
        advanceUntilIdle()
        vm.updateDraft("A or B?")
        vm.sendMessage()
        advanceUntilIdle()

        val reply = vm.uiState.value.activeConversation!!.messages.last()
        assertEquals(MessageStatus.COMPLETED, reply.status)
        assertEquals("Pick B.", reply.content)
        assertEquals(12_400L, reply.thinkingMs)
        assertEquals("Thought for 12 seconds", ThoughtLineText.label(live = false, thinkingMs = reply.thinkingMs))
        assertEquals(12_400L, store.saved.first().messages.last().thinkingMs)

        // Asking again: the old thinking stays out of the request (checked in the backend) and the timing restarts.
        vm.updateDraft("Why?")
        vm.sendMessage()
        advanceUntilIdle()
        assertEquals(12_400L, vm.uiState.value.activeConversation!!.messages.last().thinkingMs)
    }

    @Test
    fun aModelThatDoesNotThinkHasNoThinkingTime() = runTest(testDispatcher) {
        val clock = longArrayOf(1_000)
        val vm = ChatViewModel(MemoryStore(), FixedConfig(config), creds, backend(clock, thinks = false)) { clock[0] }
        advanceUntilIdle()
        vm.updateDraft("A or B?")
        vm.sendMessage()
        advanceUntilIdle()

        val reply = vm.uiState.value.activeConversation!!.messages.last()
        assertEquals("Pick B.", reply.content)
        assertNull(reply.thinkingMs)
    }
}
