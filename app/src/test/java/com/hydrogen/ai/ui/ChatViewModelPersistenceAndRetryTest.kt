package com.hydrogen.ai.ui

import com.hydrogen.ai.data.ConversationStore
import com.hydrogen.ai.data.FileConversationStore
import com.hydrogen.ai.data.InMemoryRelayCredentialStore
import com.hydrogen.ai.data.RelayConfigStore
import com.hydrogen.ai.model.AttachmentManifestItem
import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.Conversation
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.MessageStatus
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import com.hydrogen.ai.network.ChatBackend
import com.hydrogen.ai.network.ChatDispatchRequest
import com.hydrogen.ai.network.ChatDispatchResult
import com.hydrogen.ai.network.RelayHealthResult
import com.hydrogen.ai.network.StreamDelta
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelPersistenceAndRetryTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeConfigStore(initial: RelayConfig) : RelayConfigStore {
        private var config = initial
        override fun loadConfig(): RelayConfig = config
        override fun saveConfig(config: RelayConfig) {
            this.config = config
        }
    }

    private fun testConfig() = RelayConfig(
        route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT,
        modelId = "gemini-2.5-flash"
    )

    @Test
    fun restartReconcilesInterruptedMessagesWithoutFabricationAndAllowsRetry() = runTest(testDispatcher) {
        val tempDir = Files.createTempDirectory("hydrogen-vm-restart").toFile()
        try {
            val store: ConversationStore = FileConversationStore(tempDir, testDispatcher)
            val interrupted = Conversation(
                id = "conv-1",
                title = "Interrupted chat",
                createdAtEpochMs = 100L,
                updatedAtEpochMs = 200L,
                route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT,
                modelId = "gemini-2.5-flash",
                messages = listOf(
                    ChatMessage(
                        id = "u1",
                        correlationId = "corr-1",
                        role = MessageRole.USER,
                        content = "Describe image",
                        createdAtEpochMs = 150L,
                        attachments = listOf(
                            AttachmentManifestItem("att-1", "snap.png", "image/png", 42L, "file:/tmp/snap.png")
                        )
                    ),
                    ChatMessage(
                        id = "a1",
                        correlationId = "corr-1",
                        role = MessageRole.ASSISTANT,
                        content = "Partial answer",
                        createdAtEpochMs = 160L,
                        status = MessageStatus.STREAMING
                    )
                )
            )
            store.saveConversations(listOf(interrupted))

            var capturedAttachments: List<AttachmentManifestItem> = emptyList()
            val backend = object : ChatBackend {
                override suspend fun checkHealth(config: RelayConfig, bearerToken: String?) =
                    RelayHealthResult.Reachable(authenticated = true, detail = "ok")

                override suspend fun sendChat(
                    request: ChatDispatchRequest,
                    config: RelayConfig,
                    bearerToken: String?,
                    onStreamDelta: suspend (StreamDelta) -> Unit
                ): ChatDispatchResult {
                    capturedAttachments = request.attachments
                    return ChatDispatchResult.Success(
                        assistantText = "Recovered full reply",
                        modelId = config.modelId,
                        wasStreamed = false,
                        usedVision = true
                    )
                }
            }

            val creds = InMemoryRelayCredentialStore(mapOf(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT to "test-key"))
            val vm = ChatViewModel(store, FakeConfigStore(testConfig()), creds, backend) { 500L }
            advanceUntilIdle()

            val loadedMsg = vm.uiState.value.activeConversation!!.messages[1]
            assertEquals(MessageStatus.FAILED, loadedMsg.status)
            assertEquals("Partial answer", loadedMsg.content)
            assertNotNull(loadedMsg.error)
            assertEquals(MessageStatus.FAILED, store.loadConversations().first().messages[1].status)

            vm.retryMessage("a1")
            advanceUntilIdle()

            val retriedMsg = vm.uiState.value.activeConversation!!.messages[1]
            assertEquals(MessageStatus.COMPLETED, retriedMsg.status)
            assertEquals("Recovered full reply", retriedMsg.content)
            assertEquals("corr-1", retriedMsg.correlationId)
            assertEquals("Partial answer", retriedMsg.otherVersions.single().messages.single().content)
            assertEquals("Partial answer", store.loadConversations().first().messages[1].otherVersions.single().messages.single().content)
            vm.showVersion(retriedMsg.id, 0)
            advanceUntilIdle()
            assertEquals("Partial answer", vm.uiState.value.activeConversation!!.messages[1].content)
            assertEquals(1, capturedAttachments.size)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun cancelAcrossConversationSwitchPreservesPartialOutputAndPersistsAcrossRestart() = runTest(testDispatcher) {
        val tempDir = Files.createTempDirectory("hydrogen-vm-cancel").toFile()
        try {
            val store: ConversationStore = FileConversationStore(tempDir, testDispatcher)
            val streamStarted = CompletableDeferred<Unit>()
            val holdForever = CompletableDeferred<Unit>()

            val backend = object : ChatBackend {
                override suspend fun checkHealth(config: RelayConfig, bearerToken: String?) =
                    RelayHealthResult.Reachable(authenticated = true, detail = "ok")

                override suspend fun sendChat(
                    request: ChatDispatchRequest,
                    config: RelayConfig,
                    bearerToken: String?,
                    onStreamDelta: suspend (StreamDelta) -> Unit
                ): ChatDispatchResult {
                    onStreamDelta(StreamDelta(textDelta = "Hello partial"))
                    streamStarted.complete(Unit)
                    holdForever.await()
                    return ChatDispatchResult.Success(
                        assistantText = "Never reached",
                        modelId = config.modelId,
                        wasStreamed = true
                    )
                }
            }

            val creds = InMemoryRelayCredentialStore(mapOf(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT to "test-key"))
            val vm = ChatViewModel(store, FakeConfigStore(testConfig()), creds, backend) { 1_000L }
            advanceUntilIdle()

            val firstConvId = vm.uiState.value.activeConversationId!!
            vm.updateDraft("Stream something")
            vm.sendMessage()
            runCurrent()
            streamStarted.await()

            vm.createNewConversation()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isSending)

            vm.cancelActiveSend()
            advanceUntilIdle()
            assertFalse(vm.uiState.value.isSending)

            val restartedVm = ChatViewModel(store, FakeConfigStore(testConfig()), creds, backend) { 2_000L }
            advanceUntilIdle()

            val assistantMsg = restartedVm.uiState.value.conversations.first { it.id == firstConvId }.messages.last()
            assertEquals(MessageStatus.CANCELLED, assistantMsg.status)
            assertEquals("Hello partial", assistantMsg.content)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun unexpectedBackendExceptionFailsCleanlyAndAttachmentCleanupRespectsReferences() = runTest(testDispatcher) {
        val tempDir = Files.createTempDirectory("hydrogen-vm-cleanup").toFile()
        try {
            val store: ConversationStore = FileConversationStore(tempDir, testDispatcher)
            val deletedUris = mutableListOf<String>()
            val backend = object : ChatBackend {
                override suspend fun checkHealth(config: RelayConfig, bearerToken: String?) =
                    RelayHealthResult.Reachable(authenticated = true, detail = "ok")

                override suspend fun sendChat(
                    request: ChatDispatchRequest,
                    config: RelayConfig,
                    bearerToken: String?,
                    onStreamDelta: suspend (StreamDelta) -> Unit
                ): ChatDispatchResult {
                    throw IllegalStateException("Boom")
                }
            }

            val creds = InMemoryRelayCredentialStore(mapOf(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT to "test-key"))
            val vm = ChatViewModel(
                conversationStore = store,
                configStore = FakeConfigStore(testConfig()),
                credentialStore = creds,
                backend = backend,
                onDeleteLocalAttachment = { deletedUris += it }
            ) { 1_000L }
            advanceUntilIdle()

            val att = AttachmentManifestItem("att-del", "img.png", "image/png", 10L, "file:/tmp/img.png")
            vm.addStagedAttachment(att)
            vm.updateDraft("Hi")
            vm.sendMessage()
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isSending)
            val failedMsg = vm.uiState.value.activeConversation!!.messages.last()
            assertEquals(MessageStatus.FAILED, failedMsg.status)
            assertEquals(ProviderErrorCategory.UNKNOWN_PROTOCOL, failedMsg.error?.category)
            assertEquals(CLIENT_FAILURE_MESSAGE, failedMsg.error?.userMessage)
            assertEquals("IllegalStateException: Boom", failedMsg.error?.technicalDetail)

            val convId = vm.uiState.value.activeConversationId!!
            vm.deleteConversation(convId)
            advanceUntilIdle()
            assertEquals(listOf("file:/tmp/img.png"), deletedUris)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun persistedMessageAttachmentsAndConfiguredModelSurviveRestartAndNewChat() = runTest(testDispatcher) {
        val tempDir = Files.createTempDirectory("hydrogen-vm-att-persist").toFile()
        try {
            val store: ConversationStore = FileConversationStore(tempDir, testDispatcher)
            val customConfig = RelayConfig(
                route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT,
                modelId = "gemini-2.5-pro"
            )
            val backend = object : ChatBackend {
                override suspend fun checkHealth(config: RelayConfig, bearerToken: String?) =
                    RelayHealthResult.Reachable(authenticated = true, detail = "ok")

                override suspend fun sendChat(
                    request: ChatDispatchRequest,
                    config: RelayConfig,
                    bearerToken: String?,
                    onStreamDelta: suspend (StreamDelta) -> Unit
                ): ChatDispatchResult = ChatDispatchResult.Success(
                    assistantText = "Image noted",
                    modelId = config.modelId,
                    wasStreamed = false,
                    usedVision = true
                )
            }

            val creds = InMemoryRelayCredentialStore(mapOf(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT to "test-key"))
            val vm = ChatViewModel(store, FakeConfigStore(customConfig), creds, backend) { 1_000L }
            advanceUntilIdle()

            val att = AttachmentManifestItem("att-keep", "chart.png", "image/png", 128L, "file:/tmp/chart.png")
            vm.addStagedAttachment(att)
            vm.updateDraft("Analyze chart")
            vm.sendMessage()
            advanceUntilIdle()

            vm.createNewConversation()
            advanceUntilIdle()
            assertEquals("gemini-2.5-pro", vm.uiState.value.activeConversation?.modelId)

            val restartedVm = ChatViewModel(store, FakeConfigStore(customConfig), creds, backend) { 2_000L }
            advanceUntilIdle()

            val savedWithAttachment = restartedVm.uiState.value.conversations.first { conv ->
                conv.messages.any { it.attachments.isNotEmpty() }
            }
            assertEquals("Analyze chart", savedWithAttachment.messages.first().content)
            assertEquals("file:/tmp/chart.png", savedWithAttachment.messages.first().attachments.single().localUri)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun deletingStreamingConversationCancelsSendAndAllowsSubsequentChat() = runTest(testDispatcher) {
        val tempDir = Files.createTempDirectory("hydrogen-vm-del-stream").toFile()
        try {
            val store: ConversationStore = FileConversationStore(tempDir, testDispatcher)
            val streamStarted = CompletableDeferred<Unit>()
            val holdForever = CompletableDeferred<Unit>()
            var callCount = 0

            val backend = object : ChatBackend {
                override suspend fun checkHealth(config: RelayConfig, bearerToken: String?) =
                    RelayHealthResult.Reachable(authenticated = true, detail = "ok")

                override suspend fun sendChat(
                    request: ChatDispatchRequest,
                    config: RelayConfig,
                    bearerToken: String?,
                    onStreamDelta: suspend (StreamDelta) -> Unit
                ): ChatDispatchResult {
                    callCount += 1
                    if (callCount == 1) {
                        onStreamDelta(StreamDelta(textDelta = "Partial"))
                        streamStarted.complete(Unit)
                        holdForever.await()
                    }
                    return ChatDispatchResult.Success(
                        assistantText = "Follow-up reply",
                        modelId = config.modelId,
                        wasStreamed = false
                    )
                }
            }

            val creds = InMemoryRelayCredentialStore(mapOf(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT to "test-key"))
            val vm = ChatViewModel(store, FakeConfigStore(testConfig()), creds, backend) { 1_000L }
            advanceUntilIdle()

            val streamingConvId = vm.uiState.value.activeConversationId!!
            vm.updateDraft("First prompt")
            vm.sendMessage()
            runCurrent()
            streamStarted.await()
            assertTrue(vm.uiState.value.isSending)

            vm.deleteConversation(streamingConvId)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isSending)
            assertFalse(vm.uiState.value.conversations.any { it.id == streamingConvId })

            vm.updateDraft("Second prompt")
            vm.sendMessage()
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isSending)
            val reply = vm.uiState.value.activeConversation!!.messages.last()
            assertEquals(MessageStatus.COMPLETED, reply.status)
            assertEquals("Follow-up reply", reply.content)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun preflightOmittedAttachmentsAreMarkedPreservedLocallyAndRemovedAttachmentRecalculatesWarnings() = runTest(testDispatcher) {
        val tempDir = Files.createTempDirectory("hydrogen-vm-omit").toFile()
        try {
            val store: ConversationStore = FileConversationStore(tempDir, testDispatcher)
            val config = RelayConfig(
                route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT,
                modelId = "gemini-2.5-flash",
                maxImagesPerRequest = 2
            )
            var dispatchedAttachmentIds: List<String> = emptyList()
            val backend = object : ChatBackend {
                override suspend fun checkHealth(config: RelayConfig, bearerToken: String?) =
                    RelayHealthResult.Reachable(authenticated = true, detail = "ok")

                override suspend fun sendChat(
                    request: ChatDispatchRequest,
                    config: RelayConfig,
                    bearerToken: String?,
                    onStreamDelta: suspend (StreamDelta) -> Unit
                ): ChatDispatchResult {
                    dispatchedAttachmentIds = request.attachments.map { it.id }
                    return ChatDispatchResult.Success(
                        assistantText = "Handled 2 newest",
                        modelId = config.modelId,
                        wasStreamed = false,
                        usedVision = true
                    )
                }
            }

            val creds = InMemoryRelayCredentialStore(mapOf(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT to "test-key"))
            val vm = ChatViewModel(store, FakeConfigStore(config), creds, backend) { 1_000L }
            advanceUntilIdle()

            val a1 = AttachmentManifestItem("a1", "1.png", "image/png", 10L, "file:/tmp/1.png")
            val a2 = AttachmentManifestItem("a2", "2.png", "image/png", 10L, "file:/tmp/2.png")
            val a3 = AttachmentManifestItem("a3", "3.png", "image/png", 10L, "file:/tmp/3.png")
            val a4 = AttachmentManifestItem("a4", "4.png", "image/png", 10L, "file:/tmp/4.png")

            vm.addStagedAttachment(a1)
            vm.addStagedAttachment(a2)
            assertTrue(vm.uiState.value.preflightWarnings.isEmpty())

            vm.addStagedAttachment(a3)
            vm.addStagedAttachment(a4)
            assertEquals(1, vm.uiState.value.preflightWarnings.size)

            vm.removeStagedAttachment("a4")
            assertEquals(3, vm.uiState.value.stagedAttachments.size)
            assertEquals(1, vm.uiState.value.preflightWarnings.size)

            vm.updateDraft("Check three images")
            vm.sendMessage()
            advanceUntilIdle()

            assertEquals(listOf("a2", "a3"), dispatchedAttachmentIds)
            val userAttachments = vm.uiState.value.activeConversation!!.messages.first().attachments
            assertEquals(3, userAttachments.size)
            assertTrue(userAttachments[0].omittedFromRemote)
            assertFalse(userAttachments[1].omittedFromRemote)
            assertFalse(userAttachments[2].omittedFromRemote)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun newChatParksAttachmentsAndClearDeletesUnreferencedLocalFiles() = runTest(testDispatcher) {
        val tempDir = Files.createTempDirectory("hydrogen-vm-clear-staged").toFile()
        try {
            val store: ConversationStore = FileConversationStore(tempDir, testDispatcher)
            val deletedUris = mutableListOf<String>()
            val backend = object : ChatBackend {
                override suspend fun checkHealth(config: RelayConfig, bearerToken: String?) =
                    RelayHealthResult.Reachable(authenticated = true, detail = "ok")

                override suspend fun sendChat(
                    request: ChatDispatchRequest,
                    config: RelayConfig,
                    bearerToken: String?,
                    onStreamDelta: suspend (StreamDelta) -> Unit
                ): ChatDispatchResult = error("unused")
            }

            val creds = InMemoryRelayCredentialStore(mapOf(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT to "test-key"))
            val vm = ChatViewModel(
                conversationStore = store,
                configStore = FakeConfigStore(testConfig()),
                credentialStore = creds,
                backend = backend,
                onDeleteLocalAttachment = { deletedUris += it }
            ) { 1_000L }
            advanceUntilIdle()

            vm.addStagedAttachment(AttachmentManifestItem("s1", "one.png", "image/png", 10L, "file:/tmp/one.png"))
            vm.clearStagedAttachments()
            assertTrue(vm.uiState.value.stagedAttachments.isEmpty())
            assertEquals(listOf("file:/tmp/one.png"), deletedUris)

            val first = vm.uiState.value.activeConversationId!!
            vm.addStagedAttachment(AttachmentManifestItem("s2", "two.png", "image/png", 10L, "file:/tmp/two.png"))
            vm.createNewConversation()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.stagedAttachments.isEmpty())
            assertEquals(listOf("file:/tmp/one.png"), deletedUris)
            vm.selectConversation(first)
            assertEquals(listOf("s2"), vm.uiState.value.stagedAttachments.map { it.id })
            vm.clearStagedAttachments()
            assertEquals(listOf("file:/tmp/one.png", "file:/tmp/two.png"), deletedUris)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun unsentDraftStaysWithItsOwnChat() = runTest(testDispatcher) {
        val tempDir = Files.createTempDirectory("hydrogen-vm-drafts").toFile()
        try {
            val store: ConversationStore = FileConversationStore(tempDir, testDispatcher)
            val backend = object : ChatBackend {
                override suspend fun checkHealth(config: RelayConfig, bearerToken: String?): RelayHealthResult =
                    error("unused")

                override suspend fun sendChat(
                    request: ChatDispatchRequest,
                    config: RelayConfig,
                    bearerToken: String?,
                    onStreamDelta: suspend (StreamDelta) -> Unit
                ): ChatDispatchResult = error("unused")
            }
            val creds = InMemoryRelayCredentialStore(mapOf(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT to "test-key"))
            val vm = ChatViewModel(store, FakeConfigStore(testConfig()), creds, backend) { 1_000L }
            advanceUntilIdle()

            val first = vm.uiState.value.activeConversationId!!
            vm.updateDraft("half-written question")
            vm.createNewConversation()
            advanceUntilIdle()
            val second = vm.uiState.value.activeConversationId!!
            assertTrue(first != second)
            assertEquals("a new chat starts with an empty composer", "", vm.uiState.value.draftText)

            vm.updateDraft("other idea")
            vm.selectConversation(first)
            assertEquals("half-written question", vm.uiState.value.draftText)
            vm.selectConversation(second)
            assertEquals("other idea", vm.uiState.value.draftText)
            vm.selectConversation(second)
            assertEquals("re-selecting the open chat keeps its draft", "other idea", vm.uiState.value.draftText)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun firstPromptGivesACleanTitleAndRenamesAreKept() = runTest(testDispatcher) {
        val tempDir = Files.createTempDirectory("hydrogen-vm-titles").toFile()
        try {
            val store: ConversationStore = FileConversationStore(tempDir, testDispatcher)
            val backend = object : ChatBackend {
                override suspend fun checkHealth(config: RelayConfig, bearerToken: String?): RelayHealthResult =
                    error("unused")

                override suspend fun sendChat(
                    request: ChatDispatchRequest,
                    config: RelayConfig,
                    bearerToken: String?,
                    onStreamDelta: suspend (StreamDelta) -> Unit
                ): ChatDispatchResult = ChatDispatchResult.Success(
                    assistantText = "ok",
                    modelId = config.modelId,
                    wasStreamed = false
                )
            }
            val creds = InMemoryRelayCredentialStore(mapOf(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT to "test-key"))
            val vm = ChatViewModel(store, FakeConfigStore(testConfig()), creds, backend) { 1_000L }
            advanceUntilIdle()
            fun activeTitle() = vm.uiState.value.activeConversation!!.title

            assertEquals("New chat", activeTitle())
            vm.updateDraft("## **explain** how `git rebase` works. Then compare it to merge.")
            vm.sendMessage()
            advanceUntilIdle()
            assertEquals("Explain how git rebase works.", activeTitle())

            vm.updateDraft("Something else entirely")
            vm.sendMessage()
            advanceUntilIdle()
            assertEquals("the title is set once", "Explain how git rebase works.", activeTitle())

            vm.renameConversation(vm.uiState.value.activeConversationId!!, "My rebase notes")
            vm.updateDraft("one more")
            vm.sendMessage()
            advanceUntilIdle()
            assertEquals("My rebase notes", activeTitle())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
