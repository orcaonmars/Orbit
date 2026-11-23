package com.hydrogen.ai.ui

import com.hydrogen.ai.data.ConversationStore
import com.hydrogen.ai.data.InMemoryProjectStore
import com.hydrogen.ai.data.InMemoryRelayCredentialStore
import com.hydrogen.ai.data.RelayConfigStore
import com.hydrogen.ai.model.Conversation
import com.hydrogen.ai.model.Project
import com.hydrogen.ai.model.Projects
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelProjectsTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class RecordingConversationStore : ConversationStore {
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

    private class Setup(
        val vm: ChatViewModel,
        val store: RecordingConversationStore,
        val backend: RecordingBackend,
        val projects: InMemoryProjectStore
    )

    private fun setup(projects: InMemoryProjectStore = InMemoryProjectStore()): Setup {
        val store = RecordingConversationStore()
        val backend = RecordingBackend()
        val clock = longArrayOf(1_000L)
        val vm = ChatViewModel(
            store, FakeConfigStore(config), creds, backend,
            onDeleteLocalAttachment = {},
            projectStore = projects
        ) { clock[0]++ }
        return Setup(vm, store, backend, projects)
    }

    private fun ChatViewModel.send(text: String) {
        updateDraft(text)
        sendMessage()
    }

    private fun systemOf(request: ChatDispatchRequest): String =
        request.messages.firstOrNull { it.id == SystemPromptPolicy.MESSAGE_ID }?.content.orEmpty()

    private fun ChatViewModel.chat(id: String): Conversation = uiState.value.conversations.first { it.id == id }

    @Test
    fun aProjectChatSendsTheProjectNameInstructionsAndFiles() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()

        val id = s.vm.createProject("  Trip  to Japan ", "Keep costs in euros.")!!
        assertTrue(s.vm.addProjectFile(id, "plan.txt", "Day 1: Kyoto", truncated = false))
        s.vm.newChatInProject(id)
        advanceUntilIdle()

        assertEquals(id, s.vm.uiState.value.activeConversation!!.projectId)
        assertEquals("Trip to Japan", s.vm.uiState.value.activeProject!!.name)

        s.vm.send("Where do I start?")
        advanceUntilIdle()

        val system = systemOf(s.backend.requests.single())
        assertTrue(system.contains("This chat is part of the user's project \"Trip to Japan\"."))
        assertTrue(system.contains("Project instructions:\nKeep costs in euros."))
        assertTrue(system.contains("<file name=\"plan.txt\">\nDay 1: Kyoto\n</file>"))
        // The brief rides in the system message only; it is never written into the chat.
        assertTrue(s.store.saved.flatMap(Conversation::messages).none { it.content.contains("Kyoto") })
        assertEquals(listOf("Trip to Japan"), s.projects.load().map(Project::name))
    }

    @Test
    fun chatsOutsideTheProjectAndTemporaryChatsGetNoProjectBrief() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()
        val id = s.vm.createProject("Secret plans", "Only for this project.")!!

        s.vm.send("Plain chat")
        advanceUntilIdle()
        s.vm.startTemporaryChat()
        val temp = s.vm.uiState.value.activeConversationId!!
        s.vm.moveToProject(temp, id)
        s.vm.send("Temporary chat")
        advanceUntilIdle()

        assertNull(s.vm.uiState.value.activeConversation!!.projectId)
        assertEquals(2, s.backend.requests.size)
        s.backend.requests.forEach { request ->
            val system = systemOf(request)
            assertFalse(system.contains("Secret plans"))
            assertFalse(system.contains("Only for this project."))
        }
    }

    @Test
    fun newChatInAnUnknownProjectDoesNothingAndBlankNamesAreRefused() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()
        val before = s.vm.uiState.value.activeConversationId

        assertNull(s.vm.createProject("   "))
        s.vm.newChatInProject("missing")
        advanceUntilIdle()

        assertEquals(before, s.vm.uiState.value.activeConversationId)
        assertTrue(s.vm.uiState.value.projects.isEmpty())
    }

    @Test
    fun movingAChatInAndOutOfAProject() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()
        s.vm.send("Hello")
        advanceUntilIdle()
        val chat = s.vm.uiState.value.activeConversationId!!
        val id = s.vm.createProject("Work")!!

        s.vm.moveToProject(chat, id)
        advanceUntilIdle()
        assertEquals(id, s.vm.chat(chat).projectId)
        assertEquals(id, s.store.saved.first { it.id == chat }.projectId)
        assertEquals("Moved to Work.", s.vm.uiState.value.notice)

        s.vm.moveToProject(chat, "missing")
        advanceUntilIdle()
        assertEquals(id, s.vm.chat(chat).projectId)

        s.vm.moveToProject(chat, null)
        advanceUntilIdle()
        assertNull(s.vm.chat(chat).projectId)
        assertEquals("Removed from the project.", s.vm.uiState.value.notice)
    }

    @Test
    fun deletingAProjectKeepsItsChatsAndDropsTheBrief() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()
        val id = s.vm.createProject("Trip", "Keep costs in euros.")!!
        s.vm.newChatInProject(id)
        advanceUntilIdle()
        s.vm.send("First")
        advanceUntilIdle()
        val chat = s.vm.uiState.value.activeConversationId!!

        s.vm.deleteProject(id)
        advanceUntilIdle()

        assertTrue(s.vm.uiState.value.projects.isEmpty())
        assertTrue(s.projects.load().isEmpty())
        assertNull(s.vm.chat(chat).projectId)
        assertNull(s.store.saved.first { it.id == chat }.projectId)
        assertEquals(1, s.vm.chat(chat).messages.count { it.content == "First" })

        s.vm.send("Second")
        advanceUntilIdle()
        assertFalse(systemOf(s.backend.requests.last()).contains("Keep costs in euros."))
    }

    @Test
    fun filesStopAtTwentyAndCanBeRemoved() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()
        val id = s.vm.createProject("Docs")!!

        repeat(Projects.MAX_FILES) { i -> assertTrue(s.vm.addProjectFile(id, "f$i.txt", "text $i", truncated = false)) }
        assertFalse(s.vm.addProjectFile(id, "extra.txt", "more", truncated = false))
        assertFalse(s.vm.addProjectFile("missing", "x.txt", "text", truncated = false))
        val project = s.vm.uiState.value.projects.single()
        assertEquals(Projects.MAX_FILES, project.files.size)

        s.vm.removeProjectFile(id, project.files.first().id)
        assertTrue(s.vm.addProjectFile(id, "blank.txt", "   ", truncated = false).not())
        assertTrue(s.vm.addProjectFile(id, "again.txt", "fits now", truncated = true))
        advanceUntilIdle()

        val saved = s.projects.load().single()
        assertEquals(Projects.MAX_FILES, saved.files.size)
        assertEquals("again.txt", saved.files.last().name)
        assertTrue(saved.files.last().truncated)
    }

    @Test
    fun renamingKeepsTheOldNameWhenTheNewOneIsBlank() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()
        val id = s.vm.createProject("Old")!!

        s.vm.updateProject(id, "  ", "New notes")
        assertEquals("Old", s.vm.uiState.value.projects.single().name)
        assertEquals("New notes", s.vm.uiState.value.projects.single().instructions)

        s.vm.updateProject(id, "New", "")
        advanceUntilIdle()
        assertEquals("New", s.projects.load().single().name)
        assertEquals("", s.projects.load().single().instructions)
    }

    @Test
    fun projectsLoadOnStartAndDeleteAllWipesThem() = runTest(testDispatcher) {
        val s = setup(InMemoryProjectStore(listOf(Project(id = "p1", name = "Saved"))))
        advanceUntilIdle()
        assertEquals(listOf("Saved"), s.vm.uiState.value.projects.map(Project::name))

        s.vm.deleteAllLocalData()
        advanceUntilIdle()

        assertTrue(s.vm.uiState.value.projects.isEmpty())
        assertTrue(s.projects.load().isEmpty())
    }

    @Test
    fun archivingTheOpenChatUnpinsItAndOpensANewOne() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()
        s.vm.send("Keep this")
        advanceUntilIdle()
        val chat = s.vm.uiState.value.activeConversationId!!
        s.vm.togglePinned(chat)
        advanceUntilIdle()
        assertTrue(s.vm.chat(chat).pinned)

        s.vm.setArchived(chat, true)
        advanceUntilIdle()

        val state = s.vm.uiState.value
        assertTrue(state.conversations.first { it.id == chat }.archived)
        assertFalse(state.conversations.first { it.id == chat }.pinned)
        assertTrue(s.store.saved.first { it.id == chat }.archived)
        assertNotEquals(chat, state.activeConversationId)
        assertEquals("Chat archived. Find it under Archived chats.", state.notice)
    }

    @Test
    fun archivingAProjectChatOpensANewChatInTheSameProject() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()
        val id = s.vm.createProject("Trip")!!
        s.vm.newChatInProject(id)
        advanceUntilIdle()
        s.vm.send("Plan day one")
        advanceUntilIdle()
        val chat = s.vm.uiState.value.activeConversationId!!

        s.vm.setArchived(chat, true)
        advanceUntilIdle()

        assertNotEquals(chat, s.vm.uiState.value.activeConversationId)
        assertEquals(id, s.vm.uiState.value.activeConversation!!.projectId)
    }

    @Test
    fun sendingInAnArchivedChatBringsItBack() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()
        s.vm.send("Old chat")
        advanceUntilIdle()
        val chat = s.vm.uiState.value.activeConversationId!!
        s.vm.setArchived(chat, true)
        advanceUntilIdle()

        s.vm.selectConversation(chat)
        advanceUntilIdle()
        s.vm.send("I'm back")
        advanceUntilIdle()

        assertFalse(s.vm.chat(chat).archived)
        assertFalse(s.store.saved.first { it.id == chat }.archived)
    }

    @Test
    fun unarchivingDoesNotChangeTheOpenChat() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()
        s.vm.send("Old chat")
        advanceUntilIdle()
        val chat = s.vm.uiState.value.activeConversationId!!
        s.vm.setArchived(chat, true)
        advanceUntilIdle()
        val open = s.vm.uiState.value.activeConversationId

        s.vm.setArchived(chat, false)
        advanceUntilIdle()

        assertFalse(s.vm.chat(chat).archived)
        assertEquals(open, s.vm.uiState.value.activeConversationId)
        assertEquals("Chat moved back to your list.", s.vm.uiState.value.notice)
    }

    @Test
    fun archiveAndProjectSurviveABackup() = runTest(testDispatcher) {
        val s = setup()
        advanceUntilIdle()
        val id = s.vm.createProject("Trip")!!
        s.vm.newChatInProject(id)
        advanceUntilIdle()
        s.vm.send("In the project")
        advanceUntilIdle()
        val chat = s.vm.uiState.value.activeConversationId!!
        s.vm.setArchived(chat, true)
        advanceUntilIdle()

        val json = s.vm.exportBackupJson()
        val restored = com.hydrogen.ai.data.ChatBackupCodec.decode(json)
        assertTrue(restored is com.hydrogen.ai.data.ChatBackupCodec.DecodeResult.Success)
        val back = (restored as com.hydrogen.ai.data.ChatBackupCodec.DecodeResult.Success).conversations.first { it.id == chat }
        assertTrue(back.archived)
        assertEquals(id, back.projectId)
    }

    @Test
    fun startLineSaysWhatTheChatWillUse() {
        val file = com.hydrogen.ai.model.ProjectFile(id = "f", name = "a.txt", text = "x")
        val base = Project(id = "p", name = "Trip")
        assertEquals("Orbit uses this project's instructions and 1 file.", ProjectCopy.startLine(base.copy(instructions = "Euros.", files = listOf(file))))
        assertEquals("Orbit uses this project's instructions.", ProjectCopy.startLine(base.copy(instructions = "Euros.")))
        assertEquals("Orbit uses this project's 2 files.", ProjectCopy.startLine(base.copy(files = listOf(file, file.copy(id = "g")))))
        assertEquals("Add instructions or files to this project to shape its chats.", ProjectCopy.startLine(base))
    }

    @Test
    fun projectCopyHasNoDashesOrExclamationMarks() {
        val copy = listOf(
            ProjectCopy.NEW_TITLE, ProjectCopy.EXPLAINER, ProjectCopy.NAME, ProjectCopy.INSTRUCTIONS,
            ProjectCopy.INSTRUCTIONS_HINT, ProjectCopy.FILES, ProjectCopy.ADD_FILES, ProjectCopy.FILES_EMPTY,
            ProjectCopy.CREATE, ProjectCopy.DONE, ProjectCopy.DELETE,
            ProjectCopy.startLine(Project(id = "p", name = "Trip")), ProjectCopy.bannerDescription("Trip")
        )
        copy.forEach { text ->
            assertNotNull(text)
            assertFalse(text, text.contains("—"))
            assertFalse(text, text.contains("!"))
        }
    }
}
