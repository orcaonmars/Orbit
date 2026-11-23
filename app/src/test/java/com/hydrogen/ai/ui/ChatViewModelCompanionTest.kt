package com.hydrogen.ai.ui

import com.hydrogen.ai.companion.CompanionApproval
import com.hydrogen.ai.companion.CompanionCapabilities
import com.hydrogen.ai.companion.CompanionClient
import com.hydrogen.ai.companion.CompanionDefaults
import com.hydrogen.ai.companion.CompanionHealthSnapshot
import com.hydrogen.ai.companion.CompanionPairingInputParser
import com.hydrogen.ai.companion.CompanionPathHelper
import com.hydrogen.ai.companion.CompanionResult
import com.hydrogen.ai.companion.CompanionSession
import com.hydrogen.ai.companion.InMemoryCompanionSessionStore
import com.hydrogen.ai.companion.RevokeResponse
import com.hydrogen.ai.companion.WorkActionType
import com.hydrogen.ai.companion.WorkDirectoryEntry
import com.hydrogen.ai.companion.WorkTask
import com.hydrogen.ai.companion.WorkTaskEnvelope
import com.hydrogen.ai.companion.WorkTaskResult
import com.hydrogen.ai.data.ConversationStore
import com.hydrogen.ai.data.InMemoryRelayCredentialStore
import com.hydrogen.ai.data.RelayConfigStore
import com.hydrogen.ai.model.Conversation
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelCompanionTest {

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
        override suspend fun saveConversations(conversations: List<Conversation>) { saved = conversations }
    }

    private class FakeConfigStore(initial: RelayConfig) : RelayConfigStore {
        private var config = initial
        override fun loadConfig(): RelayConfig = config
        override fun saveConfig(config: RelayConfig) { this.config = config }
    }

    private val unusedBackend = object : ChatBackend {
        override suspend fun checkHealth(config: RelayConfig, bearerToken: String?) =
            RelayHealthResult.Reachable(authenticated = true, detail = "ok")
        override suspend fun sendChat(
            request: ChatDispatchRequest,
            config: RelayConfig,
            bearerToken: String?,
            onStreamDelta: suspend (StreamDelta) -> Unit
        ): ChatDispatchResult = error("unused")
    }

    private open class StubCompanionClient : CompanionClient {
        override suspend fun checkHealth(baseUrl: String): CompanionResult<CompanionHealthSnapshot> = error("unused")
        override suspend fun pairDevice(baseUrl: String, deviceName: String, pairingId: String, pairingCode: String): CompanionResult<CompanionSession> = error("unused")
        override suspend fun revokePairing(session: CompanionSession): CompanionResult<RevokeResponse> = error("unused")
        override suspend fun requestApproval(session: CompanionSession, actionType: WorkActionType, targetPath: String, summary: String, idempotencyKey: String): CompanionResult<CompanionApproval> = error("unused")
        override suspend fun getApprovalStatus(session: CompanionSession, approvalId: String): CompanionResult<CompanionApproval> = error("unused")
        override suspend fun startWorkTask(session: CompanionSession, approvalId: String, actionType: WorkActionType, targetPath: String, idempotencyKey: String, maxBytes: Int?, offsetBytes: Int?, maxEntries: Int?): CompanionResult<WorkTaskEnvelope> = error("unused")
        override suspend fun getWorkTaskStatus(session: CompanionSession, taskId: String): CompanionResult<WorkTaskEnvelope> = error("unused")
        override suspend fun cancelWorkTask(session: CompanionSession, taskId: String): CompanionResult<WorkTaskEnvelope> = error("unused")
    }

    private fun testSession(baseUrl: String = CompanionDefaults.DEFAULT_COMPANION_URL) = CompanionSession(
        baseUrl = baseUrl,
        deviceId = "and_device_1",
        deviceName = "Pixel QA",
        keyId = "key_1",
        sessionKeyBytes = ByteArray(32) { 7 },
        encryptedTransport = CompanionDefaults.ENCRYPTED_TRANSPORT_ID,
        pairedAtEpochSec = 1_700_000_000L
    )

    private fun createViewModel(
        client: CompanionClient,
        sessionStore: InMemoryCompanionSessionStore = InMemoryCompanionSessionStore(),
        pollConfig: CompanionPollConfig = CompanionPollConfig(
            approvalPollIntervalMs = 20L,
            maxApprovalPollAttempts = 5,
            workTaskPollIntervalMs = 10L,
            maxWorkTaskPollAttempts = 10
        )
    ): ChatViewModel = ChatViewModel(
        conversationStore = FakeConversationStore(),
        configStore = FakeConfigStore(RelayConfig(route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT)),
        credentialStore = InMemoryRelayCredentialStore(),
        backend = unusedBackend,
        companionClient = client,
        companionSessionStore = sessionStore,
        companionPollConfig = pollConfig
    ) { 10_000L }

    @Test
    fun pairingAndHealthSyncUpdatesWorkModeAndPersistsSession() = runTest(testDispatcher) {
        var workEnabledResponse = false
        val sessionStore = InMemoryCompanionSessionStore()
        val client = object : StubCompanionClient() {
            override suspend fun checkHealth(baseUrl: String) = CompanionResult.Success(
                CompanionHealthSnapshot(
                    service = "hydrogen-windows-work-companion",
                    version = "0.3.0",
                    status = "paired",
                    workEnabled = workEnabledResponse,
                    allowlistRootCount = if (workEnabledResponse) 1 else 0,
                    capabilities = CompanionCapabilities(
                        health = true,
                        devicePairing = true,
                        encryptedTransport = true,
                        explicitApprovals = true,
                        pathAllowlist = true,
                        remoteFileRead = workEnabledResponse,
                        remoteTaskManagement = workEnabledResponse
                    )
                )
            )

            override suspend fun pairDevice(baseUrl: String, deviceName: String, pairingId: String, pairingCode: String) =
                CompanionResult.Success(testSession(baseUrl))
        }

        val vm = createViewModel(client, sessionStore)
        advanceUntilIdle()
        assertEquals(VerificationState.UNAVAILABLE, vm.uiState.value.readiness.workMode)
        assertFalse(vm.uiState.value.companion.isPaired)

        vm.pairWithCompanion("http://10.0.2.2:18812/", "Pixel QA", "pair_1", "123456-abcd")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.companion.isPaired)
        assertNotNull(sessionStore.loadSession())
        assertEquals(VerificationState.FAIL, vm.uiState.value.readiness.workMode)

        workEnabledResponse = true
        vm.checkCompanionHealth("http://10.0.2.2:18812", "Pixel QA")
        advanceUntilIdle()
        assertEquals(VerificationState.PASS, vm.uiState.value.readiness.workMode)
    }

    @Test
    fun approvalAndWorkTaskExecutionPopulatesDirectoryPreviewAndConsumesApproval() = runTest(testDispatcher) {
        val sessionStore = InMemoryCompanionSessionStore().apply { saveSession(testSession()) }
        val client = object : StubCompanionClient() {
            override suspend fun requestApproval(
                session: CompanionSession,
                actionType: WorkActionType,
                targetPath: String,
                summary: String,
                idempotencyKey: String
            ) = CompanionResult.Success(
                CompanionApproval("apr_42", actionType.wireName, targetPath, "PENDING", 100L, 400L)
            )

            override suspend fun getApprovalStatus(session: CompanionSession, approvalId: String) =
                CompanionResult.Success(
                    CompanionApproval(approvalId, WorkActionType.LIST_DIRECTORY.wireName, "apps", "APPROVED", 100L, 400L)
                )

            override suspend fun startWorkTask(
                session: CompanionSession,
                approvalId: String,
                actionType: WorkActionType,
                targetPath: String,
                idempotencyKey: String,
                maxBytes: Int?,
                offsetBytes: Int?,
                maxEntries: Int?
            ) = CompanionResult.Success(
                WorkTaskEnvelope(
                    task = WorkTask(
                        taskId = "tsk_99",
                        approvalId = approvalId,
                        actionType = actionType.wireName,
                        targetName = targetPath,
                        status = "COMPLETED",
                        progressPercent = 100,
                        result = WorkTaskResult(
                            actionType = actionType.wireName,
                            directoryName = "apps",
                            entryCount = 1,
                            entries = listOf(WorkDirectoryEntry(name = "android", kind = "dir"))
                        )
                    )
                )
            )
        }

        val vm = createViewModel(client, sessionStore)
        advanceUntilIdle()
        assertEquals(VerificationState.UNKNOWN, vm.uiState.value.readiness.workMode)

        vm.requestCompanionApproval(WorkActionType.LIST_DIRECTORY, "apps", "Inspect apps")
        advanceUntilIdle()
        assertEquals("PENDING", vm.uiState.value.companion.activeApproval?.status)

        vm.refreshCompanionApprovalStatus()
        advanceUntilIdle()
        assertEquals("APPROVED", vm.uiState.value.companion.activeApproval?.status)

        vm.startCompanionWorkTask()
        advanceUntilIdle()
        assertEquals("CONSUMED", vm.uiState.value.companion.activeApproval?.status)
        assertEquals("COMPLETED", vm.uiState.value.companion.activeTask?.status)
        assertEquals("android", vm.uiState.value.companion.activeTask?.result?.entries?.firstOrNull()?.name)
    }

    @Test
    fun unauthorized401FromWorkEndpointClearsRevokedLocalSessionAndResetsWorkMode() = runTest(testDispatcher) {
        val sessionStore = InMemoryCompanionSessionStore().apply { saveSession(testSession()) }
        val client = object : StubCompanionClient() {
            override suspend fun checkHealth(baseUrl: String) =
                CompanionResult.Success(CompanionHealthSnapshot(workEnabled = true, allowlistRootCount = 1))

            override suspend fun requestApproval(
                session: CompanionSession,
                actionType: WorkActionType,
                targetPath: String,
                summary: String,
                idempotencyKey: String
            ) = CompanionResult.Failure("device_revoked", "Device pairing was revoked on PC.", httpStatus = 401)
        }

        val vm = createViewModel(client, sessionStore)
        advanceUntilIdle()
        vm.checkCompanionHealth("http://10.0.2.2:18812", "Pixel QA")
        advanceUntilIdle()
        assertEquals(VerificationState.PASS, vm.uiState.value.readiness.workMode)

        vm.requestCompanionApproval(WorkActionType.LIST_DIRECTORY, "apps", "List")
        advanceUntilIdle()

        assertNull(vm.uiState.value.companion.session)
        assertNull(sessionStore.loadSession())
        assertFalse(vm.uiState.value.companion.isPaired)
        assertEquals(VerificationState.UNAVAILABLE, vm.uiState.value.readiness.workMode)
        assertTrue(vm.uiState.value.companion.errorMessage?.contains("revoked", ignoreCase = true) == true)
    }

    @Test
    fun revokeCompanionPairingClearsSessionAndWorkReadiness() = runTest(testDispatcher) {
        val sessionStore = InMemoryCompanionSessionStore().apply { saveSession(testSession()) }
        val client = object : StubCompanionClient() {
            override suspend fun checkHealth(baseUrl: String) =
                CompanionResult.Success(CompanionHealthSnapshot(status = "unpaired", workEnabled = false))

            override suspend fun revokePairing(session: CompanionSession) =
                CompanionResult.Success(RevokeResponse(revoked = true, deviceId = session.deviceId))
        }

        val vm = createViewModel(client, sessionStore)
        advanceUntilIdle()
        vm.revokeCompanionPairing()
        advanceUntilIdle()

        assertNull(vm.uiState.value.companion.session)
        assertNull(sessionStore.loadSession())
        assertEquals(VerificationState.UNAVAILABLE, vm.uiState.value.readiness.workMode)
        assertEquals("Companion pairing revoked.", vm.uiState.value.companion.statusMessage)
    }

    @Test
    fun companionPathHelperJoinsParentsAndFormatsDraftSafely() {
        assertEquals("README.md", CompanionPathHelper.joinPath(".", "README.md"))
        assertEquals("apps/android", CompanionPathHelper.joinPath("apps/", "android"))
        assertEquals("C:\\repo\\README.md", CompanionPathHelper.joinPath("C:\\repo", "README.md"))
        assertEquals(".", CompanionPathHelper.parentPath("apps"))
        assertEquals("apps", CompanionPathHelper.parentPath("apps/android"))
        assertEquals("C:\\", CompanionPathHelper.parentPath("C:\\repo"))
        assertNull(CompanionPathHelper.parentPath("."))

        val binaryTask = WorkTask(
            taskId = "tsk_bin",
            actionType = WorkActionType.READ_FILE_PREVIEW.wireName,
            targetName = "logo.png",
            status = "COMPLETED",
            result = WorkTaskResult(
                actionType = WorkActionType.READ_FILE_PREVIEW.wireName,
                fileName = "logo.png",
                isBinary = true,
                encoding = "binary-omitted"
            )
        )
        assertNull(CompanionPathHelper.formatWorkResultForDraft(binaryTask))
    }

    @Test
    fun stagingNextFileChunkPassesOffsetBytesAndClearsAfterCompletion() = runTest(testDispatcher) {
        val sessionStore = InMemoryCompanionSessionStore().apply { saveSession(testSession()) }
        var capturedOffset: Int? = null
        val client = object : StubCompanionClient() {
            override suspend fun requestApproval(
                session: CompanionSession,
                actionType: WorkActionType,
                targetPath: String,
                summary: String,
                idempotencyKey: String
            ) = CompanionResult.Success(
                CompanionApproval("apr_chunk", actionType.wireName, targetPath, "APPROVED", 100L, 400L)
            )

            override suspend fun startWorkTask(
                session: CompanionSession,
                approvalId: String,
                actionType: WorkActionType,
                targetPath: String,
                idempotencyKey: String,
                maxBytes: Int?,
                offsetBytes: Int?,
                maxEntries: Int?
            ): CompanionResult<WorkTaskEnvelope> {
                capturedOffset = offsetBytes
                return CompanionResult.Success(
                    WorkTaskEnvelope(
                        task = WorkTask(
                            taskId = "tsk_chunk",
                            approvalId = approvalId,
                            actionType = actionType.wireName,
                            targetName = targetPath,
                            status = "COMPLETED",
                            progressPercent = 100,
                            result = WorkTaskResult(
                                actionType = actionType.wireName,
                                fileName = "README.md",
                                offsetBytes = offsetBytes ?: 0,
                                bytesRead = 128,
                                totalSizeBytes = 8320,
                                truncated = false,
                                isBinary = false,
                                encoding = "utf-8",
                                previewText = "second chunk"
                            )
                        )
                    )
                )
            }
        }

        val vm = createViewModel(client, sessionStore)
        advanceUntilIdle()
        vm.stageCompanionWorkTarget(WorkActionType.READ_FILE_PREVIEW, "README.md", offsetBytes = 8192)
        assertEquals(8192, vm.uiState.value.companion.pendingWorkOffsetBytes)

        vm.requestCompanionApproval(WorkActionType.READ_FILE_PREVIEW, "README.md", "Next chunk")
        advanceUntilIdle()
        assertEquals(8192, vm.uiState.value.companion.pendingWorkOffsetBytes)

        vm.startCompanionWorkTask()
        advanceUntilIdle()
        assertEquals(8192, capturedOffset)
        assertNull(vm.uiState.value.companion.pendingWorkOffsetBytes)
        assertTrue(vm.insertCompanionResultIntoDraft())
        assertTrue(vm.uiState.value.draftText.contains("second chunk"))
        assertTrue(vm.uiState.value.draftText.contains("bytes 8192..8320 of 8320 B"))

        vm.clearCompanionWorkState()
        assertNull(vm.uiState.value.companion.activeApproval)
        assertNull(vm.uiState.value.companion.activeTask)
        assertNotNull(vm.uiState.value.companion.session)
    }

    @Test
    fun readFilePreviewAutoWaitsApprovalPollsRunningTaskAndFormatsSafePreview() = runTest(testDispatcher) {
        val sessionStore = InMemoryCompanionSessionStore().apply { saveSession(testSession()) }
        val targetFile = "C:\\Users\\owner\\Documents\\Hydrogen-Work-Sandbox\\approved-preview-example.txt"
        var approvalPolls = 0
        var taskPolls = 0
        val client = object : StubCompanionClient() {
            override suspend fun requestApproval(
                session: CompanionSession,
                actionType: WorkActionType,
                targetPath: String,
                summary: String,
                idempotencyKey: String
            ) = CompanionResult.Success(
                CompanionApproval("apr_read_1", actionType.wireName, "approved-preview-example.txt", "PENDING", 100L, 400L)
            )

            override suspend fun getApprovalStatus(session: CompanionSession, approvalId: String): CompanionResult<CompanionApproval> {
                approvalPolls += 1
                val status = if (approvalPolls >= 2) "APPROVED" else "PENDING"
                return CompanionResult.Success(
                    CompanionApproval(approvalId, WorkActionType.READ_FILE_PREVIEW.wireName, "approved-preview-example.txt", status, 100L, 400L)
                )
            }

            override suspend fun startWorkTask(
                session: CompanionSession,
                approvalId: String,
                actionType: WorkActionType,
                targetPath: String,
                idempotencyKey: String,
                maxBytes: Int?,
                offsetBytes: Int?,
                maxEntries: Int?
            ) = CompanionResult.Success(
                WorkTaskEnvelope(
                    task = WorkTask("tsk_read_1", approvalId, actionType.wireName, "approved-preview-example.txt", "QUEUED", 0)
                )
            )

            override suspend fun getWorkTaskStatus(session: CompanionSession, taskId: String): CompanionResult<WorkTaskEnvelope> {
                taskPolls += 1
                val task = if (taskPolls == 1) {
                    WorkTask(taskId, "apr_read_1", WorkActionType.READ_FILE_PREVIEW.wireName, "approved-preview-example.txt", "RUNNING", 50)
                } else {
                    WorkTask(
                        taskId = taskId,
                        approvalId = "apr_read_1",
                        actionType = WorkActionType.READ_FILE_PREVIEW.wireName,
                        targetName = "approved-preview-example.txt",
                        status = "COMPLETED",
                        progressPercent = 100,
                        result = WorkTaskResult(
                            actionType = WorkActionType.READ_FILE_PREVIEW.wireName,
                            fileName = "approved-preview-example.txt",
                            offsetBytes = 0,
                            bytesRead = 69,
                            totalSizeBytes = 69,
                            truncated = false,
                            isBinary = false,
                            encoding = "utf-8",
                            sha256 = "1234567890abcdef1234567890abcdef",
                            previewText = "Hydrogen sandbox read-only preview"
                        )
                    )
                }
                return CompanionResult.Success(WorkTaskEnvelope(task = task))
            }
        }

        val vm = createViewModel(client, sessionStore)
        advanceUntilIdle()
        vm.requestCompanionApproval(WorkActionType.READ_FILE_PREVIEW, targetFile, "Preview sandbox file", autoWait = true)
        advanceUntilIdle()

        assertEquals(2, approvalPolls)
        assertFalse(vm.uiState.value.companion.isPollingApproval)
        assertEquals("APPROVED", vm.uiState.value.companion.activeApproval?.status)

        vm.startCompanionWorkTask()
        advanceUntilIdle()

        assertEquals(2, taskPolls)
        assertFalse(vm.uiState.value.companion.isRunningWorkTask)
        assertEquals("CONSUMED", vm.uiState.value.companion.activeApproval?.status)
        assertEquals("COMPLETED", vm.uiState.value.companion.activeTask?.status)
        assertEquals("Hydrogen sandbox read-only preview", vm.uiState.value.companion.activeTask?.result?.previewText)
        assertTrue(vm.insertCompanionResultIntoDraft())
        assertTrue(vm.uiState.value.draftText.contains("approved-preview-example.txt"))
        assertTrue(vm.uiState.value.draftText.contains("Hydrogen sandbox read-only preview"))
    }

    @Test
    fun deniedApprovalStopsPollingAndExpiredStartUpdatesApprovalState() = runTest(testDispatcher) {
        val sessionStore = InMemoryCompanionSessionStore().apply { saveSession(testSession()) }
        var startCalls = 0
        val client = object : StubCompanionClient() {
            override suspend fun requestApproval(
                session: CompanionSession,
                actionType: WorkActionType,
                targetPath: String,
                summary: String,
                idempotencyKey: String
            ) = CompanionResult.Success(
                CompanionApproval("apr_deny", actionType.wireName, "approved-preview-example.txt", "PENDING", 100L, 400L)
            )

            override suspend fun getApprovalStatus(session: CompanionSession, approvalId: String) =
                CompanionResult.Success(
                    CompanionApproval(approvalId, WorkActionType.READ_FILE_PREVIEW.wireName, "approved-preview-example.txt", "DENIED", 100L, 400L)
                )

            override suspend fun startWorkTask(
                session: CompanionSession,
                approvalId: String,
                actionType: WorkActionType,
                targetPath: String,
                idempotencyKey: String,
                maxBytes: Int?,
                offsetBytes: Int?,
                maxEntries: Int?
            ): CompanionResult<WorkTaskEnvelope> {
                startCalls += 1
                return CompanionResult.Failure("approval_expired", "Approval has expired.", httpStatus = 403)
            }
        }

        val vm = createViewModel(client, sessionStore)
        advanceUntilIdle()
        vm.requestCompanionApproval(WorkActionType.READ_FILE_PREVIEW, "C:\\sandbox\\approved-preview-example.txt", "Deny test", autoWait = true)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.companion.isPollingApproval)
        assertEquals("DENIED", vm.uiState.value.companion.activeApproval?.status)
        assertTrue(vm.uiState.value.companion.errorMessage?.contains("DENIED") == true)

        vm.startCompanionWorkTask()
        advanceUntilIdle()
        assertEquals(0, startCalls)

        val clientExpired = object : StubCompanionClient() {
            override suspend fun requestApproval(
                session: CompanionSession,
                actionType: WorkActionType,
                targetPath: String,
                summary: String,
                idempotencyKey: String
            ) = CompanionResult.Success(
                CompanionApproval("apr_exp", actionType.wireName, "approved-preview-example.txt", "APPROVED", 100L, 400L)
            )

            override suspend fun startWorkTask(
                session: CompanionSession,
                approvalId: String,
                actionType: WorkActionType,
                targetPath: String,
                idempotencyKey: String,
                maxBytes: Int?,
                offsetBytes: Int?,
                maxEntries: Int?
            ) = CompanionResult.Failure("approval_expired", "Approval has expired.", httpStatus = 403)
        }
        val vmExp = createViewModel(clientExpired, sessionStore)
        advanceUntilIdle()
        vmExp.requestCompanionApproval(WorkActionType.READ_FILE_PREVIEW, "C:\\sandbox\\approved-preview-example.txt", "Exp test")
        advanceUntilIdle()
        vmExp.startCompanionWorkTask()
        advanceUntilIdle()
        assertEquals("EXPIRED", vmExp.uiState.value.companion.activeApproval?.status)
        assertTrue(vmExp.uiState.value.companion.errorMessage?.contains("expired", ignoreCase = true) == true)
    }

    @Test
    fun cancellingActiveWorkTaskStopsPollingAndTransitionsToCancelled() = runTest(testDispatcher) {
        val sessionStore = InMemoryCompanionSessionStore().apply { saveSession(testSession()) }
        var cancelCalled = false
        val client = object : StubCompanionClient() {
            override suspend fun requestApproval(
                session: CompanionSession,
                actionType: WorkActionType,
                targetPath: String,
                summary: String,
                idempotencyKey: String
            ) = CompanionResult.Success(
                CompanionApproval("apr_cancel", actionType.wireName, "approved-preview-example.txt", "APPROVED", 100L, 400L)
            )

            override suspend fun startWorkTask(
                session: CompanionSession,
                approvalId: String,
                actionType: WorkActionType,
                targetPath: String,
                idempotencyKey: String,
                maxBytes: Int?,
                offsetBytes: Int?,
                maxEntries: Int?
            ) = CompanionResult.Success(
                WorkTaskEnvelope(WorkTask("tsk_cancel", approvalId, actionType.wireName, "approved-preview-example.txt", "QUEUED", 0))
            )

            override suspend fun getWorkTaskStatus(session: CompanionSession, taskId: String) = CompanionResult.Success(
                WorkTaskEnvelope(WorkTask(taskId, "apr_cancel", WorkActionType.READ_FILE_PREVIEW.wireName, "approved-preview-example.txt", "RUNNING", 25))
            )

            override suspend fun cancelWorkTask(session: CompanionSession, taskId: String): CompanionResult<WorkTaskEnvelope> {
                cancelCalled = true
                return CompanionResult.Success(
                    WorkTaskEnvelope(WorkTask(taskId, "apr_cancel", WorkActionType.READ_FILE_PREVIEW.wireName, "approved-preview-example.txt", "CANCELLED", 25))
                )
            }
        }

        val vm = createViewModel(client, sessionStore)
        advanceUntilIdle()
        vm.requestCompanionApproval(WorkActionType.READ_FILE_PREVIEW, "C:\\sandbox\\approved-preview-example.txt", "Cancel test")
        advanceUntilIdle()

        vm.startCompanionWorkTask()
        testScheduler.advanceTimeBy(15L)
        testScheduler.runCurrent()
        assertTrue(vm.uiState.value.companion.isRunningWorkTask)
        assertEquals("RUNNING", vm.uiState.value.companion.activeTask?.status)

        vm.cancelCompanionWorkTask()
        advanceUntilIdle()

        assertTrue(cancelCalled)
        assertFalse(vm.uiState.value.companion.isRunningWorkTask)
        assertFalse(vm.uiState.value.companion.isCancellingWorkTask)
        assertEquals("CANCELLED", vm.uiState.value.companion.activeTask?.status)
    }

    @Test
    fun revokingPairingWhileWaitingForApprovalCancelsPollingAndClearsSession() = runTest(testDispatcher) {
        val sessionStore = InMemoryCompanionSessionStore().apply { saveSession(testSession()) }
        var pollCount = 0
        val client = object : StubCompanionClient() {
            override suspend fun checkHealth(baseUrl: String) =
                CompanionResult.Success(CompanionHealthSnapshot(status = "unpaired", workEnabled = false))

            override suspend fun requestApproval(
                session: CompanionSession,
                actionType: WorkActionType,
                targetPath: String,
                summary: String,
                idempotencyKey: String
            ) = CompanionResult.Success(
                CompanionApproval("apr_wait", actionType.wireName, "approved-preview-example.txt", "PENDING", 100L, 400L)
            )

            override suspend fun getApprovalStatus(session: CompanionSession, approvalId: String): CompanionResult<CompanionApproval> {
                pollCount += 1
                return CompanionResult.Success(
                    CompanionApproval(approvalId, WorkActionType.READ_FILE_PREVIEW.wireName, "approved-preview-example.txt", "PENDING", 100L, 400L)
                )
            }

            override suspend fun revokePairing(session: CompanionSession) =
                CompanionResult.Success(RevokeResponse(revoked = true, deviceId = session.deviceId))
        }

        val vm = createViewModel(client, sessionStore)
        advanceUntilIdle()
        vm.requestCompanionApproval(WorkActionType.READ_FILE_PREVIEW, "C:\\sandbox\\approved-preview-example.txt", "Wait then revoke", autoWait = true)
        testScheduler.advanceTimeBy(25L)
        testScheduler.runCurrent()
        assertTrue(vm.uiState.value.companion.isPollingApproval)

        vm.revokeCompanionPairing()
        advanceUntilIdle()

        val pollsAtRevoke = pollCount
        testScheduler.advanceTimeBy(200L)
        testScheduler.runCurrent()
        assertEquals(pollsAtRevoke, pollCount)
        assertFalse(vm.uiState.value.companion.isPollingApproval)
        assertNull(vm.uiState.value.companion.session)
        assertEquals(VerificationState.UNAVAILABLE, vm.uiState.value.readiness.workMode)
    }

    @Test
    fun revokeWhileApprovalRequestInFlightDropsLateApprovalInsteadOfWaitingForever() = runTest(testDispatcher) {
        // Revoke Pairing is gated only by pairingBusy, so it stays enabled during Request Approval. Cover the
        // approval reply landing after the revoke completed (500/0) and while the revoke is still in flight (100/150).
        for ((approvalReplyDelayMs, revokeReplyDelayMs) in listOf(500L to 0L, 100L to 150L)) {
            val sessionStore = InMemoryCompanionSessionStore().apply { saveSession(testSession()) }
            var statusPolls = 0
            val client = object : StubCompanionClient() {
                override suspend fun checkHealth(baseUrl: String) =
                    CompanionResult.Success(CompanionHealthSnapshot(status = "unpaired", workEnabled = false))

                override suspend fun requestApproval(
                    session: CompanionSession,
                    actionType: WorkActionType,
                    targetPath: String,
                    summary: String,
                    idempotencyKey: String
                ): CompanionResult<CompanionApproval> {
                    delay(approvalReplyDelayMs)
                    return CompanionResult.Success(
                        CompanionApproval("apr_late", actionType.wireName, "approved-preview-example.txt", "PENDING", 100L, 400L)
                    )
                }

                override suspend fun getApprovalStatus(session: CompanionSession, approvalId: String): CompanionResult<CompanionApproval> {
                    statusPolls += 1
                    return CompanionResult.Success(
                        CompanionApproval(approvalId, WorkActionType.READ_FILE_PREVIEW.wireName, "approved-preview-example.txt", "PENDING", 100L, 400L)
                    )
                }

                override suspend fun revokePairing(session: CompanionSession): CompanionResult<RevokeResponse> {
                    delay(revokeReplyDelayMs)
                    return CompanionResult.Success(RevokeResponse(revoked = true, deviceId = session.deviceId))
                }
            }

            val vm = createViewModel(client, sessionStore)
            advanceUntilIdle()
            vm.requestCompanionApproval(
                WorkActionType.READ_FILE_PREVIEW,
                "C:\\sandbox\\approved-preview-example.txt",
                "Revoke before the approval reply",
                autoWait = true
            )
            testScheduler.runCurrent()
            assertTrue(vm.uiState.value.companion.isRequestingApproval)

            vm.revokeCompanionPairing()
            advanceUntilIdle()

            val companion = vm.uiState.value.companion
            assertNull(companion.session)
            assertNull(companion.activeApproval)
            assertFalse(companion.isRequestingApproval)
            assertFalse(companion.isPollingApproval)
            assertFalse(companion.isWorkBusy)
            assertEquals("Companion pairing revoked.", companion.statusMessage)
            assertEquals(0, statusPolls)
            assertEquals(VerificationState.UNAVAILABLE, vm.uiState.value.readiness.workMode)
        }
    }

    @Test
    fun healthUnpairedWithStaleSessionSurfacesFailClosedWarning() = runTest(testDispatcher) {
        val sessionStore = InMemoryCompanionSessionStore().apply { saveSession(testSession()) }
        val client = object : StubCompanionClient() {
            override suspend fun checkHealth(baseUrl: String) = CompanionResult.Success(
                CompanionHealthSnapshot(
                    service = "hydrogen-windows-work-companion",
                    version = "0.3.0",
                    status = "unpaired",
                    workEnabled = false,
                    pairedDeviceCount = 0,
                    allowlistRootCount = 1
                )
            )
        }

        val vm = createViewModel(client, sessionStore)
        advanceUntilIdle()
        vm.checkCompanionHealth("http://10.0.2.2:18812", "Pixel QA")
        advanceUntilIdle()

        assertEquals(VerificationState.FAIL, vm.uiState.value.readiness.workMode)
        assertNull(vm.uiState.value.companion.statusMessage)
        assertTrue(vm.uiState.value.companion.errorMessage?.contains("unpaired", ignoreCase = true) == true)
        assertNotNull(vm.uiState.value.companion.session)
    }

    @Test
    fun startWorkTaskDenied403MapsApprovalStatusToDeniedFailClosed() = runTest(testDispatcher) {
        val sessionStore = InMemoryCompanionSessionStore().apply { saveSession(testSession()) }
        val client = object : StubCompanionClient() {
            override suspend fun requestApproval(
                session: CompanionSession,
                actionType: WorkActionType,
                targetPath: String,
                summary: String,
                idempotencyKey: String
            ) = CompanionResult.Success(
                CompanionApproval("apr_den_start", actionType.wireName, "approved-preview-example.txt", "APPROVED", 100L, 400L)
            )

            override suspend fun startWorkTask(
                session: CompanionSession,
                approvalId: String,
                actionType: WorkActionType,
                targetPath: String,
                idempotencyKey: String,
                maxBytes: Int?,
                offsetBytes: Int?,
                maxEntries: Int?
            ) = CompanionResult.Failure(
                code = "approval_not_granted",
                userMessage = "Approval state is DENIED.",
                httpStatus = 403
            )
        }

        val vm = createViewModel(client, sessionStore)
        advanceUntilIdle()
        vm.requestCompanionApproval(WorkActionType.READ_FILE_PREVIEW, "C:\\sandbox\\approved-preview-example.txt", "Denied test")
        advanceUntilIdle()
        vm.startCompanionWorkTask()
        advanceUntilIdle()

        assertEquals("DENIED", vm.uiState.value.companion.activeApproval?.status)
        assertFalse(vm.uiState.value.companion.isRunningWorkTask)
        assertTrue(vm.uiState.value.companion.errorMessage?.contains("DENIED") == true)
    }

    @Test
    fun replayDetected401OfflineAnd404PollingFailClosedCleanly() = runTest(testDispatcher) {
        val sessionStoreReplay = InMemoryCompanionSessionStore().apply { saveSession(testSession()) }
        val replayClient = object : StubCompanionClient() {
            override suspend fun requestApproval(
                session: CompanionSession,
                actionType: WorkActionType,
                targetPath: String,
                summary: String,
                idempotencyKey: String
            ) = CompanionResult.Success(
                CompanionApproval("apr_rep", actionType.wireName, "approved-preview-example.txt", "PENDING", 100L, 400L)
            )

            override suspend fun getApprovalStatus(session: CompanionSession, approvalId: String) =
                CompanionResult.Failure("replay_detected", "Duplicate request nonce rejected.", httpStatus = 401)
        }

        val vmReplay = createViewModel(replayClient, sessionStoreReplay)
        advanceUntilIdle()
        vmReplay.requestCompanionApproval(WorkActionType.READ_FILE_PREVIEW, "C:\\sandbox\\approved-preview-example.txt", "Replay poll", autoWait = true)
        advanceUntilIdle()

        assertNull(vmReplay.uiState.value.companion.session)
        assertNull(sessionStoreReplay.loadSession())
        assertFalse(vmReplay.uiState.value.companion.isPollingApproval)
        assertEquals(VerificationState.UNAVAILABLE, vmReplay.uiState.value.readiness.workMode)
        assertTrue(vmReplay.uiState.value.companion.errorMessage?.contains("revoked", ignoreCase = true) == true)

        val sessionStoreOffline = InMemoryCompanionSessionStore().apply { saveSession(testSession()) }
        val offlineClient = object : StubCompanionClient() {
            override suspend fun requestApproval(
                session: CompanionSession,
                actionType: WorkActionType,
                targetPath: String,
                summary: String,
                idempotencyKey: String
            ) = CompanionResult.Success(
                CompanionApproval("apr_off", actionType.wireName, "approved-preview-example.txt", "PENDING", 100L, 400L)
            )

            override suspend fun getApprovalStatus(session: CompanionSession, approvalId: String) =
                CompanionResult.Failure("network_error", "Failed to connect to /10.0.2.2:18812")
        }

        val vmOffline = createViewModel(offlineClient, sessionStoreOffline)
        advanceUntilIdle()
        vmOffline.requestCompanionApproval(WorkActionType.READ_FILE_PREVIEW, "C:\\sandbox\\approved-preview-example.txt", "Offline poll", autoWait = true)
        advanceUntilIdle()

        assertNotNull(vmOffline.uiState.value.companion.session)
        assertFalse(vmOffline.uiState.value.companion.isPollingApproval)
        assertTrue(vmOffline.uiState.value.companion.errorMessage?.contains("10.0.2.2") == true)

        val sessionStore404 = InMemoryCompanionSessionStore().apply { saveSession(testSession()) }
        val notFoundClient = object : StubCompanionClient() {
            override suspend fun requestApproval(
                session: CompanionSession,
                actionType: WorkActionType,
                targetPath: String,
                summary: String,
                idempotencyKey: String
            ) = CompanionResult.Success(
                CompanionApproval("apr_404", actionType.wireName, "approved-preview-example.txt", "PENDING", 100L, 400L)
            )

            override suspend fun getApprovalStatus(session: CompanionSession, approvalId: String) =
                CompanionResult.Failure("approval_not_found", "Approval request not found.", httpStatus = 404)
        }

        val vm404 = createViewModel(notFoundClient, sessionStore404)
        advanceUntilIdle()
        vm404.requestCompanionApproval(WorkActionType.READ_FILE_PREVIEW, "C:\\sandbox\\approved-preview-example.txt", "404 poll", autoWait = true)
        advanceUntilIdle()

        assertNotNull(vm404.uiState.value.companion.session)
        assertNull(vm404.uiState.value.companion.activeApproval)
        assertFalse(vm404.uiState.value.companion.isPollingApproval)
        assertTrue(vm404.uiState.value.companion.errorMessage?.contains("not found", ignoreCase = true) == true)
    }

    @Test
    fun pairingInputParserNormalizesDashboardAndCliStringsAndViewModelUsesNormalizedValues() = runTest(testDispatcher) {
        val fromCli = CompanionPairingInputParser.normalize(
            "pairingId=pair_abc123 pairingCode=482910-deadbeef",
            ""
        )
        assertEquals("pair_abc123", fromCli.pairingId)
        assertEquals("482910-deadbeef", fromCli.pairingCode)

        val fromDashboard = CompanionPairingInputParser.normalize(
            "  Pairing id: pair_dash_99 ",
            " Pairing code: 123456-cafe "
        )
        assertEquals("pair_dash_99", fromDashboard.pairingId)
        assertEquals("123456-cafe", fromDashboard.pairingCode)

        val fromMultiLineDashboard = CompanionPairingInputParser.normalize(
            "Pairing id: pair_multi_77\nPairing code: 654321-beef",
            ""
        )
        assertEquals("pair_multi_77", fromMultiLineDashboard.pairingId)
        assertEquals("654321-beef", fromMultiLineDashboard.pairingCode)

        var capturedId: String? = null
        var capturedCode: String? = null
        val client = object : StubCompanionClient() {
            override suspend fun checkHealth(baseUrl: String) = CompanionResult.Success(
                CompanionHealthSnapshot(
                    service = "hydrogen-windows-work-companion",
                    version = "0.3.0",
                    status = "paired",
                    workEnabled = true,
                    pairedDeviceCount = 1,
                    allowlistRootCount = 1,
                    capabilities = CompanionCapabilities(health = true, devicePairing = true, remoteFileRead = true)
                )
            )

            override suspend fun pairDevice(baseUrl: String, deviceName: String, pairingId: String, pairingCode: String): CompanionResult<CompanionSession> {
                capturedId = pairingId
                capturedCode = pairingCode
                return CompanionResult.Success(testSession(baseUrl))
            }
        }

        val vm = createViewModel(client)
        advanceUntilIdle()
        vm.pairWithCompanion(
            baseUrl = CompanionDefaults.DEFAULT_COMPANION_URL,
            deviceName = "Pixel Emulator",
            pairingId = "Pairing id: pair_dash_99",
            pairingCode = " 123456-cafe "
        )
        advanceUntilIdle()

        assertEquals("pair_dash_99", capturedId)
        assertEquals("123456-cafe", capturedCode)
        assertTrue(vm.uiState.value.companion.isPaired)
    }

    @Test
    fun replay401AndAllowlist403AndOfflineWorkErrorsFailClosedWithDistinctReasons() = runTest(testDispatcher) {
        val replayStore = InMemoryCompanionSessionStore().apply { saveSession(testSession()) }
        val replayClient = object : StubCompanionClient() {
            override suspend fun requestApproval(
                session: CompanionSession,
                actionType: WorkActionType,
                targetPath: String,
                summary: String,
                idempotencyKey: String
            ) = CompanionResult.Failure("replay_detected", "Duplicate request nonce rejected.", httpStatus = 401)
        }
        val vmReplay = createViewModel(replayClient, replayStore)
        advanceUntilIdle()
        vmReplay.requestCompanionApproval(WorkActionType.READ_FILE_PREVIEW, "C:\\sandbox\\approved-preview-example.txt", "Replay test")
        advanceUntilIdle()
        assertNull(vmReplay.uiState.value.companion.session)
        assertEquals(VerificationState.UNAVAILABLE, vmReplay.uiState.value.readiness.workMode)
        assertTrue(vmReplay.uiState.value.companion.errorMessage?.contains("replay_detected") == true)

        val allowlistStore = InMemoryCompanionSessionStore().apply { saveSession(testSession()) }
        val allowlistClient = object : StubCompanionClient() {
            override suspend fun requestApproval(
                session: CompanionSession,
                actionType: WorkActionType,
                targetPath: String,
                summary: String,
                idempotencyKey: String
            ) = CompanionResult.Failure("outside_allowlist", "Path is outside authorized directories.", httpStatus = 403)
        }
        val vmAllowlist = createViewModel(allowlistClient, allowlistStore)
        advanceUntilIdle()
        vmAllowlist.requestCompanionApproval(WorkActionType.READ_FILE_PREVIEW, "C:\\Windows\\System32\\drivers\\etc\\hosts", "Blocked")
        advanceUntilIdle()
        assertNotNull(vmAllowlist.uiState.value.companion.session)
        assertNull(vmAllowlist.uiState.value.companion.activeApproval)
        assertTrue(vmAllowlist.uiState.value.companion.errorMessage?.contains("outside authorized directories", ignoreCase = true) == true)

        val offlineStore = InMemoryCompanionSessionStore().apply { saveSession(testSession()) }
        val offlineClient = object : StubCompanionClient() {
            override suspend fun requestApproval(
                session: CompanionSession,
                actionType: WorkActionType,
                targetPath: String,
                summary: String,
                idempotencyKey: String
            ) = CompanionResult.Success(
                CompanionApproval("apr_ok", actionType.wireName, "approved-preview-example.txt", "APPROVED", 100L, 400L)
            )

            override suspend fun startWorkTask(
                session: CompanionSession,
                approvalId: String,
                actionType: WorkActionType,
                targetPath: String,
                idempotencyKey: String,
                maxBytes: Int?,
                offsetBytes: Int?,
                maxEntries: Int?
            ) = CompanionResult.Failure("network_error", "Connection refused to 10.0.2.2:18812")
        }
        val vmOffline = createViewModel(offlineClient, offlineStore)
        advanceUntilIdle()
        vmOffline.requestCompanionApproval(WorkActionType.READ_FILE_PREVIEW, "C:\\sandbox\\approved-preview-example.txt", "Offline start")
        advanceUntilIdle()
        vmOffline.startCompanionWorkTask()
        advanceUntilIdle()
        assertFalse(vmOffline.uiState.value.companion.isRunningWorkTask)
        assertNull(vmOffline.uiState.value.companion.activeTask)
        assertTrue(vmOffline.uiState.value.companion.errorMessage?.contains("Connection refused") == true)
    }

    @Test
    fun stopWaitingCancelsInFlightCheckOnceSoStaleApprovalCannotRebindToNewTarget() = runTest(testDispatcher) {
        val sessionStore = InMemoryCompanionSessionStore().apply { saveSession(testSession()) }
        val oldPath = "C:\\sandbox\\old-preview.txt"
        val newPath = "C:\\sandbox\\new-preview.txt"
        val statusChecks = mutableListOf<String>()
        var startCalls = 0
        val client = object : StubCompanionClient() {
            override suspend fun requestApproval(
                session: CompanionSession,
                actionType: WorkActionType,
                targetPath: String,
                summary: String,
                idempotencyKey: String
            ) = CompanionResult.Success(
                CompanionApproval(
                    if (targetPath == oldPath) "apr_old" else "apr_new",
                    actionType.wireName,
                    targetPath.substringAfterLast('\\'),
                    "PENDING",
                    100L,
                    400L
                )
            )

            // Slow PC link; the owner approved only the OLD request on the Windows dashboard.
            override suspend fun getApprovalStatus(session: CompanionSession, approvalId: String): CompanionResult<CompanionApproval> {
                statusChecks += approvalId
                delay(100L)
                val status = if (approvalId == "apr_old") "APPROVED" else "PENDING"
                return CompanionResult.Success(
                    CompanionApproval(approvalId, WorkActionType.READ_FILE_PREVIEW.wireName, "preview.txt", status, 100L, 400L)
                )
            }

            override suspend fun startWorkTask(
                session: CompanionSession,
                approvalId: String,
                actionType: WorkActionType,
                targetPath: String,
                idempotencyKey: String,
                maxBytes: Int?,
                offsetBytes: Int?,
                maxEntries: Int?
            ): CompanionResult<WorkTaskEnvelope> {
                startCalls += 1
                return CompanionResult.Failure("approval_scope_mismatch", "Approval does not cover this target.", httpStatus = 403)
            }
        }
        val vm = createViewModel(
            client,
            sessionStore,
            pollConfig = CompanionPollConfig(
                approvalPollIntervalMs = 1_000L,
                maxApprovalPollAttempts = 1,
                workTaskPollIntervalMs = 10L,
                maxWorkTaskPollAttempts = 10
            )
        )
        advanceUntilIdle()

        // Old request is PENDING and not auto-waiting (same state as after tapping Stop Waiting).
        vm.requestCompanionApproval(WorkActionType.READ_FILE_PREVIEW, oldPath, "Old preview")
        advanceUntilIdle()
        assertEquals("apr_old", vm.uiState.value.companion.activeApproval?.approvalId)

        // Check Once is in flight, so the approval card offers Stop Waiting.
        vm.refreshCompanionApprovalStatus()
        testScheduler.runCurrent()
        assertTrue(vm.uiState.value.companion.isPollingApproval)
        assertEquals(listOf("apr_old"), statusChecks)

        vm.stopWaitingForCompanionApproval()
        assertFalse(vm.uiState.value.companion.isPollingApproval)

        // Request PC Approval for a different file exactly as the UI does (autoWait = true).
        vm.requestCompanionApproval(WorkActionType.READ_FILE_PREVIEW, newPath, "New preview", autoWait = true)
        testScheduler.runCurrent()
        // Past the stale Check Once response (t=100ms), before the new auto-wait poll (t=1000ms).
        testScheduler.advanceTimeBy(500L)
        testScheduler.runCurrent()

        val companion = vm.uiState.value.companion
        assertEquals("apr_new", companion.activeApproval?.approvalId)
        assertEquals("PENDING", companion.activeApproval?.status)
        assertEquals(newPath, companion.activeApprovalTargetPath)
        assertTrue(companion.isPollingApproval)

        // Run must never pair the old approval with the newly staged target.
        vm.startCompanionWorkTask()
        advanceUntilIdle()
        assertEquals(0, startCalls)
        assertEquals(listOf("apr_old", "apr_new"), statusChecks)
        assertEquals("PENDING", vm.uiState.value.companion.activeApproval?.status)
        assertFalse(vm.uiState.value.companion.isPollingApproval)
    }

    @Test
    fun restagingDifferentPathDetachesApprovedApprovalSoRunCannotReuseItForNewTarget() = runTest(testDispatcher) {
        val sessionStore = InMemoryCompanionSessionStore().apply { saveSession(testSession()) }
        val approvedPath = "C:\\sandbox\\approved-preview.txt"
        val otherPath = "C:\\sandbox\\other-file.txt"
        val startCalls = mutableListOf<Pair<String, String>>()
        val client = object : StubCompanionClient() {
            override suspend fun requestApproval(
                session: CompanionSession,
                actionType: WorkActionType,
                targetPath: String,
                summary: String,
                idempotencyKey: String
            ) = CompanionResult.Success(
                CompanionApproval("apr_chunk", actionType.wireName, targetPath.substringAfterLast('\\'), "PENDING", 100L, 400L)
            )

            // Fake PC: the owner approved only the request for approvedPath on the Windows dashboard.
            override suspend fun getApprovalStatus(session: CompanionSession, approvalId: String): CompanionResult<CompanionApproval> =
                CompanionResult.Success(
                    CompanionApproval(approvalId, WorkActionType.READ_FILE_PREVIEW.wireName, "approved-preview.txt", "APPROVED", 100L, 400L)
                )

            override suspend fun startWorkTask(
                session: CompanionSession,
                approvalId: String,
                actionType: WorkActionType,
                targetPath: String,
                idempotencyKey: String,
                maxBytes: Int?,
                offsetBytes: Int?,
                maxEntries: Int?
            ): CompanionResult<WorkTaskEnvelope> {
                startCalls += (approvalId to targetPath)
                return CompanionResult.Failure("approval_scope_mismatch", "Approval does not cover this target.", httpStatus = 403)
            }
        }
        val vm = createViewModel(client, sessionStore)
        advanceUntilIdle()

        // Stage Next Chunk, then Request PC Approval (UI passes autoWait = true); owner approves on PC.
        vm.stageCompanionWorkTarget(WorkActionType.READ_FILE_PREVIEW, approvedPath, offsetBytes = 4096)
        vm.requestCompanionApproval(WorkActionType.READ_FILE_PREVIEW, approvedPath, "Next chunk", autoWait = true)
        advanceUntilIdle()
        assertEquals("APPROVED", vm.uiState.value.companion.activeApproval?.status)
        assertEquals(4096, vm.uiState.value.companion.pendingWorkOffsetBytes)

        // Reset Offset with the path unchanged: the offset is not part of the owner's approval, so it stays runnable.
        vm.stageCompanionWorkTarget(WorkActionType.READ_FILE_PREVIEW, approvedPath, null)
        assertEquals("APPROVED", vm.uiState.value.companion.activeApproval?.status)
        assertNull(vm.uiState.value.companion.pendingWorkOffsetBytes)

        // Edit the path field to another file, then Reset Offset: the approval for approvedPath must not follow.
        vm.stageCompanionWorkTarget(WorkActionType.READ_FILE_PREVIEW, otherPath, null)
        val companion = vm.uiState.value.companion
        assertNull(companion.activeApproval)
        assertEquals(otherPath, companion.activeApprovalTargetPath)
        assertFalse(companion.isPollingApproval)
        assertTrue(companion.statusMessage.orEmpty().contains("apr_chunk"))

        // Run must never send the approval granted for approvedPath together with otherPath.
        vm.startCompanionWorkTask()
        advanceUntilIdle()
        assertTrue(startCalls.isEmpty())
    }

    @Test
    fun revokeDuringInFlightRefreshTaskCannotResurrectPcFilePreview() = runTest(testDispatcher) {
        val sessionStore = InMemoryCompanionSessionStore().apply { saveSession(testSession()) }
        val statusCalls = mutableListOf<String>()
        var revokeCalls = 0
        val client = object : StubCompanionClient() {
            override suspend fun checkHealth(baseUrl: String) =
                CompanionResult.Success(CompanionHealthSnapshot(status = "unpaired", workEnabled = false))

            override suspend fun requestApproval(
                session: CompanionSession,
                actionType: WorkActionType,
                targetPath: String,
                summary: String,
                idempotencyKey: String
            ) = CompanionResult.Success(
                CompanionApproval("apr_refresh", actionType.wireName, "owner-notes.txt", "APPROVED", 100L, 400L)
            )

            override suspend fun startWorkTask(
                session: CompanionSession,
                approvalId: String,
                actionType: WorkActionType,
                targetPath: String,
                idempotencyKey: String,
                maxBytes: Int?,
                offsetBytes: Int?,
                maxEntries: Int?
            ) = CompanionResult.Success(
                WorkTaskEnvelope(WorkTask("tsk_refresh", approvalId, actionType.wireName, "owner-notes.txt", "QUEUED", 0))
            )

            // Auto-poll sees RUNNING; the manual Refresh Task snapshot is slow and already carries file content.
            override suspend fun getWorkTaskStatus(session: CompanionSession, taskId: String): CompanionResult<WorkTaskEnvelope> {
                statusCalls += taskId
                if (statusCalls.size == 1) {
                    return CompanionResult.Success(
                        WorkTaskEnvelope(WorkTask(taskId, "apr_refresh", WorkActionType.READ_FILE_PREVIEW.wireName, "owner-notes.txt", "RUNNING", 40))
                    )
                }
                delay(100L)
                return CompanionResult.Success(
                    WorkTaskEnvelope(
                        WorkTask(
                            taskId, "apr_refresh", WorkActionType.READ_FILE_PREVIEW.wireName, "owner-notes.txt", "COMPLETED", 100,
                            result = WorkTaskResult(
                                actionType = WorkActionType.READ_FILE_PREVIEW.wireName,
                                fileName = "owner-notes.txt",
                                offsetBytes = 0,
                                bytesRead = 17,
                                totalSizeBytes = 17L,
                                isBinary = false,
                                encoding = "utf-8",
                                previewText = "synthetic preview"
                            )
                        )
                    )
                )
            }

            override suspend fun revokePairing(session: CompanionSession): CompanionResult<RevokeResponse> {
                revokeCalls += 1
                return CompanionResult.Success(RevokeResponse(revoked = true, deviceId = session.deviceId))
            }
        }
        val vm = createViewModel(
            client,
            sessionStore,
            pollConfig = CompanionPollConfig(
                approvalPollIntervalMs = 20L,
                maxApprovalPollAttempts = 5,
                workTaskPollIntervalMs = 10L,
                maxWorkTaskPollAttempts = 1
            )
        )
        advanceUntilIdle()
        vm.requestCompanionApproval(WorkActionType.READ_FILE_PREVIEW, "C:\\sandbox\\owner-notes.txt", "Refresh then revoke")
        advanceUntilIdle()
        vm.startCompanionWorkTask()
        advanceUntilIdle()
        // Auto-poll budget exhausted while RUNNING, so the task card offers Refresh Task.
        assertEquals("RUNNING", vm.uiState.value.companion.activeTask?.status)
        assertFalse(vm.uiState.value.companion.isRunningWorkTask)

        vm.refreshCompanionWorkTaskStatus()
        testScheduler.runCurrent()
        assertTrue(vm.uiState.value.companion.isRunningWorkTask)
        assertEquals(2, statusCalls.size)

        // Revoke while the slow pre-revoke snapshot is still in flight.
        vm.revokeCompanionPairing()
        advanceUntilIdle()

        val companion = vm.uiState.value.companion
        assertEquals(1, revokeCalls)
        assertNull(companion.session)
        // The stale snapshot must not re-render PC file content on the revoked device.
        assertNull(companion.activeTask)
        assertFalse(companion.isRunningWorkTask)
        assertEquals("Companion pairing revoked.", companion.statusMessage)
        assertEquals(2, statusCalls.size)
    }
}