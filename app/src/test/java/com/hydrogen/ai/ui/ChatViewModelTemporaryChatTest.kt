package com.hydrogen.ai.ui

import com.hydrogen.ai.data.ConversationStore
import com.hydrogen.ai.data.InMemoryPersonalStore
import com.hydrogen.ai.data.InMemoryRelayCredentialStore
import com.hydrogen.ai.data.InMemoryUserMemoryStore
import com.hydrogen.ai.data.RelayConfigStore
import com.hydrogen.ai.data.UserMemoryState
import com.hydrogen.ai.model.AttachmentManifestItem
import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.Conversation
import com.hydrogen.ai.model.MemoryItem
import com.hydrogen.ai.model.OrbitInstructions
import com.hydrogen.ai.model.PersonalProfile
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import com.hydrogen.ai.model.SystemPromptPolicy
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTemporaryChatTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Remembers every list ever saved, so a test can prove a temporary chat was never among them. */
    private class RecordingConversationStore : ConversationStore {
        var saved: List<Conversation> = emptyList()
        val everSaved = mutableListOf<Conversation>()
        override suspend fun loadConversations(): List<Conversation> = saved
        override suspend fun saveConversations(conversations: List<Conversation>) {
            saved = conversations
            everSaved += conversations
        }
    }

    private class FakeConfigStore(initial: RelayConfig) : RelayConfigStore {
        private var config = initial
        override fun loadConfig(): RelayConfig = config
        override fun saveConfig(config: RelayConfig) {
            this.config = config
        }
    }

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

    private class Setup(
        val vm: ChatViewModel,
        val store: RecordingConversationStore,
        val backend: RecordingBackend,
        val memory: InMemoryUserMemoryStore,
        val personal: InMemoryPersonalStore,
        val deleted: MutableList<String>
    )

    private fun setup(
        memory: InMemoryUserMemoryStore = InMemoryUserMemoryStore(),
        personal: InMemoryPersonalStore = InMemoryPersonalStore()
    ): Setup {
        val store = RecordingConversationStore()
        val backend = RecordingBackend()
        val deleted = mutableListOf<String>()
        val clock = longArrayOf(1_000L)
        val vm = ChatViewModel(
            store, FakeConfigStore(config), creds, backend,
            onDeleteLocalAttachment = { deleted += it },
            memoryStore = memory,
            personalStore = personal
        ) { clock[0]++ }
        return Setup(vm, store, backend, memory, personal, deleted)
    }

    private fun ChatViewModel.send(text: String) {
        updateDraft(text)
        sendMessage()
    }

    @Test
    fun temporaryChatIsNeverSavedAndSkipsMemoryButKeepsPersonal() = runTest(testDispatcher) {
        val s = setup(
            memory = InMemoryUserMemoryStore(UserMemoryState(items = listOf(MemoryItem("m", "I live in Toronto", 1L)))),
            personal = InMemoryPersonalStore(PersonalProfile(name = "Sam"))
        )
        advanceUntilIdle()

        s.vm.startTemporaryChat()
        assertTrue(s.vm.uiState.value.isTemporaryChat)
        s.vm.send("Secret question")
        advanceUntilIdle()

        val system = systemOf(s.backend.requests.single())!!.content
        assertTrue(system.startsWith(OrbitInstructions.DEFAULT_STYLE))
        assertTrue(system.contains("Their name is Sam."))
        assertFalse(system.contains("Toronto"))

        val state = s.vm.uiState.value
        assertTrue(state.activeConversation!!.messages.any { it.content == "Secret question" })
        assertTrue(state.savedConversations.none { it.temporary })
        assertTrue(s.store.everSaved.none { c -> c.temporary || c.messages.any { it.content == "Secret question" } })
        assertFalse(s.vm.exportBackupJson().contains("Secret question"))
        assertFalse(s.vm.exportAllMarkdown().contains("Secret question"))
    }

    @Test
    fun rememberCommandInTemporaryChatGoesToTheModelInstead() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()

        s.vm.startTemporaryChat()
        s.vm.send("Remember that my dog is called Pixel.")
        advanceUntilIdle()

        assertEquals(1, s.backend.requests.size)
        assertTrue(s.vm.uiState.value.memoryItems.isEmpty())
        assertTrue(s.memory.load().items.isEmpty())
    }

    @Test
    fun turningItOffGoesBackToThePreviousChatAndDiscardsIt() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()
        val before = s.vm.uiState.value.activeConversationId!!

        s.vm.startTemporaryChat()
        s.vm.send("Hello")
        advanceUntilIdle()
        s.vm.leaveTemporaryChat()
        advanceUntilIdle()

        val state = s.vm.uiState.value
        assertEquals(before, state.activeConversationId)
        assertFalse(state.isTemporaryChat)
        assertTrue(state.conversations.none { it.temporary })
        assertEquals(1, state.conversations.size)
    }

    @Test
    fun togglingOnAndOffOnAnEmptyChatLeavesNoBlankChatsBehind() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()
        val before = s.vm.uiState.value.activeConversationId!!

        s.vm.startTemporaryChat()
        s.vm.startTemporaryChat()
        assertEquals(1, s.vm.uiState.value.conversations.count { it.temporary })
        s.vm.leaveTemporaryChat()
        s.vm.startTemporaryChat()
        s.vm.leaveTemporaryChat()
        advanceUntilIdle()

        val state = s.vm.uiState.value
        assertEquals(before, state.activeConversationId)
        assertEquals(1, state.conversations.size)
    }

    @Test
    fun newChatOrOpeningAnotherChatDiscardsIt() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()
        val saved = s.vm.uiState.value.activeConversationId!!

        s.vm.startTemporaryChat()
        s.vm.send("One")
        advanceUntilIdle()
        s.vm.createNewConversation()
        advanceUntilIdle()
        assertTrue(s.vm.uiState.value.conversations.none { it.temporary })
        assertFalse(s.vm.uiState.value.isTemporaryChat)

        s.vm.startTemporaryChat()
        s.vm.send("Two")
        advanceUntilIdle()
        s.vm.selectConversation(saved)
        advanceUntilIdle()
        val state = s.vm.uiState.value
        assertEquals(saved, state.activeConversationId)
        assertTrue(state.conversations.none { it.temporary })
        assertTrue(state.conversations.none { c -> c.messages.any { it.content == "One" || it.content == "Two" } })
    }

    @Test
    fun unsentTextInATemporaryChatIsDroppedWithIt() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()
        s.vm.updateDraft("draft for the saved chat")

        s.vm.startTemporaryChat()
        assertEquals("", s.vm.uiState.value.draftText)
        s.vm.updateDraft("typed but never sent")
        s.vm.leaveTemporaryChat()
        advanceUntilIdle()

        assertEquals("draft for the saved chat", s.vm.uiState.value.draftText)
    }

    @Test
    fun temporaryChatCannotInheritSavedDraftAttachments() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()
        val savedId = s.vm.uiState.value.activeConversationId!!
        s.vm.addStagedAttachment(AttachmentManifestItem("saved", "saved.png", "image/png", 10L, "file:/tmp/saved.png"))
        s.vm.startTemporaryChat()
        assertTrue(s.vm.uiState.value.stagedAttachments.isEmpty())
        s.vm.addStagedAttachment(AttachmentManifestItem("temp", "temp.png", "image/png", 10L, "file:/tmp/temp.png"))
        s.vm.leaveTemporaryChat()
        assertEquals(savedId, s.vm.uiState.value.activeConversationId)
        assertEquals(listOf("saved"), s.vm.uiState.value.stagedAttachments.map { it.id })
        assertEquals(listOf("file:/tmp/temp.png"), s.deleted)
    }

    @Test
    fun deletingParkedDraftsAndAllDataDisposesOnlyTheirFiles() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()
        val first = s.vm.uiState.value.activeConversationId!!
        s.vm.addStagedAttachment(AttachmentManifestItem("a", "a.png", "image/png", 10L, "file:/tmp/a.png"))
        s.vm.createNewConversation()
        advanceUntilIdle()
        s.vm.addStagedAttachment(AttachmentManifestItem("b", "b.png", "image/png", 10L, "file:/tmp/b.png"))
        s.vm.deleteConversation(first)
        advanceUntilIdle()
        assertEquals(listOf("file:/tmp/a.png"), s.deleted)
        assertEquals(listOf("b"), s.vm.uiState.value.stagedAttachments.map { it.id })
        s.vm.createNewConversation()
        advanceUntilIdle()
        s.vm.deleteAllLocalData()
        advanceUntilIdle()
        assertEquals(listOf("file:/tmp/a.png", "file:/tmp/b.png"), s.deleted)
        assertTrue(s.vm.uiState.value.parkedDrafts.isEmpty())
    }

    @Test
    fun lateImportsBelongToOriginatingChatAndDeletedTargetsRejectFiles() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()
        val origin = s.vm.uiState.value.activeConversationId!!
        s.vm.updateDraft("Original draft")
        s.vm.createNewConversation()
        advanceUntilIdle()
        s.vm.updateDraft("Unrelated draft")
        assertTrue(s.vm.appendImportedText(origin, "Document text"))
        assertTrue(s.vm.addStagedAttachment(
            AttachmentManifestItem("late", "late.png", "image/png", 10L, "file:/tmp/late.png"), origin))
        assertEquals("Unrelated draft", s.vm.uiState.value.draftText)
        assertTrue(s.vm.uiState.value.stagedAttachments.isEmpty())
        s.vm.selectConversation(origin)
        assertTrue(s.vm.uiState.value.draftText.contains("Document text"))
        assertEquals(listOf("late"), s.vm.uiState.value.stagedAttachments.map { it.id })
        s.vm.deleteConversation(origin)
        advanceUntilIdle()
        assertFalse(s.vm.appendImportedText(origin, "Must not appear"))
        assertFalse(s.vm.addStagedAttachment(
            AttachmentManifestItem("deleted", "deleted.png", "image/png", 10L, "file:/tmp/deleted.png"), origin))
        assertTrue(s.deleted.contains("file:/tmp/deleted.png"))
        assertFalse(s.vm.uiState.value.draftText.contains("Must not appear"))
    }

    @Test
    fun branchingIsOffInATemporaryChat() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()

        s.vm.startTemporaryChat()
        s.vm.send("Hello")
        advanceUntilIdle()
        val tempId = s.vm.uiState.value.activeConversationId
        val count = s.vm.uiState.value.conversations.size
        s.vm.branchFrom(s.vm.uiState.value.activeConversation!!.messages.first().id)
        advanceUntilIdle()

        assertEquals(tempId, s.vm.uiState.value.activeConversationId)
        assertEquals(count, s.vm.uiState.value.conversations.size)
    }

    @Test
    fun filesOnlyTheTemporaryChatUsedAreDeletedWhenItEnds() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()

        s.vm.startTemporaryChat()
        s.vm.addStagedAttachment(AttachmentManifestItem("att-t", "snap.png", "image/png", 10L, "file:/tmp/temp-snap.png"))
        s.vm.send("What is this?")
        advanceUntilIdle()
        val sent = s.vm.uiState.value.activeConversation!!.messages.first()
        assertEquals("file:/tmp/temp-snap.png", sent.attachments.single().localUri)

        s.vm.leaveTemporaryChat()
        advanceUntilIdle()

        assertEquals(listOf("file:/tmp/temp-snap.png"), s.deleted)
    }

    @Test
    fun filesAttachedButNotSentInATemporaryChatAreDroppedWithIt() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()
        val savedChatId = s.vm.uiState.value.activeConversationId

        s.vm.startTemporaryChat()
        s.vm.addStagedAttachment(AttachmentManifestItem("att-u", "notes.pdf", "application/pdf", 10L, "file:/tmp/unsent.pdf"))
        s.vm.leaveTemporaryChat()
        advanceUntilIdle()

        assertEquals(savedChatId, s.vm.uiState.value.activeConversationId)
        assertTrue(s.vm.uiState.value.stagedAttachments.isEmpty())
        assertEquals(listOf("file:/tmp/unsent.pdf"), s.deleted)
    }

    @Test
    fun savePersonalPersistsAndReachesEveryChat() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()

        s.vm.savePersonal(PersonalProfile(name = "  Sam   Lee ", about = "I teach math.", replyStyle = ""))
        advanceUntilIdle()
        assertEquals(PersonalProfile(name = "Sam Lee", about = "I teach math."), s.personal.load())
        assertEquals("Sam Lee", s.vm.uiState.value.personal.name)

        s.vm.send("Hello")
        advanceUntilIdle()

        val system = systemOf(s.backend.requests.single())!!.content
        assertTrue(system.indexOf(OrbitInstructions.DEFAULT_STYLE) == 0)
        assertTrue(system.contains("Their name is Sam Lee."))
        assertTrue(system.contains("I teach math."))
        assertFalse(system.contains("How the user wants you to reply"))
        // Personal rides in the system message only; it is never written into the chat.
        assertTrue(s.store.saved.flatMap { it.messages }.none { it.content.contains("I teach math.") })
    }

    @Test
    fun personalLoadsOnStartAndDeleteAllWipesIt() = runTest(testDispatcher) {
        val s = setup(personal = InMemoryPersonalStore(PersonalProfile(name = "Sam", replyStyle = "Keep it short.")))
        advanceUntilIdle()
        assertEquals("Sam", s.vm.uiState.value.personal.name)

        s.vm.deleteAllLocalData()
        advanceUntilIdle()

        assertTrue(s.vm.uiState.value.personal.isEmpty)
        assertTrue(s.personal.load().isEmpty)
    }

    @Test
    fun editingAndCancellingPreservesUnsentDraftFiles() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()
        s.vm.send("First question")
        advanceUntilIdle()
        val sentId = s.vm.uiState.value.activeConversation!!.messages.first().id
        val draft = AttachmentManifestItem("draft", "notes.png", "image/png", 10L, "file:/tmp/draft.png")
        s.vm.updateDraft("Unsent follow-up")
        s.vm.addStagedAttachment(draft)
        s.vm.startEditingMessage(sentId)
        assertEquals("First question", s.vm.uiState.value.draftText)
        assertTrue(s.vm.uiState.value.stagedAttachments.isEmpty())
        assertFalse(s.deleted.contains(draft.localUri))
        s.vm.cancelEditing()
        assertEquals("Unsent follow-up", s.vm.uiState.value.draftText)
        assertEquals(listOf(draft), s.vm.uiState.value.stagedAttachments)
        assertFalse(s.deleted.contains(draft.localUri))
        s.vm.startEditingMessage(sentId)
        s.vm.updateDraft("Edited question")
        s.vm.sendMessage()
        advanceUntilIdle()
        assertEquals("Unsent follow-up", s.vm.uiState.value.draftText)
        assertEquals(listOf(draft), s.vm.uiState.value.stagedAttachments)
        assertFalse(s.deleted.contains(draft.localUri))
    }
}
