package com.hydrogen.ai.ui

import com.hydrogen.ai.data.AppVersionStore
import com.hydrogen.ai.data.ChatsLoadProblem
import com.hydrogen.ai.data.FileConversationStore
import com.hydrogen.ai.data.InMemoryAppVersionStore
import com.hydrogen.ai.data.InMemoryRelayCredentialStore
import com.hydrogen.ai.data.RelayConfigStore
import com.hydrogen.ai.model.RelayConfig
import com.hydrogen.ai.network.ChatBackend
import com.hydrogen.ai.network.ChatDispatchRequest
import com.hydrogen.ai.network.ChatDispatchResult
import com.hydrogen.ai.network.RelayHealthResult
import com.hydrogen.ai.network.StreamDelta
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Updating from Orbit 0.1: every chat comes across, "What's new" shows once, and a damaged file never stops the app. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelUpgradeTest {

    @get:Rule val temporaryFolder = TemporaryFolder()
    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(testDispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeConfigStore(var config: RelayConfig = RelayConfig()) : RelayConfigStore {
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

    private val v01: String =
        requireNotNull(javaClass.classLoader?.getResource("upgrade/v0.1-conversations.json")).readText(Charsets.UTF_8)

    private fun chatsFolder(contents: String? = null): File {
        val root = temporaryFolder.newFolder()
        if (contents != null) File(root, "conversations.json").writeText(contents, Charsets.UTF_8)
        return root
    }

    private fun keptCopies(root: File) =
        root.listFiles { f -> f.name.startsWith("conversations.unreadable-") }.orEmpty().toList()

    private fun TestScope.viewModel(root: File, versions: AppVersionStore): ChatViewModel =
        ChatViewModel(
            FileConversationStore(root, ioDispatcher = testDispatcher, clock = { 77L }),
            FakeConfigStore(),
            InMemoryRelayCredentialStore(),
            OkBackend(),
            appVersionStore = versions,
            currentVersionCode = 2
        ) { 1_000L }.also { advanceUntilIdle() }

    @Test fun updateFrom01KeepsEveryChatAndShowsWhatsNewOnce() = runTest(testDispatcher) {
        val root = chatsFolder(v01)
        val versions = InMemoryAppVersionStore()

        val first = viewModel(root, versions)
        val state = first.uiState.value
        assertTrue(state.whatsNew)
        assertNull(state.notice)
        assertEquals(
            listOf("Explain photosynthesis simply", "Plan a weekend in Lisbon with a table"),
            state.conversations.map { it.title }
        )
        assertEquals("Explain photosynthesis simply", state.activeConversation?.title)
        assertTrue(state.conversations.single { it.title.startsWith("Plan a weekend") }.pinned)
        assertEquals(0, versions.lastSeenVersion())

        first.dismissWhatsNew()
        assertFalse(first.uiState.value.whatsNew)
        assertEquals(2, versions.lastSeenVersion())

        val second = viewModel(root, versions)
        assertFalse(second.uiState.value.whatsNew)
        assertEquals(2, second.uiState.value.conversations.size)
        assertTrue(keptCopies(root).isEmpty())
    }

    @Test fun aFreshInstallSkipsTheSheetAndRecordsTheVersion() = runTest(testDispatcher) {
        val versions = InMemoryAppVersionStore()
        val vm = viewModel(chatsFolder(), versions)
        assertFalse(vm.uiState.value.whatsNew)
        assertEquals(2, versions.lastSeenVersion())
    }

    @Test fun anOlderBuildNeverMovesTheRecordedVersionBack() = runTest(testDispatcher) {
        val versions = InMemoryAppVersionStore(5)
        val vm = viewModel(chatsFolder(v01), versions)
        assertFalse(vm.uiState.value.whatsNew)
        assertEquals(5, versions.lastSeenVersion())
    }

    @Test fun aBrokenVersionStoreStartsNormallyWithoutTheSheet() = runTest(testDispatcher) {
        val broken = object : AppVersionStore {
            override fun lastSeenVersion(): Int = error("prefs unavailable")
            override fun recordSeen(versionCode: Int) = error("prefs unavailable")
        }
        val vm = viewModel(chatsFolder(v01), broken)
        assertFalse(vm.uiState.value.whatsNew)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(2, vm.uiState.value.conversations.size)
    }

    @Test fun aDamagedMessageIsReportedAndTheFileWrittenBackClean() = runTest(testDispatcher) {
        val damaged = v01.replace(
            "\"role\":\"USER\",\"content\":\"Now make it shorter\"",
            "\"role\":\"ROBOT\",\"content\":\"Now make it shorter\""
        )
        val root = chatsFolder(damaged)
        val vm = viewModel(root, InMemoryAppVersionStore())
        val state = vm.uiState.value
        assertEquals(ChatsLoadProblem(skippedMessages = 1).notice, state.notice)
        assertEquals(3, state.conversations.single { it.pinned }.messages.size)
        assertEquals("conversations.unreadable-77.json", keptCopies(root).single().name)
        assertEquals(damaged, keptCopies(root).single().readText(Charsets.UTF_8))

        // The clean file reads with nothing to report, so the next start keeps no second copy.
        val reread = FileConversationStore(root, ioDispatcher = testDispatcher)
        assertEquals(2, reread.loadConversations().size)
        assertNull(reread.lastLoadProblem())
    }

    @Test fun anUnreadableFileStartsFreshKeepsACopyAndDeleteAllRemovesIt() = runTest(testDispatcher) {
        val root = chatsFolder("this is not a chats file")
        val vm = viewModel(root, InMemoryAppVersionStore())
        val state = vm.uiState.value
        assertEquals(ChatsLoadProblem(unreadable = true).notice, state.notice)
        assertFalse(state.isLoading)
        assertEquals(1, state.conversations.size)
        assertTrue(state.conversations.single().messages.isEmpty())
        assertEquals(1, keptCopies(root).size)

        vm.deleteAllLocalData()
        advanceUntilIdle()
        assertTrue(keptCopies(root).isEmpty())
    }
}
