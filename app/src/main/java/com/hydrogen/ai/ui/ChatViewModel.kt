package com.hydrogen.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hydrogen.ai.companion.AskPcProposals
import com.hydrogen.ai.companion.CompanionClient
import com.hydrogen.ai.companion.CompanionConfig
import com.hydrogen.ai.companion.CompanionDefaults
import com.hydrogen.ai.companion.CompanionPairingInputParser
import com.hydrogen.ai.companion.CompanionPathHelper
import com.hydrogen.ai.companion.CompanionResult
import com.hydrogen.ai.companion.CompanionSessionStore
import com.hydrogen.ai.companion.CompanionUrlSafety
import com.hydrogen.ai.companion.HttpCompanionClient
import com.hydrogen.ai.companion.InMemoryCompanionSessionStore
import com.hydrogen.ai.companion.WorkActionType
import com.hydrogen.ai.BuildConfig
import com.hydrogen.ai.data.AppVersionStore
import com.hydrogen.ai.data.CompositeRelayCredentialStore
import com.hydrogen.ai.data.ConversationStore
import com.hydrogen.ai.data.ErrorLogStore
import com.hydrogen.ai.data.InMemoryAppVersionStore
import com.hydrogen.ai.data.InMemoryErrorLogStore
import com.hydrogen.ai.model.ErrorLog
import com.hydrogen.ai.model.WhatsNew
import com.hydrogen.ai.data.RelayConfigStore
import com.hydrogen.ai.data.RelayCredentialStore
import com.hydrogen.ai.data.InMemoryPersonalStore
import com.hydrogen.ai.data.InMemoryProjectStore
import com.hydrogen.ai.data.ProjectStore
import com.hydrogen.ai.model.Project
import com.hydrogen.ai.model.ProjectFile
import com.hydrogen.ai.model.Projects
import com.hydrogen.ai.data.InMemoryUserMemoryStore
import com.hydrogen.ai.data.PersonalStore
import com.hydrogen.ai.data.SavedChatsOnly
import com.hydrogen.ai.data.UserMemoryState
import com.hydrogen.ai.data.UserMemoryStore
import com.hydrogen.ai.data.maskSecretToken
import com.hydrogen.ai.data.cleanBackupTokens
import com.hydrogen.ai.model.OrbitInstructions
import com.hydrogen.ai.model.Personal
import com.hydrogen.ai.model.PersonalProfile
import com.hydrogen.ai.model.ShareIntentParser
import com.hydrogen.ai.model.UserMemory
import com.hydrogen.ai.model.AttachmentManifestItem
import com.hydrogen.ai.model.ChatConnectionPreference
import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.data.ChatBackupCodec
import com.hydrogen.ai.model.Conversation
import com.hydrogen.ai.model.ConversationTitler
import com.hydrogen.ai.model.ConversationListTools
import com.hydrogen.ai.model.ProviderPresets
import com.hydrogen.ai.model.GeminiTurnMetadata
import com.hydrogen.ai.model.HydrogenDefaults
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.MessageStatus
import com.hydrogen.ai.model.ProviderError
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.ProviderReadiness
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import com.hydrogen.ai.model.EditResendPolicy
import com.hydrogen.ai.model.MessageVersions
import com.hydrogen.ai.model.RegeneratePolicy
import com.hydrogen.ai.model.SystemPromptPolicy
import com.hydrogen.ai.model.ToolCallRecord
import com.hydrogen.ai.model.VerificationState
import com.hydrogen.ai.network.ChatBackend
import com.hydrogen.ai.network.ChatDispatchRequest
import com.hydrogen.ai.network.ChatDispatchResult
import com.hydrogen.ai.network.HttpGeminiHydrogenRelayBackend
import com.hydrogen.ai.network.ModelListResult
import com.hydrogen.ai.network.PreflightAssessment
import com.hydrogen.ai.network.PreflightValidator
import com.hydrogen.ai.network.RelayHealthResult
import com.hydrogen.ai.network.StreamDelta
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class CompanionPollConfig(
    val approvalPollIntervalMs: Long = 1_500L,
    val maxApprovalPollAttempts: Int = 120,
    val workTaskPollIntervalMs: Long = 250L,
    val maxWorkTaskPollAttempts: Int = 60
)

/** Shown when sending fails inside the app itself; the exception text goes to technicalDetail, never the headline. */
internal const val CLIENT_FAILURE_MESSAGE = "Something went wrong on this phone. Your message is saved; tap Retry."

/** Failures where another key for the same service can help: limits, credits and a key that is turned down. */
internal val KEY_RELAY_CATEGORIES = setOf(
    ProviderErrorCategory.QUOTA_DAILY_429,
    ProviderErrorCategory.QUOTA_RATE_TPM_RPM_429,
    ProviderErrorCategory.OUT_OF_CREDITS,
    ProviderErrorCategory.UNAUTHORIZED_UPSTREAM_401,
    ProviderErrorCategory.UNAUTHORIZED_RELAY_401
)

class ChatViewModel(
    conversationStore: ConversationStore,
    private val configStore: RelayConfigStore,
    private val credentialStore: RelayCredentialStore,
    private val backend: ChatBackend,
    private val companionClient: CompanionClient = HttpCompanionClient(),
    private val companionSessionStore: CompanionSessionStore = InMemoryCompanionSessionStore(),
    private val onDeleteLocalAttachment: (String) -> Unit = {},
    private val companionPollConfig: CompanionPollConfig = CompanionPollConfig(),
    private val memoryStore: UserMemoryStore = InMemoryUserMemoryStore(),
    private val personalStore: PersonalStore = InMemoryPersonalStore(),
    private val projectStore: ProjectStore = InMemoryProjectStore(),
    private val appVersionStore: AppVersionStore = InMemoryAppVersionStore(),
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    private val errorLogStore: ErrorLogStore = InMemoryErrorLogStore(),
    // Keep the clock last: tests pass it as a trailing lambda.
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    /** Every save goes through this wrapper, so a temporary chat never reaches storage. */
    private val conversationStore: ConversationStore = SavedChatsOnly(conversationStore)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private val memorySaveLock = Mutex()
    private val personalSaveLock = Mutex()
    private val projectSaveLock = Mutex()
    private val errorLogLock = Mutex()
    private var activeSendJob: Job? = null
    private var activeSendTarget: Pair<String, String>? = null
    /** When each streaming reply's first thinking text arrived, for "Thought for N seconds". */
    private val thinkingStartedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var approvalPollJob: Job? = null
    private var workTaskPollJob: Job? = null

    init {
        loadInitialState()
    }

    override fun onCleared() {
        cancelCompanionPollingJobs()
        super.onCleared()
    }

    private fun cancelCompanionPollingJobs() {
        approvalPollJob?.cancel()
        approvalPollJob = null
        workTaskPollJob?.cancel()
        workTaskPollJob = null
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            val savedConfig = configStore.loadConfig()
            (credentialStore as? CompositeRelayCredentialStore)?.persistEnabled = savedConfig.rememberTokenInKeystore
            val companionConfig = companionSessionStore.loadConfig()
            val companionSession = companionSessionStore.loadSession()
            val loaded = conversationStore.loadConversations()
            // The store already kept a copy of anything it couldn't read; saving now writes back a clean file so the
            // next start doesn't keep another copy.
            val loadProblem = conversationStore.lastLoadProblem()
            val now = clock()
            val (reconciled, didReconcile) = reconcileInterruptedConversations(loaded, now)
            // Opening on an archived chat would undo archiving it, so start on the newest chat in the list instead.
            val initialList = if (reconciled.none { !it.archived }) {
                listOf(newBlankConversation(now, savedConfig)) + reconciled
            } else {
                reconciled
            }
            if (loaded.isEmpty() || didReconcile || loadProblem != null) {
                conversationStore.saveConversations(initialList)
            }
            val initialConfig = initialList.first { !it.archived }.preferredConnection?.applyTo(savedConfig) ?: savedConfig
            val token = credentialStore.getToken(initialConfig)
            val hasToken = !token.isNullOrBlank()
            val initialCompanion = CompanionUiState(
                config = companionConfig,
                session = companionSession
            )
            val memory = runCatching { memoryStore.load() }.getOrDefault(UserMemoryState())
            val personal = runCatching { personalStore.load() }.getOrDefault(PersonalProfile())
            val projects = runCatching { projectStore.load() }.getOrDefault(emptyList())
            val errorLog = runCatching { errorLogStore.load() }.getOrDefault(emptyList())
            val hadSavedData = loaded.isNotEmpty() || loadProblem != null || savedConfig.isConfigured ||
                memory.items.isNotEmpty() || !personal.isEmpty || projects.isNotEmpty()
            val lastSeenVersion = runCatching { appVersionStore.lastSeenVersion() }.getOrDefault(currentVersionCode)
            val showWhatsNew = WhatsNew.shouldShow(lastSeenVersion, currentVersionCode, hadSavedData)
            if (!showWhatsNew) recordVersionSeen()
            _uiState.update {
                it.copy(
                    whatsNew = showWhatsNew,
                    notice = loadProblem?.notice ?: it.notice,
                    personal = personal,
                    projects = projects,
                    errorLog = errorLog,
                    isLoading = false,
                    conversations = initialList,
                    activeConversationId = initialList.first { c -> !c.archived }.id,
                    relayConfig = initialConfig,
                    hasToken = hasToken,
                    maskedTokenLabel = maskSecretToken(token),
                    backupKeyLabels = backupLabels(initialConfig),
                    capabilities = initialConfig.capabilities(),
                    readiness = buildBaselineReadiness(initialConfig, hasToken, computeWorkMode(initialCompanion)),
                    companion = initialCompanion,
                    memoryItems = memory.items,
                    memoryEnabled = memory.enabled
                )
            }
        }
    }

    // ---- Memory you control (device-local; sent only inside the chosen provider's system message) ----

    /** Adds a note (newest first; past [UserMemory.MAX_ITEMS] the oldest drops). False when blank or a duplicate. */
    fun addMemory(text: String): Boolean {
        val before = _uiState.value.memoryItems
        val after = UserMemory.add(before, text, UUID.randomUUID().toString(), clock())
        if (after == before) return false
        _uiState.update { it.copy(memoryItems = after) }
        persistMemory()
        return true
    }

    fun updateMemory(id: String, text: String): Boolean {
        val before = _uiState.value.memoryItems
        val after = UserMemory.update(before, id, text)
        if (after == before) return false
        _uiState.update { it.copy(memoryItems = after) }
        persistMemory()
        return true
    }

    fun setMemoryPinned(id: String, pinned: Boolean) {
        val before = _uiState.value.memoryItems
        val after = UserMemory.setPinned(before, id, pinned)
        if (after == before) return
        _uiState.update { it.copy(memoryItems = after) }
        persistMemory()
    }

    fun removeMemory(id: String) {
        _uiState.update { it.copy(memoryItems = UserMemory.remove(it.memoryItems, id)) }
        persistMemory()
    }

    fun clearMemory() {
        _uiState.update { it.copy(memoryItems = emptyList()) }
        persistMemory()
    }

    fun setMemoryEnabled(enabled: Boolean) {
        _uiState.update { it.copy(memoryEnabled = enabled) }
        persistMemory()
    }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    /** Closes the "What's new" sheet for good: it shows once per update, however it was closed. */
    fun dismissWhatsNew() {
        _uiState.update { it.copy(whatsNew = false) }
        recordVersionSeen()
    }

    /** Never moves the recorded version backwards, so going back to an older build can't replay newer notes. */
    private fun recordVersionSeen() {
        runCatching {
            if (appVersionStore.lastSeenVersion() < currentVersionCode) appVersionStore.recordSeen(currentVersionCode)
        }
    }

    // ---- Personal (device-local; sent in the system message of every chat) ----

    /** Settings → Personal → Save. Blank fields are simply left out of the system message. */
    fun savePersonal(profile: PersonalProfile) {
        val cleaned = Personal.clean(profile)
        _uiState.update { it.copy(personal = cleaned) }
        viewModelScope.launch {
            personalSaveLock.withLock { runCatching { personalStore.save(_uiState.value.personal) } }
        }
    }

    /**
     * Privacy & data → "Delete all local data". Stops any reply, then wipes chats (and their attachments through
     * [onDeleteLocalAttachment]), memory, saved API keys, the connection settings and the PC pairing. Files the
     * screen owns (saved prompts, personas, saved connections) are removed by the caller.
     */
    fun deleteAllLocalData() {
        cancelEditing()
        val job = activeSendJob
        activeSendJob = null
        activeSendTarget = null
        job?.cancel()
        clearLocalCompanionSession()
        (credentialStore as? CompositeRelayCredentialStore)?.persistEnabled = false
        credentialStore.clearAllTokens()
        val freshConfig = RelayConfig()
        configStore.saveConfig(freshConfig)
        viewModelScope.launch {
            val current = _uiState.value
            val attachmentUris = (
                current.conversations.flatMap { c ->
                    MessageVersions.everyMessage(c.messages).flatMap { m -> m.attachments.map { it.localUri } }.toList()
                } +
                    current.stagedAttachments.map { it.localUri } +
                    current.parkedDrafts.values.flatMap { it.attachments }.map { it.localUri } +
                    current.draftBeforeEdit?.attachments.orEmpty().map { it.localUri }
                ).distinct()
            val fresh = listOf(newBlankConversation(clock(), freshConfig))
            conversationStore.saveConversations(fresh)
            runCatching { conversationStore.deleteKeptCopies() }
            memorySaveLock.withLock { memoryStore.save(UserMemoryState()) }
            personalSaveLock.withLock { runCatching { personalStore.save(PersonalProfile()) } }
            projectSaveLock.withLock { runCatching { projectStore.save(emptyList()) } }
            errorLogLock.withLock { runCatching { errorLogStore.save(emptyList()) } }
            _uiState.update {
                it.copy(
                    personal = PersonalProfile(),
                    projects = emptyList(),
                    errorLog = emptyList(),
                    isSending = false,
                    conversations = fresh,
                    activeConversationId = fresh.first().id,
                    draftText = "",
                    parkedDrafts = emptyMap(),
                    draftBeforeEdit = null,
                    editingMessageId = null,
                    stagedAttachments = emptyList(),
                    preflightWarnings = emptyList(),
                    memoryItems = emptyList(),
                    memoryEnabled = true,
                    relayConfig = freshConfig,
                    hasToken = false,
                    maskedTokenLabel = maskSecretToken(null),
                    backupKeyLabels = emptyList(),
                    capabilities = freshConfig.capabilities(),
                    readiness = buildBaselineReadiness(freshConfig, false, computeWorkMode(it.companion)),
                    notice = "All local data deleted."
                )
            }
            attachmentUris.forEach(onDeleteLocalAttachment)
        }
    }

    // ---- Error log (device-local; what failed and where, never chat text or keys) ----

    /** Settings → Error log → Clear. */
    fun clearErrorLog() {
        _uiState.update { it.copy(errorLog = emptyList()) }
        viewModelScope.launch { errorLogLock.withLock { runCatching { errorLogStore.save(emptyList()) } } }
    }

    /** The hosted service the current connection talks to ("Anthropic"), or null for your own server. */
    fun currentServiceName(config: RelayConfig = _uiState.value.relayConfig): String? =
        ProviderPresets.serviceNameFor(config.route, config.effectiveBaseUrl)

    /**
     * Adds a failure to the error log. A temporary chat leaves no trace, so its failures are skipped; a stopped reply
     * isn't a problem, so it is skipped too. Keys this phone knows are hidden again on top of the pattern redaction.
     */
    private fun recordError(error: ProviderError, config: RelayConfig, modelId: String, temporary: Boolean) {
        if (temporary || !ErrorLog.shouldRecord(error)) return
        val entry = ErrorLog.entryFor(
            id = UUID.randomUUID().toString(),
            error = error,
            atEpochMs = clock(),
            service = currentServiceName(config),
            baseUrl = config.effectiveBaseUrl,
            modelId = modelId,
            knownSecrets = listOf(runCatching { credentialStore.getToken(config) }.getOrNull()) +
                runCatching { credentialStore.getBackupTokens(config) }.getOrDefault(emptyList())
        )
        _uiState.update { it.copy(errorLog = ErrorLog.add(it.errorLog, entry)) }
        viewModelScope.launch {
            errorLogLock.withLock { runCatching { errorLogStore.save(_uiState.value.errorLog) } }
        }
    }

    /** Saves the latest memory state; the lock keeps quick successive edits in order. */
    private fun persistMemory() {
        viewModelScope.launch {
            memorySaveLock.withLock {
                val state = _uiState.value
                runCatching { memoryStore.save(UserMemoryState(state.memoryItems, state.memoryEnabled)) }
            }
        }
    }

    /**
     * The dispatch's single system message, without the PC hint: Orbit's reply style, Personal, memory (never in a
     * temporary chat), then the chat's own instructions. See [OrbitInstructions].
     */
    private fun instructionsFor(conversation: Conversation): String {
        val state = _uiState.value
        val memory = if (conversation.temporary || !state.memoryEnabled) null else UserMemory.systemBlock(state.memoryItems)
        val project = if (conversation.temporary) null else Projects.systemBlock(Projects.projectOf(conversation, state.projects))
        return OrbitInstructions.compose(state.personal, memory, conversation.systemPrompt, project)
    }

    /** Puts [instructionsFor] first, then the "Ask your PC" hint while a PC is paired, in one system message. */
    private fun withInstructions(messages: List<ChatMessage>, conversation: Conversation): List<ChatMessage> {
        val state = _uiState.value
        val createdAt = conversation.createdAtEpochMs
        val withSystem = OrbitInstructions.applyTo(messages, instructionsFor(conversation), createdAt)
        return AskPcProposals.applySystemHint(withSystem, askPcHintEnabled(state), createdAt)
    }

    private fun askPcHintEnabled(state: ChatUiState): Boolean =
        AskPcProposals.hintEnabled(state.companion.isPaired, state.companion.health?.workEnabled)

    fun updateDraft(text: String) {
        _uiState.update { it.copy(draftText = text) }
    }

    /** The target is captured before opening a picker or starting asynchronous file I/O. */
    fun addStagedAttachment(item: AttachmentManifestItem, targetId: String? = _uiState.value.activeConversationId): Boolean {
        var accepted = false
        _uiState.update { state ->
            accepted = targetId != null && state.conversations.any { it.id == targetId }
            if (!accepted || targetId == null) return@update state
            if (targetId != state.activeConversationId) {
                val before = state.parkedDrafts[targetId] ?: ParkedChatDraft("", emptyList())
                return@update state.copy(parkedDrafts = state.parkedDrafts +
                    (targetId to before.copy(attachments = before.attachments + item)))
            }
            val updated = state.stagedAttachments + item
            val preview = state.activeConversation?.messages.orEmpty() + ChatMessage(
                id = "preview",
                correlationId = "preview",
                role = MessageRole.USER,
                content = state.draftText,
                createdAtEpochMs = clock(),
                attachments = updated
            )
            val preflight = PreflightValidator.assess(preview, state.relayConfig)
            state.copy(stagedAttachments = updated, preflightWarnings = preflight.warnings)
        }
        if (!accepted) {
            val current = _uiState.value
            if (!isAttachmentUriReferenced(item.localUri, current.conversations, current.stagedAttachments)) {
                onDeleteLocalAttachment(item.localUri)
            }
        }
        return accepted
    }

    /** Imported text is merged into the originating draft, never whichever chat happens to be open. */
    fun appendImportedText(targetId: String?, text: String): Boolean {
        var accepted = false
        _uiState.update { state ->
            accepted = targetId != null && state.conversations.any { it.id == targetId }
            if (!accepted || targetId == null) return@update state
            if (targetId == state.activeConversationId) {
                state.copy(draftText = ShareIntentParser.mergeIntoDraft(state.draftText, text))
            } else {
                val before = state.parkedDrafts[targetId] ?: ParkedChatDraft("", emptyList())
                state.copy(parkedDrafts = state.parkedDrafts +
                    (targetId to before.copy(text = ShareIntentParser.mergeIntoDraft(before.text, text))))
            }
        }
        return accepted
    }

    fun stagedAttachmentCount(targetId: String?): Int {
        val state = _uiState.value
        if (targetId == null) return 0
        return if (targetId == state.activeConversationId) state.stagedAttachments.size
        else state.parkedDrafts[targetId]?.attachments?.size ?: 0
    }

    fun removeStagedAttachment(attachmentId: String) {
        var removedUri: String? = null
        _uiState.update { state ->
            val removed = state.stagedAttachments.firstOrNull { it.id == attachmentId }
            removedUri = removed?.localUri
            val remainingStaged = state.stagedAttachments.filterNot { it.id == attachmentId }
            val stillReferenced = isAttachmentUriReferenced(
                uri = removedUri,
                conversations = state.conversations,
                staged = remainingStaged
            )
            if (stillReferenced) removedUri = null
            val warnings = if (remainingStaged.isEmpty()) {
                emptyList()
            } else {
                val preview = state.activeConversation?.messages.orEmpty() + ChatMessage(
                    id = "preview",
                    correlationId = "preview",
                    role = MessageRole.USER,
                    content = state.draftText,
                    createdAtEpochMs = clock(),
                    attachments = remainingStaged
                )
                PreflightValidator.assess(preview, state.relayConfig).warnings
            }
            state.copy(
                stagedAttachments = remainingStaged,
                preflightWarnings = warnings
            )
        }
        removedUri?.let(onDeleteLocalAttachment)
    }

    fun clearStagedAttachments() {
        val removedUris = mutableListOf<String>()
        _uiState.update { state ->
            if (state.stagedAttachments.isEmpty()) return@update state
            state.stagedAttachments
                .map { it.localUri }
                .distinct()
                .filterNot { uri ->
                    isAttachmentUriReferenced(uri, state.conversations, emptyList())
                }
                .forEach { removedUris += it }
            state.copy(
                stagedAttachments = emptyList(),
                preflightWarnings = emptyList()
            )
        }
        removedUris.forEach(onDeleteLocalAttachment)
    }

    fun reportAttachmentImportFailure(reason: String) {
        if (reason.isBlank()) return
        _uiState.update { state ->
            state.copy(preflightWarnings = (state.preflightWarnings + reason).distinct())
        }
    }

    /** Park text AND files together. State is immutable so retries of StateFlow.update cannot mutate a side map. */
    private fun ChatUiState.withDraftSwappedTo(nextConversationId: String): ChatUiState {
        val currentId = activeConversationId
        if (currentId == nextConversationId) return this
        val next = parkedDrafts[nextConversationId]
        val parked = parkedDrafts.toMutableMap()
        parked.remove(nextConversationId)
        if (currentId != null) {
            if (draftText.isBlank() && stagedAttachments.isEmpty()) parked.remove(currentId)
            else parked[currentId] = ParkedChatDraft(draftText, stagedAttachments)
        }
        return copy(
            draftText = next?.text.orEmpty(),
            stagedAttachments = next?.attachments.orEmpty(),
            parkedDrafts = parked.toMap()
        )
    }

    fun selectConversation(conversationId: String) {
        if (_uiState.value.conversations.none { it.id == conversationId }) return
        if (_uiState.value.editingMessageId != null && _uiState.value.activeConversationId != conversationId) {
            cancelEditing()
        }
        discardTemporaryChat(keepId = conversationId)
        _uiState.update {
            it.withDraftSwappedTo(conversationId)
                .copy(activeConversationId = conversationId, preflightWarnings = emptyList())
        }
        restoreChatConnection()
    }

    /** Switching chats restores only a trusted, device-local target, and resolves its own credentials. */
    private fun restoreChatConnection() {
        val state = _uiState.value
        val chosen = state.activeConversation?.preferredConnection?.applyTo(state.relayConfig) ?: return
        if (chosen == state.relayConfig) return
        modelListGeneration++
        val token = credentialStore.getToken(chosen)
        val hasToken = !token.isNullOrBlank()
        _uiState.update {
            it.copy(
                relayConfig = chosen,
                modelList = ModelListUiState(),
                hasToken = hasToken,
                maskedTokenLabel = maskSecretToken(token),
                backupKeyLabels = backupLabels(chosen),
                capabilities = chosen.capabilities(),
                readiness = buildBaselineReadiness(chosen, hasToken, computeWorkMode(it.companion))
            )
        }
    }

    /** Save a selection on the current chat only. Temporary chats remain in memory. */
    private fun rememberChatConnection(config: RelayConfig) {
        val preferred = ChatConnectionPreference.capture(config)
        val active = _uiState.value.activeConversation ?: return
        if (active.preferredConnection == preferred) return
        _uiState.update { state ->
            state.copy(conversations = state.conversations.map { conversation ->
                if (conversation.id == active.id) conversation.copy(preferredConnection = preferred) else conversation
            })
        }
        if (!active.temporary) viewModelScope.launch { conversationStore.saveConversations(_uiState.value.conversations) }
    }

    /** What the composer held before "Edit" filled it, restored if the edit is cancelled. */
    // The pre-edit draft is held in immutable UI state, including its attachments.

    /** Loads one of your sent messages (text and attachments) into the composer to change and resend. */
    fun startEditingMessage(messageId: String) {
        val state = _uiState.value
        val messages = state.activeConversation?.messages.orEmpty()
        if (!EditResendPolicy.canEdit(messages, messageId, state.isSending)) return
        val message = messages.first { it.id == messageId }
        val original = state.draftBeforeEdit ?: ParkedChatDraft(state.draftText, state.stagedAttachments)
        // Preserve unrelated draft files before clearing the edit composer.
        _uiState.update { it.copy(draftBeforeEdit = original) }
        clearStagedAttachments()
        _uiState.update {
            it.copy(
                editingMessageId = messageId,
                draftText = message.content,
                stagedAttachments = message.attachments.map { att -> att.copy(omittedFromRemote = false) },
                preflightWarnings = emptyList()
            )
        }
    }

    fun cancelEditing() {
        if (_uiState.value.editingMessageId == null) return
        clearStagedAttachments()
        _uiState.update {
            it.copy(editingMessageId = null, draftBeforeEdit = null,
                draftText = it.draftBeforeEdit?.text.orEmpty(),
                stagedAttachments = it.draftBeforeEdit?.attachments.orEmpty())
        }
    }

    /**
     * Shows version [target] (from 0) of the turn that starts with [messageId], the "< 2 / 3 >" arrows. Waits while a
     * reply is on its way or a message is being edited, since either would be pulled out from under you.
     */
    fun showVersion(messageId: String, target: Int) {
        val state = _uiState.value
        if (state.isSending || state.editingMessageId != null) return
        val active = state.activeConversation ?: return
        val index = active.messages.indexOfFirst { it.id == messageId }
        if (index < 0) return
        val switched = MessageVersions.switchTo(active.messages, index, target)
        if (switched === active.messages) return
        // Flipping between versions is reading, not a change, so the chat keeps its place in the list.
        val updatedList = upsertConversation(state.conversations, active.copy(messages = switched))
        _uiState.update { it.copy(conversations = updatedList) }
        viewModelScope.launch { conversationStore.saveConversations(updatedList) }
    }

    /**
     * Opens a temporary chat: never saved or backed up, left out of the chat list, and it skips memory (Personal still
     * applies). Leaving it in any way discards it. There is only ever one; starting another replaces it.
     */
    fun startTemporaryChat() {
        val state = _uiState.value
        if (state.isTemporaryChat && state.activeConversation?.messages.isNullOrEmpty()) return
        if (!state.isTemporaryChat) chatBeforeTemporary = state.activeConversationId
        cancelEditing()
        discardTemporaryChat()
        val fresh = newBlankConversation(clock(), _uiState.value.relayConfig).copy(temporary = true)
        _uiState.update {
            it.withDraftSwappedTo(fresh.id).copy(
                conversations = listOf(fresh) + it.conversations,
                activeConversationId = fresh.id,
                preflightWarnings = emptyList()
            )
        }
    }

    /** The saved chat that was open when the temporary one started, so turning it off goes back there. */
    private var chatBeforeTemporary: String? = null

    /** Ends the temporary chat (discarding it) and returns to the chat that was open before, or a new one. */
    fun leaveTemporaryChat() {
        if (!_uiState.value.isTemporaryChat) return
        val back = chatBeforeTemporary?.takeIf { id -> _uiState.value.savedConversations.any { it.id == id } }
        chatBeforeTemporary = null
        if (back != null) selectConversation(back) else createNewConversation()
    }

    /**
     * Drops the temporary chat unless it is [keepId]: stops its reply if one is on the way and deletes files only it
     * used. There is nothing to unsave, since it was never written.
     */
    private fun discardTemporaryChat(keepId: String? = null) {
        val temp = _uiState.value.conversations.firstOrNull { it.temporary && it.id != keepId } ?: return
        val stoppingReply = activeSendTarget?.first == temp.id
        if (stoppingReply) {
            val job = activeSendJob
            activeSendJob = null
            activeSendTarget = null
            job?.cancel()
        }
        // Files attached in the temporary chat but not sent go with it, like its unsent text, so they can't follow
        // you into a saved chat.
        val unsentFiles = mutableListOf<String>()
        _uiState.update { state ->
            val wasOpen = state.activeConversationId == temp.id
            unsentFiles.clear()
            if (wasOpen) unsentFiles += state.stagedAttachments.map { it.localUri }
            unsentFiles += state.parkedDrafts[temp.id]?.attachments.orEmpty().map { it.localUri }
            state.copy(
                isSending = if (stoppingReply) false else state.isSending,
                conversations = state.conversations.filterNot { it.id == temp.id },
                parkedDrafts = state.parkedDrafts - temp.id,
                // Null, so the draft swap that follows drops the temporary chat's unsent text instead of keeping it.
                activeConversationId = if (wasOpen) null else state.activeConversationId,
                draftText = if (wasOpen) "" else state.draftText,
                stagedAttachments = if (wasOpen) emptyList() else state.stagedAttachments,
                preflightWarnings = if (wasOpen) emptyList() else state.preflightWarnings
            )
        }
        val after = _uiState.value
        (MessageVersions.everyMessage(temp.messages).flatMap { it.attachments.asSequence() }.map { it.localUri } +
            unsentFiles.asSequence())
            .distinct()
            .filterNot { uri -> isAttachmentUriReferenced(uri, after.conversations, after.stagedAttachments) }
            .toList()
            .forEach(onDeleteLocalAttachment)
    }

    fun createNewConversation() = startConversation(projectId = null)

    /** A new chat inside a project, so the project's instructions and files apply from its first message. */
    fun newChatInProject(projectId: String) {
        if (_uiState.value.projects.none { it.id == projectId }) return
        startConversation(projectId)
    }

    private fun startConversation(projectId: String?): Job {
        cancelEditing()
        discardTemporaryChat()
        return viewModelScope.launch {
            val current = _uiState.value
            val fresh = newBlankConversation(clock(), current.relayConfig).copy(projectId = projectId)
            val updated = listOf(fresh) + current.conversations
            conversationStore.saveConversations(updated)
            _uiState.update {
                it.withDraftSwappedTo(fresh.id).copy(
                    conversations = updated,
                    activeConversationId = fresh.id,
                    preflightWarnings = emptyList()
                )
            }
        }
    }

    /**
     * "Branch from here": opens a new chat holding the active chat up to [messageId], placed at the top of the
     * list; the original chat is left untouched. Attachment files are shared, and deleting either chat keeps
     * files the other still uses.
     */
    fun branchFrom(messageId: String) {
        // A branch would be a saved copy of a chat that promised not to be saved.
        if (_uiState.value.isSending || _uiState.value.isTemporaryChat) return
        cancelEditing()
        viewModelScope.launch {
            val current = _uiState.value
            val source = current.activeConversation ?: return@launch
            val branch = com.hydrogen.ai.model.ConversationBranch.branchAt(
                source, messageId, UUID.randomUUID().toString(), clock()
            ) { UUID.randomUUID().toString() } ?: return@launch
            val updated = listOf(branch) + current.conversations
            conversationStore.saveConversations(updated)
            _uiState.update {
                it.withDraftSwappedTo(branch.id).copy(
                    conversations = updated,
                    activeConversationId = branch.id,
                    preflightWarnings = emptyList()
                )
            }
        }
    }

    /** JSON backup of every non-empty conversation (no device-local attachment paths). */
    fun exportBackupJson(): String = ChatBackupCodec.encode(_uiState.value.savedConversations, clock())

    /** Every saved chat as one readable Markdown file (newest first); empty when there is nothing to export. */
    fun exportAllMarkdown(): String =
        com.hydrogen.ai.model.ConversationMarkdownExporter.exportAll(_uiState.value.savedConversations)

    /** Adds conversations from a backup; existing conversations are never overwritten. */
    fun importBackup(text: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            when (val decoded = ChatBackupCodec.decode(text)) {
                is ChatBackupCodec.DecodeResult.Failure -> onResult("Import failed: ${decoded.reason}")
                is ChatBackupCodec.DecodeResult.Success -> {
                    val merged = ChatBackupCodec.merge(_uiState.value.conversations, decoded.conversations)
                    if (merged.added > 0) {
                        conversationStore.saveConversations(merged.conversations)
                        _uiState.update { it.copy(conversations = merged.conversations) }
                    }
                    onResult(
                        "Imported ${merged.added} conversation(s)" +
                            if (merged.skipped > 0) "; ${merged.skipped} already on this device." else "."
                    )
                }
            }
        }
    }

    /** Renames a conversation locally; blank titles are ignored and list order is unchanged. */
    fun renameConversation(conversationId: String, newTitle: String) {
        val title = ConversationListTools.normalizeTitle(newTitle) ?: return
        viewModelScope.launch {
            val current = _uiState.value
            if (current.conversations.none { it.id == conversationId }) return@launch
            val updated = current.conversations.map {
                if (it.id == conversationId) it.copy(title = title) else it
            }
            conversationStore.saveConversations(updated)
            _uiState.update { state ->
                state.copy(
                    conversations = state.conversations.map {
                        if (it.id == conversationId) it.copy(title = title) else it
                    }
                )
            }
        }
    }

    /** Sets or clears (blank) a chat's instructions; they apply to the next send or retry. */
    fun setSystemPrompt(conversationId: String, text: String) {
        val prompt = SystemPromptPolicy.normalize(text)
        viewModelScope.launch {
            val current = _uiState.value
            if (current.conversations.none { it.id == conversationId }) return@launch
            val updated = current.conversations.map {
                if (it.id == conversationId) it.copy(systemPrompt = prompt) else it
            }
            conversationStore.saveConversations(updated)
            _uiState.update { state ->
                state.copy(
                    conversations = state.conversations.map {
                        if (it.id == conversationId) it.copy(systemPrompt = prompt) else it
                    }
                )
            }
        }
    }

    /** Pins a chat to the top of the list, or unpins it. Order within each section is unchanged. */
    fun togglePinned(conversationId: String) {
        viewModelScope.launch {
            val current = _uiState.value
            if (current.conversations.none { it.id == conversationId }) return@launch
            val flip: (Conversation) -> Conversation = {
                if (it.id == conversationId) it.copy(pinned = !it.pinned) else it
            }
            conversationStore.saveConversations(current.conversations.map(flip))
            _uiState.update { state -> state.copy(conversations = state.conversations.map(flip)) }
        }
    }

    // ---- Archive ----

    /**
     * Archives a chat (out of the list, still on this device, still found by search) or brings it back. Archiving
     * unpins it. Archiving the open chat moves you to a new one, like closing it. A temporary chat has nothing to keep.
     */
    fun setArchived(conversationId: String, archived: Boolean) {
        val target = _uiState.value.conversations.firstOrNull { it.id == conversationId } ?: return
        if (target.temporary || target.archived == archived) return
        val change: (Conversation) -> Conversation = {
            if (it.id == conversationId) it.copy(archived = archived, pinned = if (archived) false else it.pinned) else it
        }
        val leavingActive = archived && _uiState.value.activeConversationId == conversationId
        if (leavingActive) cancelEditing()
        viewModelScope.launch {
            conversationStore.saveConversations(_uiState.value.conversations.map(change))
            _uiState.update { state ->
                state.copy(
                    conversations = state.conversations.map(change),
                    notice = if (archived) "Chat archived. Find it under Archived chats." else "Chat moved back to your list."
                )
            }
            if (leavingActive && _uiState.value.activeConversationId == conversationId) {
                val project = target.projectId?.takeIf { id -> _uiState.value.projects.any { it.id == id } }
                startConversation(project)
            }
        }
    }

    // ---- Projects (device-local; their instructions and files go only into the project's chats) ----

    /** Creates a project and returns its id, or null when the name is blank or there are already too many. */
    fun createProject(name: String, instructions: String = ""): String? {
        val cleanName = Projects.normalizeName(name) ?: return null
        if (_uiState.value.projects.size >= Projects.MAX_PROJECTS) return null
        val now = clock()
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            instructions = Projects.cleanInstructions(instructions),
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        _uiState.update { it.copy(projects = listOf(project) + it.projects) }
        persistProjects()
        return project.id
    }

    /** Renames a project and sets its instructions. A blank name keeps the old one. */
    fun updateProject(projectId: String, name: String, instructions: String) {
        val now = clock()
        _uiState.update { state ->
            state.copy(projects = state.projects.map { p ->
                if (p.id != projectId) p else p.copy(
                    name = Projects.normalizeName(name) ?: p.name,
                    instructions = Projects.cleanInstructions(instructions),
                    updatedAtEpochMs = now
                )
            })
        }
        persistProjects()
    }

    /** Adds a file's text to a project. False when the project is full ([Projects.MAX_FILES]) or the text is blank. */
    fun addProjectFile(projectId: String, name: String, text: String, truncated: Boolean): Boolean {
        val now = clock()
        val file = ProjectFile(id = UUID.randomUUID().toString(), name = name, text = text, addedAtEpochMs = now, truncated = truncated)
        var added = false
        _uiState.update { state ->
            // update() may run this more than once; only the last run counts.
            added = false
            state.copy(projects = state.projects.map { p ->
                if (p.id != projectId) p else Projects.withFile(p, file, now)?.also { added = true } ?: p
            })
        }
        if (added) persistProjects()
        return added
    }

    fun removeProjectFile(projectId: String, fileId: String) {
        val now = clock()
        _uiState.update { state ->
            state.copy(projects = state.projects.map { p ->
                if (p.id != projectId || p.files.none { it.id == fileId }) p
                else p.copy(files = p.files.filterNot { it.id == fileId }, updatedAtEpochMs = now)
            })
        }
        persistProjects()
    }

    /** Deletes a project. Its chats are kept and go back to the main list, without the project's instructions. */
    fun deleteProject(projectId: String) {
        if (_uiState.value.projects.none { it.id == projectId }) return
        val release: (Conversation) -> Conversation = { if (it.projectId == projectId) it.copy(projectId = null) else it }
        _uiState.update { state -> state.copy(projects = state.projects.filterNot { it.id == projectId }) }
        persistProjects()
        viewModelScope.launch {
            conversationStore.saveConversations(_uiState.value.conversations.map(release))
            _uiState.update { state -> state.copy(conversations = state.conversations.map(release)) }
        }
    }

    /** Moves a chat into a project, or out of one with null. Temporary chats stay out of projects. */
    fun moveToProject(conversationId: String, projectId: String?) {
        val state = _uiState.value
        val target = state.conversations.firstOrNull { it.id == conversationId } ?: return
        if (target.temporary || target.projectId == projectId) return
        if (projectId != null && state.projects.none { it.id == projectId }) return
        val move: (Conversation) -> Conversation = { if (it.id == conversationId) it.copy(projectId = projectId) else it }
        viewModelScope.launch {
            conversationStore.saveConversations(_uiState.value.conversations.map(move))
            val name = projectId?.let { id -> _uiState.value.projects.firstOrNull { it.id == id }?.name }
            _uiState.update { s ->
                s.copy(
                    conversations = s.conversations.map(move),
                    notice = if (name != null) "Moved to $name." else "Removed from the project."
                )
            }
        }
    }

    /** Saves the latest projects; the lock keeps quick successive edits in order. */
    private fun persistProjects() {
        viewModelScope.launch {
            projectSaveLock.withLock { runCatching { projectStore.save(_uiState.value.projects) } }
        }
    }

    fun deleteConversation(conversationId: String) {
        if (_uiState.value.activeConversationId == conversationId) cancelEditing()
        val cancellingActiveSend = activeSendTarget?.first == conversationId
        val jobToCancel = if (cancellingActiveSend) activeSendJob else null
        if (cancellingActiveSend) {
            activeSendJob = null
            activeSendTarget = null
            jobToCancel?.cancel()
        }
        viewModelScope.launch {
            val current = _uiState.value
            val deleted = current.conversations.firstOrNull { it.id == conversationId }
            val remaining = current.conversations.filterNot { it.id == conversationId }
            // Something to open that isn't archived: opening an archived chat would look like it came back.
            val nextList = if (remaining.none { !it.archived }) {
                listOf(newBlankConversation(clock(), current.relayConfig)) + remaining
            } else {
                remaining
            }
            conversationStore.saveConversations(nextList)
            val deletingActive = current.activeConversationId == conversationId
            val deletedDraftUris = current.parkedDrafts[conversationId]?.attachments.orEmpty().map { it.localUri } +
                (if (deletingActive) current.stagedAttachments.map { it.localUri } else emptyList())
            val nextActive = if (deletingActive) {
                nextList.first { !it.archived }.id
            } else {
                current.activeConversationId
            }
            _uiState.update {
                val swapped = if (deletingActive && nextActive != null && it.activeConversationId == conversationId) {
                    it.copy(activeConversationId = null, draftText = "", stagedAttachments = emptyList())
                        .withDraftSwappedTo(nextActive)
                } else it
                swapped.copy(
                    parkedDrafts = swapped.parkedDrafts - conversationId,
                    isSending = if (cancellingActiveSend) false else it.isSending,
                    conversations = nextList,
                    activeConversationId = nextActive,
                    preflightWarnings = if (deletingActive) emptyList() else it.preflightWarnings
                )
            }
            // Files from hidden versions go too; nothing else can show them once the chat is gone.
            (deleted?.messages.orEmpty().let { MessageVersions.everyMessage(it).flatMap { m -> m.attachments }.toList() }
                .map { it.localUri } + deletedDraftUris)
                .distinct()
                .filterNot { uri ->
                    isAttachmentUriReferenced(uri, nextList, _uiState.value.stagedAttachments)
                }
                .forEach(onDeleteLocalAttachment)
            if (deletingActive) restoreChatConnection()
        }
    }

    fun setReasoningMode(mode: com.hydrogen.ai.model.ReasoningMode) {
        if (_uiState.value.isSending) return
        val config = _uiState.value.relayConfig
        if (mode !in reasoningOptions(config.route, config.effectiveBaseUrl, config.modelId, config.openRouterReasoningModels)) return
        val updated = config.copy(reasoningMode = mode)
        configStore.saveConfig(updated)
        _uiState.update { it.copy(relayConfig = updated) }
        rememberChatConnection(updated)
    }

    fun saveRelaySettings(
        route: ProviderRoute,
        baseUrl: String,
        modelId: String,
        token: String?,
        maxContextTokens: Int,
        maxImages: Int,
        streamingEnabled: Boolean,
        rememberTokenInKeystore: Boolean,
        webSearchEnabled: Boolean,
        /** Null keeps the saved backup keys; a list replaces them. */
        backupTokens: List<String>? = null,
        reasoningMode: com.hydrogen.ai.model.ReasoningMode = _uiState.value.relayConfig.reasoningMode,
        discoveredReasoningModels: Set<String>? = null
    ) {
        val defaultModel = when (route) {
            ProviderRoute.GEMINI_HYDROGEN_RELAY -> HydrogenDefaults.PRIMARY_MODEL_ID
            ProviderRoute.GOOGLE_AI_STUDIO_DIRECT -> HydrogenDefaults.GOOGLE_AI_STUDIO_DEFAULT_MODEL
            ProviderRoute.ANTHROPIC_MESSAGES -> "claude-opus-5-5"
            ProviderRoute.LOCAL_OFFLINE -> HydrogenDefaults.PRIMARY_MODEL_ID
        }
        val previous = _uiState.value.relayConfig
        // Keys are looked up by service/origin; switching retains each provider's own key.
        val draft = previous.copy(
            route = route,
            baseUrl = when (route) {
                ProviderRoute.GEMINI_HYDROGEN_RELAY -> ProviderPresets.normalizeBaseUrl(baseUrl)
                ProviderRoute.GOOGLE_AI_STUDIO_DIRECT -> HydrogenDefaults.GOOGLE_AI_STUDIO_BASE_URL
                ProviderRoute.ANTHROPIC_MESSAGES -> baseUrl.trim().trimEnd('/').ifEmpty { HydrogenDefaults.ANTHROPIC_BASE_URL }
                ProviderRoute.LOCAL_OFFLINE -> ""
            },
            modelId = modelId.trim().ifBlank { defaultModel },
            maxContextTokensBudget = maxContextTokens.coerceAtLeast(4_000),
            maxImagesPerRequest = maxImages.coerceIn(1, 32),
            streamingEnabled = streamingEnabled,
            rememberTokenInKeystore = rememberTokenInKeystore,
            webSearchEnabled = webSearchEnabled,
            reasoningMode = reasoningMode
        )
        val scoped = if (draft.route == previous.route && draft.effectiveBaseUrl == previous.effectiveBaseUrl) draft
            else draft.copy(openRouterReasoningModels = emptySet())
        val cleaned = normalizeReasoning(
            if (discoveredReasoningModels != null && com.hydrogen.ai.model.ReasoningPolicy.isOpenRouter(scoped.effectiveBaseUrl))
                scoped.copy(openRouterReasoningModels = discoveredReasoningModels)
            else scoped
        )
        val sameConnection = cleaned.route == previous.route && cleaned.effectiveBaseUrl == previous.effectiveBaseUrl && token == null
        if (!sameConnection) modelListGeneration++
        val compositeStore = credentialStore as? CompositeRelayCredentialStore
        compositeStore?.persistEnabled = rememberTokenInKeystore
        if (!rememberTokenInKeystore) {
            credentialStore.clearPersistedTokens()
        }
        configStore.saveConfig(cleaned)
        // A paste may hold several keys: the first becomes the main key and the rest are added as backup keys.
        val pastedKeys = token?.let { ProviderPresets.splitPastedKeys(it) }.orEmpty()
        if (token != null) credentialStore.setToken(cleaned, pastedKeys.firstOrNull() ?: token.trim())
        val selectedToken = credentialStore.getToken(cleaned)
        val hasToken = !selectedToken.isNullOrBlank()
        val extraKeys = pastedKeys.drop(1)
        val newBackups = when {
            backupTokens != null -> backupTokens + extraKeys
            extraKeys.isNotEmpty() -> credentialStore.getBackupTokens(cleaned) + extraKeys
            else -> null
        }
        if (newBackups != null) {
            credentialStore.setBackupTokens(cleaned, cleanBackupTokens(newBackups, selectedToken))
        }
        _uiState.update {
            it.copy(
                relayConfig = cleaned,
                modelList = if (!sameConnection)
                    ModelListUiState() else it.modelList,
                hasToken = hasToken,
                maskedTokenLabel = maskSecretToken(selectedToken),
                backupKeyLabels = backupLabels(cleaned),
                capabilities = cleaned.capabilities(),
                readiness = if (sameConnection && cleaned.modelId == previous.modelId && hasToken == it.hasToken) it.readiness
                    else buildBaselineReadiness(cleaned, hasToken, computeWorkMode(it.companion)),
                notice = if (extraKeys.isEmpty()) it.notice
                else "Saved the first key as your main key and kept the others as backup keys " +
                    "(${backupLabels(cleaned).size} now). Orbit switches to the next one if a key hits a limit."
            )
        }
        rememberChatConnection(cleaned)
    }

    /**
     * Adds backup keys for the active service. A paste may hold several keys, one per line. It needs a saved main
     * key, and repeats are ignored.
     */
    fun addBackupKey(key: String) {
        val config = _uiState.value.relayConfig
        val main = credentialStore.getToken(config)
        val pasted = ProviderPresets.splitPastedKeys(key)
        if (main.isNullOrBlank() || pasted.isEmpty()) return
        credentialStore.setBackupTokens(config, cleanBackupTokens(credentialStore.getBackupTokens(config) + pasted, main))
        _uiState.update { it.copy(backupKeyLabels = backupLabels(config)) }
    }

    fun removeBackupKey(index: Int) {
        val config = _uiState.value.relayConfig
        val current = credentialStore.getBackupTokens(config)
        if (index !in current.indices) return
        credentialStore.setBackupTokens(config, current.filterIndexed { i, _ -> i != index })
        _uiState.update { it.copy(backupKeyLabels = backupLabels(config)) }
    }

    /** Remove only the active service's key, leaving other providers and their credentials untouched. */
    fun deleteActiveProviderCredential() {
        val config = _uiState.value.relayConfig
        credentialStore.setToken(config, null)
        credentialStore.setBackupTokens(config, emptyList())
        _uiState.update {
            it.copy(
                hasToken = false,
                maskedTokenLabel = maskSecretToken(null),
                backupKeyLabels = emptyList(),
                readiness = buildBaselineReadiness(config, false, computeWorkMode(it.companion))
            )
        }
    }

    /** Discovery never changes the active chat connection or saves the pasted key. */
    private fun providerDraft(route: ProviderRoute, url: String, model: String = ""): RelayConfig =
        _uiState.value.relayConfig.copy(route = route, baseUrl = url, modelId = model)

    fun savedDraftKeyLabel(route: ProviderRoute, url: String): String? =
        credentialStore.getToken(providerDraft(route, url))?.takeIf { it.isNotBlank() }?.let(::maskSecretToken)

    suspend fun browseDraftModels(route: ProviderRoute, url: String, model: String, pasted: String?): ModelListUiState {
        val config = providerDraft(route, url, model)
        val token = pasted?.let { ProviderPresets.splitPastedKeys(it).firstOrNull() }
            ?: credentialStore.getToken(config)
        return when (val result = backend.listModels(config, token)) {
            is ModelListResult.Success -> ModelListUiState(
                baseUrl = url, modelIds = result.modelIds,
                message = (if (result.modelIds.size == 1) "1 model found" else "${result.modelIds.size} models found") + ". Choose one, then connect.", reasoningModelIds = result.reasoningModelIds
            )
            is ModelListResult.Failure -> ModelListUiState(baseUrl = url, message = result.message)
        }
    }

    private var modelListGeneration = 0L

    fun loadModelList() {
        val state = _uiState.value
        if (state.modelList.isLoading) return
        val config = state.relayConfig
        val generation = ++modelListGeneration
        val token = credentialStore.getToken(config)
        _uiState.update { it.copy(modelList = ModelListUiState(isLoading = true, baseUrl = config.baseUrl)) }
        viewModelScope.launch {
            val result = backend.listModels(config, token)
            if (generation != modelListGeneration) return@launch
            val listed = when (result) {
                is ModelListResult.Success -> ModelListUiState(
                    baseUrl = config.baseUrl,
                    modelIds = result.modelIds,
                    message = result.modelIds.size.let { n ->
                        if (n == 1) "1 model available. Tap it, then Save." else "$n models available. Tap one, then Save."
                    }
                )
                is ModelListResult.Failure -> ModelListUiState(baseUrl = config.baseUrl, message = result.message)
            }
            val current = _uiState.value.relayConfig
            if (current.route != config.route || current.effectiveBaseUrl != config.effectiveBaseUrl ||
                credentialStore.getToken(config) != token) {
                _uiState.update { it.copy(modelList = ModelListUiState()) }
                return@launch
            }
            val catalog = (result as? ModelListResult.Success)?.reasoningModelIds
            val updated = if (catalog != null && com.hydrogen.ai.model.ReasoningPolicy.isOpenRouter(current.effectiveBaseUrl))
                normalizeReasoning(current.copy(openRouterReasoningModels = catalog)) else current
            if (updated != current) configStore.saveConfig(updated)
            _uiState.update { it.copy(modelList = listed, relayConfig = updated) }
        }
    }

    fun checkRelayHealth() {
        val state = _uiState.value
        if (state.isCheckingHealth) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingHealth = true) }
            val checkedToken = credentialStore.getToken(state.relayConfig)
            val result = backend.checkHealth(state.relayConfig, checkedToken)
            if (_uiState.value.relayConfig != state.relayConfig || credentialStore.getToken(state.relayConfig) != checkedToken) {
                // The connection changed while this check was in flight. Its answer is about the old
                // service, so it must not overwrite the new service's readiness.
                _uiState.update { it.copy(isCheckingHealth = false) }
                return@launch
            }
            val now = clock()
            val workMode = computeWorkMode(_uiState.value.companion)
            val updated = when (result) {
                is RelayHealthResult.Reachable -> ProviderReadiness(
                    configured = state.relayConfig.isConfigured &&
                        credentialReady(state.relayConfig, credentialStore.hasToken(state.relayConfig)),
                    relayReachable = VerificationState.PASS,
                    authenticated = if (result.authenticated) VerificationState.PASS else VerificationState.UNKNOWN,
                    workMode = workMode,
                    lastVerifiedAtEpochMs = now,
                    statusDetail = result.detail
                )
                is RelayHealthResult.Failure -> {
                    val hasToken = credentialStore.hasToken(state.relayConfig)
                    updateReadinessForError(
                        current = buildBaselineReadiness(state.relayConfig, hasToken, workMode),
                        config = state.relayConfig,
                        hasToken = hasToken,
                        error = result.error,
                        now = now
                    )
                }
            }
            _uiState.update { it.copy(isCheckingHealth = false, readiness = updated) }
        }
    }

    fun checkCompanionHealth(baseUrl: String, deviceName: String) {
        val current = _uiState.value.companion
        if (current.isCheckingHealth || current.isPairing || current.isRevoking) return
        val normalizedConfig = normalizeCompanionConfig(baseUrl, deviceName)
        companionSessionStore.saveConfig(normalizedConfig)
        _uiState.update {
            it.copy(
                companion = it.companion.copy(
                    config = normalizedConfig,
                    isCheckingHealth = true,
                    statusMessage = null,
                    errorMessage = null
                )
            )
        }
        viewModelScope.launch {
            when (val result = companionClient.checkHealth(normalizedConfig.baseUrl)) {
                is CompanionResult.Success -> {
                    val h = result.value
                    _uiState.update {
                        val staleLocalSession = it.companion.session != null &&
                            h.status.equals("unpaired", ignoreCase = true)
                        val updatedCompanion = it.companion.copy(
                            isCheckingHealth = false,
                            health = h,
                            statusMessage = if (staleLocalSession) {
                                null
                            } else {
                                // The status card describes the PC in plain words; this only confirms the tap.
                                "Connection checked."
                            },
                            errorMessage = if (staleLocalSession) {
                                "This phone is unpaired on your PC. Tap Remove from phone, then pair again."
                            } else {
                                null
                            }
                        )
                        it.copy(
                            companion = updatedCompanion,
                            readiness = it.readiness.copy(workMode = computeWorkMode(updatedCompanion))
                        )
                    }
                }
                is CompanionResult.Failure -> {
                    _uiState.update {
                        val updatedCompanion = it.companion.copy(
                            isCheckingHealth = false,
                            errorMessage = result.userMessage
                        )
                        it.copy(
                            companion = updatedCompanion,
                            readiness = it.readiness.copy(workMode = computeWorkMode(updatedCompanion))
                        )
                    }
                }
            }
        }
    }

    fun pairWithCompanion(
        baseUrl: String,
        deviceName: String,
        pairingId: String,
        pairingCode: String
    ) {
        val current = _uiState.value.companion
        if (current.isCheckingHealth || current.isPairing || current.isRevoking) return
        val parsedChallenge = CompanionPairingInputParser.normalize(pairingId, pairingCode)
        val normalizedConfig = normalizeCompanionConfig(baseUrl, deviceName)
        companionSessionStore.saveConfig(normalizedConfig)
        _uiState.update {
            it.copy(
                companion = it.companion.copy(
                    config = normalizedConfig,
                    isPairing = true,
                    statusMessage = null,
                    errorMessage = null
                )
            )
        }
        viewModelScope.launch {
            when (
                val result = companionClient.pairDevice(
                    baseUrl = normalizedConfig.baseUrl,
                    deviceName = normalizedConfig.deviceName,
                    pairingId = parsedChallenge.pairingId,
                    pairingCode = parsedChallenge.pairingCode
                )
            ) {
                is CompanionResult.Success -> {
                    val session = result.value
                    companionSessionStore.saveSession(session)
                    val refreshedHealth = (companionClient.checkHealth(normalizedConfig.baseUrl) as? CompanionResult.Success)?.value
                    _uiState.update {
                        val updatedCompanion = it.companion.copy(
                            isPairing = false,
                            session = session,
                            health = refreshedHealth ?: it.companion.health,
                            statusMessage = "Paired with PC companion (key ${session.keyId}).",
                            errorMessage = null
                        )
                        it.copy(
                            companion = updatedCompanion,
                            readiness = it.readiness.copy(workMode = computeWorkMode(updatedCompanion))
                        )
                    }
                }
                is CompanionResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            companion = it.companion.copy(
                                isPairing = false,
                                errorMessage = result.userMessage
                            )
                        )
                    }
                }
            }
        }
    }

    fun revokeCompanionPairing() {
        val current = _uiState.value.companion
        val session = current.session ?: return
        if (current.isCheckingHealth || current.isPairing || current.isRevoking) return
        cancelCompanionPollingJobs()
        _uiState.update {
            it.copy(
                companion = it.companion.copy(
                    isRevoking = true,
                    isPollingApproval = false,
                    isRunningWorkTask = false,
                    statusMessage = null,
                    errorMessage = null
                )
            )
        }
        viewModelScope.launch {
            when (val result = companionClient.revokePairing(session)) {
                is CompanionResult.Success -> {
                    companionSessionStore.clearSession()
                    val refreshedHealth = (companionClient.checkHealth(session.baseUrl) as? CompanionResult.Success)?.value
                    _uiState.update {
                        val updatedCompanion = it.companion.copy(
                            isRevoking = false,
                            session = null,
                            health = refreshedHealth ?: it.companion.health,
                            activeApproval = null,
                            activeApprovalTargetPath = "",
                            pendingWorkOffsetBytes = null,
                            activeTask = null,
                            statusMessage = "Companion pairing revoked.",
                            errorMessage = null
                        )
                        it.copy(
                            companion = updatedCompanion,
                            readiness = it.readiness.copy(workMode = computeWorkMode(updatedCompanion))
                        )
                    }
                }
                is CompanionResult.Failure -> {
                    if (result.httpStatus == 401) {
                        companionSessionStore.clearSession()
                        _uiState.update {
                            val updatedCompanion = it.companion.copy(
                                isRevoking = false,
                                session = null,
                                activeApproval = null,
                                activeApprovalTargetPath = "",
                                pendingWorkOffsetBytes = null,
                                activeTask = null,
                                statusMessage = "Companion already unpaired; local session cleared.",
                                errorMessage = null
                            )
                            it.copy(
                                companion = updatedCompanion,
                                readiness = it.readiness.copy(workMode = computeWorkMode(updatedCompanion))
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                companion = it.companion.copy(
                                    isRevoking = false,
                                    errorMessage = result.userMessage
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun clearLocalCompanionSession() {
        cancelCompanionPollingJobs()
        companionSessionStore.clearSession()
        _uiState.update {
            val updatedCompanion = it.companion.copy(
                session = null,
                isPollingApproval = false,
                isRunningWorkTask = false,
                isCancellingWorkTask = false,
                activeApproval = null,
                activeApprovalTargetPath = "",
                pendingWorkOffsetBytes = null,
                activeTask = null,
                statusMessage = "Local companion session cleared.",
                errorMessage = null
            )
            it.copy(
                companion = updatedCompanion,
                readiness = it.readiness.copy(workMode = computeWorkMode(updatedCompanion))
            )
        }
    }

    fun stageCompanionWorkTarget(
        actionType: WorkActionType,
        targetPath: String,
        offsetBytes: Int? = null
    ) {
        val cleanPath = targetPath.trim()
        val safeOffset = offsetBytes?.takeIf { it > 0 && actionType == WorkActionType.READ_FILE_PREVIEW }
        // A live (PENDING/APPROVED) approval is what the Windows owner sees/grants for the exact action+path it
        // was requested with. Staging a different action or path (e.g. editing the path, then Reset Offset)
        // must detach it; otherwise the card shows it as approved for the new path and Run would pair the old
        // approvalId with a target the owner never saw. Changing only the byte offset keeps the approval.
        val before = _uiState.value.companion
        val detachedApproval = before.activeApproval?.takeIf { approval ->
            (approval.status == "PENDING" || approval.status == "APPROVED") &&
                (actionType != before.activeApprovalAction || cleanPath != before.activeApprovalTargetPath)
        }
        if (detachedApproval != null) {
            // Cancel any wait/check-once for it so a late response cannot re-attach it to the new target.
            approvalPollJob?.cancel()
            approvalPollJob = null
        }
        _uiState.update { state ->
            val current = state.companion
            val hint = if (safeOffset != null) {
                "Staged next chunk for $cleanPath from byte $safeOffset. Request PC approval to read it."
            } else {
                "Staged ${actionType.displayName} for '$cleanPath'. Request PC approval when ready."
            }
            val detachNote = detachedApproval?.let {
                " Approval ${it.approvalId} (${it.status}) was for a different target and no longer applies."
            }.orEmpty()
            state.copy(
                companion = current.copy(
                    isPollingApproval = if (detachedApproval != null) false else current.isPollingApproval,
                    activeApproval = if (detachedApproval != null) null else current.activeApproval,
                    activeApprovalAction = actionType,
                    activeApprovalTargetPath = cleanPath,
                    pendingWorkOffsetBytes = safeOffset,
                    statusMessage = hint + detachNote,
                    errorMessage = null
                )
            )
        }
    }

    fun clearCompanionWorkState() {
        cancelCompanionPollingJobs()
        _uiState.update { state ->
            state.copy(
                companion = state.companion.copy(
                    isPollingApproval = false,
                    isRunningWorkTask = false,
                    isCancellingWorkTask = false,
                    activeApproval = null,
                    pendingWorkOffsetBytes = null,
                    activeTask = null,
                    statusMessage = "Cleared active PC Work approval/result.",
                    errorMessage = null
                )
            )
        }
    }

    fun insertCompanionResultIntoDraft(): Boolean {
        val task = _uiState.value.companion.activeTask ?: return false
        val formatted = CompanionPathHelper.formatWorkResultForDraft(task) ?: run {
            _uiState.update { state ->
                state.copy(
                    companion = state.companion.copy(
                        errorMessage = "Only completed directory listings or UTF-8 text previews can be inserted into chat."
                    )
                )
            }
            return false
        }
        _uiState.update { state ->
            val nextDraft = if (state.draftText.isBlank()) {
                formatted
            } else {
                state.draftText.trimEnd() + "\n\n" + formatted
            }
            state.copy(
                draftText = nextDraft,
                companion = state.companion.copy(
                    statusMessage = "Inserted PC Work result into chat draft.",
                    errorMessage = null
                )
            )
        }
        return true
    }

    fun requestCompanionApproval(
        actionType: WorkActionType,
        targetPath: String,
        summary: String,
        autoWait: Boolean = false
    ) {
        val current = _uiState.value.companion
        val session = current.session ?: return
        if (current.isRevoking || current.isRequestingApproval || current.isRunningWorkTask) return
        val cleanPath = targetPath.trim()
        if (cleanPath.isEmpty()) {
            _uiState.update {
                it.copy(companion = it.companion.copy(errorMessage = "Target path on Windows PC is required."))
            }
            return
        }
        cancelCompanionPollingJobs()
        val keepOffset = current.pendingWorkOffsetBytes?.takeIf {
            actionType == WorkActionType.READ_FILE_PREVIEW && cleanPath == current.activeApprovalTargetPath
        }
        val idempotencyKey = "apr_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        _uiState.update {
            it.copy(
                companion = it.companion.copy(
                    isRequestingApproval = true,
                    isPollingApproval = false,
                    pendingWorkOffsetBytes = keepOffset,
                    statusMessage = null,
                    errorMessage = null
                )
            )
        }
        viewModelScope.launch {
            val result = companionClient.requestApproval(
                session = session,
                actionType = actionType,
                targetPath = cleanPath,
                summary = summary,
                idempotencyKey = idempotencyKey
            )
            // Revoke Pairing stays enabled while this request is in flight. If the pairing was revoked, cleared or
            // replaced meanwhile, drop the late reply: binding it would resurrect a PENDING approval for an unpaired
            // device, auto-wait would poll for it and leave isPollingApproval stuck true, and a late 401 for the old
            // session must not wipe a newer pairing.
            val companionNow = _uiState.value.companion
            if (companionNow.isRevoking || companionNow.session?.keyId != session.keyId) {
                _uiState.update { it.copy(companion = it.companion.copy(isRequestingApproval = false)) }
                return@launch
            }
            when (result) {
                is CompanionResult.Success -> {
                    val approval = result.value
                    _uiState.update {
                        it.copy(
                            companion = it.companion.copy(
                                isRequestingApproval = false,
                                activeApproval = approval,
                                activeApprovalTargetPath = cleanPath,
                                activeApprovalAction = actionType,
                                activeTask = null,
                                statusMessage = "Approval ${approval.approvalId} requested (${approval.status}). Approve locally on Windows PC.",
                                errorMessage = null
                            )
                        )
                    }
                    if (autoWait && approval.status == "PENDING") {
                        launchApprovalPollingLoop(session, approval.approvalId)
                    }
                }
                is CompanionResult.Failure -> {
                    applyAuthenticatedCompanionFailure(
                        failure = result,
                        transform = { it.copy(isRequestingApproval = false) }
                    )
                }
            }
        }
    }

    /** One idempotency key per card, so a retry after a failure can't create a second request on the PC. */
    private val askPcKeys = mutableMapOf<String, String>()

    /**
     * The user tapped Send on the "Ask your PC" card under assistant message [messageId]. Re-parses that reply,
     * sends it with origin "assistant-proposed" and waits for the Windows owner's decision.
     * Nothing here approves or runs anything: the owner decides on the PC, and the user taps Run afterwards.
     *
     * [newRequest] is "Send again" after the PC let the first request expire; it needs a new idempotency key,
     * because the old one would only return the same expired request.
     */
    fun sendAskPcProposal(messageId: String, newRequest: Boolean = false) {
        val state = _uiState.value
        val current = state.companion
        val session = current.session ?: return
        if (current.isRevoking || current.isRequestingApproval || current.isRunningWorkTask) return
        val previous = current.askPcSends[messageId]
        if (previous?.sending == true) return
        if (previous?.approvalId != null) {
            val expired = current.activeApproval?.let {
                it.approvalId == previous.approvalId && it.status == "EXPIRED"
            } == true
            if (!newRequest || !expired) return
            askPcKeys.remove(messageId)
        }
        val message = state.activeConversation?.messages
            ?.firstOrNull { it.id == messageId && it.role == MessageRole.ASSISTANT } ?: return
        val proposal = AskPcProposals.parse(message.content) ?: return
        cancelCompanionPollingJobs()
        val idempotencyKey = askPcKeys.getOrPut(messageId) {
            "pcp_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        }
        _uiState.update {
            it.copy(
                companion = it.companion.copy(
                    isRequestingApproval = true,
                    isPollingApproval = false,
                    statusMessage = null,
                    errorMessage = null,
                    askPcSends = it.companion.askPcSends + (messageId to AskPcSend(sending = true))
                )
            )
        }
        viewModelScope.launch {
            val result = companionClient.proposeApproval(
                session = session,
                actionType = proposal.actionType,
                targetPath = proposal.targetPath,
                summary = proposal.summary,
                idempotencyKey = idempotencyKey,
                reason = proposal.reason
            )
            // Same late-reply rule as requestCompanionApproval: never bind a reply to a revoked or replaced pairing.
            val companionNow = _uiState.value.companion
            if (companionNow.isRevoking || companionNow.session?.keyId != session.keyId) {
                _uiState.update {
                    it.copy(
                        companion = it.companion.copy(
                            isRequestingApproval = false,
                            askPcSends = it.companion.askPcSends - messageId
                        )
                    )
                }
                return@launch
            }
            when (result) {
                is CompanionResult.Success -> {
                    val approval = result.value
                    _uiState.update {
                        it.copy(
                            companion = it.companion.copy(
                                isRequestingApproval = false,
                                activeApproval = approval,
                                activeApprovalTargetPath = proposal.targetPath,
                                activeApprovalAction = proposal.actionType,
                                pendingWorkOffsetBytes = null,
                                activeTask = null,
                                statusMessage = "Sent to your PC. Approve or deny it there.",
                                errorMessage = null,
                                askPcSends = it.companion.askPcSends + (messageId to AskPcSend(approvalId = approval.approvalId))
                            )
                        )
                    }
                    if (approval.status == "PENDING") {
                        launchApprovalPollingLoop(session, approval.approvalId)
                    }
                }
                is CompanionResult.Failure -> {
                    val calm = AskPcProposals.failureMessage(result)
                    applyAuthenticatedCompanionFailure(
                        failure = if (result.httpStatus == 401) result else result.copy(userMessage = calm),
                        transform = {
                            it.copy(
                                isRequestingApproval = false,
                                askPcSends = it.askPcSends + (messageId to AskPcSend(errorMessage = calm))
                            )
                        }
                    )
                }
            }
        }
    }

    fun refreshCompanionApprovalStatus() {
        val current = _uiState.value.companion
        val session = current.session ?: return
        val approval = current.activeApproval ?: return
        if (current.isRevoking) return
        approvalPollJob?.cancel()
        approvalPollJob = null
        _uiState.update {
            it.copy(companion = it.companion.copy(isPollingApproval = true, errorMessage = null))
        }
        // Track the one-shot check as the approval poll job so Stop Waiting, a new approval request,
        // Clear Work State, task start and revoke all cancel it. Untracked, a late response could
        // resurrect a cleared approval or re-bind a stale APPROVED approval to a newly staged target.
        val job = viewModelScope.launch {
            when (val result = companionClient.getApprovalStatus(session, approval.approvalId)) {
                is CompanionResult.Success -> applyApprovalUpdate(result.value, stillWaiting = false)
                is CompanionResult.Failure -> {
                    applyAuthenticatedCompanionFailure(
                        failure = result,
                        transform = { it.copy(isPollingApproval = false) }
                    )
                }
            }
        }
        approvalPollJob = job
        job.invokeOnCompletion { if (approvalPollJob === job) approvalPollJob = null }
    }

    fun startWaitingForCompanionApproval() {
        val current = _uiState.value.companion
        val session = current.session ?: return
        val approval = current.activeApproval ?: return
        if (current.isRevoking || current.isRequestingApproval || approval.status != "PENDING") return
        launchApprovalPollingLoop(session, approval.approvalId)
    }

    fun stopWaitingForCompanionApproval() {
        val approval = _uiState.value.companion.activeApproval
        approvalPollJob?.cancel()
        approvalPollJob = null
        _uiState.update {
            it.copy(
                companion = it.companion.copy(
                    isPollingApproval = false,
                    statusMessage = approval?.let { apr ->
                        "Stopped waiting for approval ${apr.approvalId} (${apr.status}). Tap Check Once or Wait anytime."
                    },
                    errorMessage = null
                )
            )
        }
    }

    private fun launchApprovalPollingLoop(
        session: com.hydrogen.ai.companion.CompanionSession,
        approvalId: String
    ) {
        approvalPollJob?.cancel()
        _uiState.update {
            it.copy(
                companion = it.companion.copy(
                    isPollingApproval = true,
                    statusMessage = "Waiting for explicit Windows owner approval ($approvalId)...",
                    errorMessage = null
                )
            )
        }
        val job = viewModelScope.launch {
            var transientErrors = 0
            val maxAttempts = companionPollConfig.maxApprovalPollAttempts.coerceAtLeast(1)
            for (attempt in 1..maxAttempts) {
                delay(companionPollConfig.approvalPollIntervalMs)
                if (_uiState.value.companion.session?.keyId != session.keyId) return@launch
                when (val result = companionClient.getApprovalStatus(session, approvalId)) {
                    is CompanionResult.Success -> {
                        transientErrors = 0
                        val updated = result.value
                        val terminal = updated.status != "PENDING"
                        applyApprovalUpdate(updated, stillWaiting = !terminal && attempt < maxAttempts)
                        if (terminal) return@launch
                    }
                    is CompanionResult.Failure -> {
                        if (result.httpStatus == 401 || result.httpStatus == 403 || result.httpStatus == 404) {
                            applyAuthenticatedCompanionFailure(result) { it.copy(isPollingApproval = false) }
                            return@launch
                        }
                        transientErrors += 1
                        if (transientErrors >= 3) {
                            applyAuthenticatedCompanionFailure(result) { it.copy(isPollingApproval = false) }
                            return@launch
                        }
                    }
                }
            }
            _uiState.update {
                it.copy(
                    companion = it.companion.copy(
                        isPollingApproval = false,
                        statusMessage = "Approval $approvalId is still PENDING. Tap Wait or Check Once to continue checking."
                    )
                )
            }
        }
        approvalPollJob = job
        job.invokeOnCompletion { if (approvalPollJob === job) approvalPollJob = null }
    }

    private fun applyApprovalUpdate(
        updated: com.hydrogen.ai.companion.CompanionApproval,
        stillWaiting: Boolean
    ) {
        val statusMsg = when (updated.status) {
            "APPROVED" -> "Approval ${updated.approvalId} APPROVED on Windows PC. Ready to run."
            "PENDING" -> if (stillWaiting) "Waiting for explicit Windows owner approval (${updated.approvalId})..." else "Approval ${updated.approvalId}: PENDING."
            "DENIED", "EXPIRED", "REVOKED" -> null
            else -> "Approval ${updated.approvalId}: ${updated.status}."
        }
        val errorMsg = if (updated.status in setOf("DENIED", "EXPIRED", "REVOKED")) {
            "Approval ${updated.approvalId} was ${updated.status} on Windows PC. Request a new approval if needed."
        } else {
            null
        }
        _uiState.update {
            it.copy(
                companion = it.companion.copy(
                    isPollingApproval = stillWaiting,
                    activeApproval = updated,
                    statusMessage = statusMsg,
                    errorMessage = errorMsg
                )
            )
        }
    }

    fun startCompanionWorkTask(
        maxBytes: Int? = null,
        offsetBytes: Int? = null,
        maxEntries: Int? = null
    ) {
        val current = _uiState.value.companion
        val session = current.session ?: return
        val approval = current.activeApproval ?: return
        if (current.isRevoking || current.isRunningWorkTask) return
        if (approval.status != "APPROVED") {
            _uiState.update {
                it.copy(
                    companion = it.companion.copy(
                        errorMessage = "Explicit Windows owner approval is required (current state: ${approval.status})."
                    )
                )
            }
            return
        }
        val targetPath = current.activeApprovalTargetPath.trim()
        if (targetPath.isEmpty()) {
            _uiState.update {
                it.copy(companion = it.companion.copy(errorMessage = "Target path is missing for approved request."))
            }
            return
        }
        cancelCompanionPollingJobs()
        val actionType = current.activeApprovalAction
        val effectiveOffsetBytes = offsetBytes ?: current.pendingWorkOffsetBytes?.takeIf {
            actionType == WorkActionType.READ_FILE_PREVIEW
        }
        val idempotencyKey = "tsk_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        _uiState.update {
            it.copy(
                companion = it.companion.copy(
                    isPollingApproval = false,
                    isRunningWorkTask = true,
                    statusMessage = "Starting approved ${actionType.displayName} task...",
                    errorMessage = null
                )
            )
        }
        val job = viewModelScope.launch {
            when (
                val started = companionClient.startWorkTask(
                    session = session,
                    approvalId = approval.approvalId,
                    actionType = actionType,
                    targetPath = targetPath,
                    idempotencyKey = idempotencyKey,
                    maxBytes = maxBytes,
                    offsetBytes = effectiveOffsetBytes,
                    maxEntries = maxEntries
                )
            ) {
                is CompanionResult.Success -> {
                    val task = started.value.task
                    val isActiveInitial = task.status == "QUEUED" || task.status == "RUNNING"
                    _uiState.update {
                        it.copy(
                            companion = it.companion.copy(
                                isRunningWorkTask = isActiveInitial,
                                activeApproval = approval.copy(status = "CONSUMED"),
                                pendingWorkOffsetBytes = null,
                                activeTask = task,
                                statusMessage = "Work task ${task.taskId}: ${task.status} (${task.progressPercent}%).",
                                errorMessage = task.error
                            )
                        )
                    }
                    if (isActiveInitial) {
                        pollWorkTaskUntilTerminal(session, task.taskId)
                    }
                }
                is CompanionResult.Failure -> {
                    applyAuthenticatedCompanionFailure(
                        failure = started,
                        transform = { comp ->
                            val updatedApproval = when {
                                started.httpStatus == 403 && started.code == "approval_expired" ->
                                    comp.activeApproval?.copy(status = "EXPIRED")
                                started.httpStatus == 403 && started.code == "approval_not_granted" -> {
                                    val upper = started.userMessage.uppercase()
                                    val inferredStatus = when {
                                        "DENIED" in upper -> "DENIED"
                                        "EXPIRED" in upper -> "EXPIRED"
                                        "CONSUMED" in upper -> "CONSUMED"
                                        else -> "REVOKED"
                                    }
                                    comp.activeApproval?.copy(status = inferredStatus)
                                }
                                else -> comp.activeApproval
                            }
                            comp.copy(
                                isRunningWorkTask = false,
                                activeApproval = updatedApproval
                            )
                        }
                    )
                }
            }
        }
        workTaskPollJob = job
        job.invokeOnCompletion {
            if (workTaskPollJob === job) {
                workTaskPollJob = null
            }
        }
    }

    private suspend fun pollWorkTaskUntilTerminal(
        session: com.hydrogen.ai.companion.CompanionSession,
        taskId: String
    ) {
        var transientErrors = 0
        val maxAttempts = companionPollConfig.maxWorkTaskPollAttempts.coerceAtLeast(1)
        for (attempt in 1..maxAttempts) {
            delay(companionPollConfig.workTaskPollIntervalMs)
            if (_uiState.value.companion.session?.keyId != session.keyId) return
            when (val polled = companionClient.getWorkTaskStatus(session, taskId)) {
                is CompanionResult.Success -> {
                    transientErrors = 0
                    val updated = polled.value.task
                    val stillActive = updated.status == "QUEUED" || updated.status == "RUNNING"
                    _uiState.update {
                        it.copy(
                            companion = it.companion.copy(
                                isRunningWorkTask = stillActive && attempt < maxAttempts,
                                activeTask = updated,
                                statusMessage = "Work task ${updated.taskId}: ${updated.status} (${updated.progressPercent}%).",
                                errorMessage = updated.error
                            )
                        )
                    }
                    if (!stillActive) return
                }
                is CompanionResult.Failure -> {
                    if (polled.httpStatus == 401 || polled.httpStatus == 403 || polled.httpStatus == 404) {
                        applyAuthenticatedCompanionFailure(
                            failure = polled,
                            transform = { it.copy(isRunningWorkTask = false) }
                        )
                        return
                    }
                    transientErrors += 1
                    if (transientErrors >= 3) {
                        applyAuthenticatedCompanionFailure(
                            failure = polled,
                            transform = { it.copy(isRunningWorkTask = false) }
                        )
                        return
                    }
                }
            }
        }
        _uiState.update {
            it.copy(
                companion = it.companion.copy(
                    isRunningWorkTask = false,
                    statusMessage = "Work task $taskId is still active; tap Refresh Task or Cancel Task."
                )
            )
        }
    }

    fun refreshCompanionWorkTaskStatus() {
        val current = _uiState.value.companion
        val session = current.session ?: return
        val task = current.activeTask ?: return
        if (current.isRevoking) return
        workTaskPollJob?.cancel()
        workTaskPollJob = null
        _uiState.update {
            it.copy(companion = it.companion.copy(isRunningWorkTask = true, errorMessage = null))
        }
        // Track the one-shot refresh as the work task poll job so revoke, Clear Work State, Cancel Task and
        // local session clear all cancel it. Untracked, a late response could resurrect a revoked/cleared
        // PC file preview or overwrite a CANCELLED task with a stale pre-cancel snapshot.
        val job = viewModelScope.launch {
            when (val result = companionClient.getWorkTaskStatus(session, task.taskId)) {
                is CompanionResult.Success -> {
                    val updated = result.value.task
                    _uiState.update {
                        it.copy(
                            companion = it.companion.copy(
                                isRunningWorkTask = false,
                                activeTask = updated,
                                statusMessage = "Work task ${updated.taskId}: ${updated.status} (${updated.progressPercent}%).",
                                errorMessage = updated.error
                            )
                        )
                    }
                }
                is CompanionResult.Failure -> {
                    applyAuthenticatedCompanionFailure(
                        failure = result,
                        transform = { it.copy(isRunningWorkTask = false) }
                    )
                }
            }
        }
        workTaskPollJob = job
        job.invokeOnCompletion { if (workTaskPollJob === job) workTaskPollJob = null }
    }

    fun cancelCompanionWorkTask() {
        val current = _uiState.value.companion
        val session = current.session ?: return
        val task = current.activeTask ?: return
        if (current.isRevoking || current.isCancellingWorkTask) return
        workTaskPollJob?.cancel()
        workTaskPollJob = null
        _uiState.update {
            it.copy(
                companion = it.companion.copy(
                    isRunningWorkTask = false,
                    isCancellingWorkTask = true,
                    errorMessage = null
                )
            )
        }
        viewModelScope.launch {
            when (val result = companionClient.cancelWorkTask(session, task.taskId)) {
                is CompanionResult.Success -> {
                    val cancelled = result.value.task
                    _uiState.update {
                        it.copy(
                            companion = it.companion.copy(
                                isCancellingWorkTask = false,
                                activeTask = cancelled,
                                statusMessage = "Work task ${cancelled.taskId}: ${cancelled.status}.",
                                errorMessage = cancelled.error
                            )
                        )
                    }
                }
                is CompanionResult.Failure -> {
                    applyAuthenticatedCompanionFailure(
                        failure = result,
                        transform = { it.copy(isCancellingWorkTask = false) }
                    )
                }
            }
        }
    }

    private fun applyAuthenticatedCompanionFailure(
        failure: CompanionResult.Failure,
        transform: (CompanionUiState) -> CompanionUiState
    ) {
        if (failure.httpStatus == 401) {
            cancelCompanionPollingJobs()
            companionSessionStore.clearSession()
            _uiState.update { state ->
                val cleared = transform(state.companion).copy(
                    session = null,
                    isPollingApproval = false,
                    isRunningWorkTask = false,
                    isCancellingWorkTask = false,
                    activeApproval = null,
                    activeApprovalTargetPath = "",
                    pendingWorkOffsetBytes = null,
                    activeTask = null,
                    statusMessage = null,
                    errorMessage = "PC companion rejected or revoked this pairing (${failure.code}: ${failure.userMessage}); local session cleared. Pair again from PC."
                )
                state.copy(
                    companion = cleared,
                    readiness = state.readiness.copy(workMode = computeWorkMode(cleared))
                )
            }
        } else if (failure.httpStatus == 404 && failure.code == "approval_not_found") {
            _uiState.update { state ->
                state.copy(
                    companion = transform(state.companion).copy(
                        activeApproval = null,
                        statusMessage = null,
                        errorMessage = failure.userMessage
                    )
                )
            }
        } else {
            _uiState.update { state ->
                state.copy(
                    companion = transform(state.companion).copy(errorMessage = failure.userMessage)
                )
            }
        }
    }

    private fun computeWorkMode(companion: CompanionUiState): VerificationState = when {
        !companion.isPaired -> VerificationState.UNAVAILABLE
        companion.health == null -> VerificationState.UNKNOWN
        companion.health.workEnabled -> VerificationState.PASS
        else -> VerificationState.FAIL
    }

    private fun normalizeCompanionConfig(baseUrl: String, deviceName: String): CompanionConfig =
        CompanionConfig(
            // "192.168.1.23" (Wi-Fi mode) becomes "http://192.168.1.23:18812"; see docs/WIFI_MODE_SPEC.md.
            baseUrl = CompanionUrlSafety.normalize(baseUrl).ifBlank { CompanionDefaults.defaultUrl() },
            deviceName = deviceName.trim().ifBlank { CompanionDefaults.DEFAULT_DEVICE_NAME }.take(64)
        )

    fun sendMessage() {
        val state = _uiState.value
        if (state.isSending) return
        val trimmedDraft = state.draftText.trim()
        val attachments = state.stagedAttachments
        if (trimmedDraft.isEmpty() && attachments.isEmpty()) return

        // "Remember that …" saves a memory note on this device; nothing is sent to the provider. A temporary chat
        // never writes memory, so there it goes to the model as an ordinary message.
        val rememberText = if (attachments.isEmpty() && state.editingMessageId == null && !state.isTemporaryChat) {
            UserMemory.rememberCommand(trimmedDraft)
        } else {
            null
        }
        if (rememberText != null) {
            val cleaned = UserMemory.clean(rememberText)
            val saved = addMemory(rememberText)
            val notice = when {
                saved && state.memoryEnabled -> "Saved to memory: $cleaned"
                saved -> "Saved to memory (memory is off, so chats won't use it yet): $cleaned"
                else -> "Already in memory: $cleaned"
            }
            _uiState.update { it.copy(draftText = "", notice = notice, preflightWarnings = emptyList()) }
            return
        }

        // Writing in an archived chat brings it back to the list, as in other chat apps.
        val active = (state.activeConversation ?: newBlankConversation(clock())).let {
            if (it.archived) it.copy(archived = false) else it
        }
        // Edit and resend: the edited message becomes a new version of its turn. What was there (the old message and
        // everything after it) stays as an earlier version behind the "< 1 / 2 >" arrows, files and all.
        val editIndex = state.editingMessageId
            ?.let { id -> active.messages.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
        if (state.editingMessageId != null) {
            _uiState.update { it.copy(editingMessageId = null) }
            // Restore the original draft below, once the edited turn is staged.
        }
        val now = clock()
        val correlationId = UUID.randomUUID().toString()
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            correlationId = correlationId,
            role = MessageRole.USER,
            content = trimmedDraft,
            createdAtEpochMs = now,
            modelId = state.relayConfig.modelId,
            status = MessageStatus.COMPLETED,
            attachments = attachments
        )

        val historyWithUser = if (editIndex != null) {
            MessageVersions.addVersion(active.messages, editIndex, userMessage)
        } else {
            active.messages + userMessage
        }
        val preflight = PreflightValidator.assess(historyWithUser, state.relayConfig)
        val markedHistoryWithUser = applyOmittedAttachmentMarks(historyWithUser, preflight)

        if (preflight.blockingError != null) {
            val blockedReply = ChatMessage(
                id = UUID.randomUUID().toString(),
                correlationId = correlationId,
                role = MessageRole.ASSISTANT,
                content = "",
                createdAtEpochMs = now + 1,
                modelId = state.relayConfig.modelId,
                status = MessageStatus.FAILED,
                requestProfile = com.hydrogen.ai.model.ReplyRequestProfile.from(normalizeReasoning(state.relayConfig)),
                error = preflight.blockingError
            )
            val updatedConversation = active.copy(
                title = deriveTitle(active, trimmedDraft),
                updatedAtEpochMs = now,
                modelId = state.relayConfig.modelId,
                route = state.relayConfig.route,
                messages = markedHistoryWithUser + blockedReply
            )
            val updatedList = upsertConversation(state.conversations, updatedConversation)
            _uiState.update {
                it.copy(
                    conversations = updatedList,
                    activeConversationId = updatedConversation.id,
                    draftText = state.draftBeforeEdit?.text.orEmpty(),
                    stagedAttachments = state.draftBeforeEdit?.attachments.orEmpty(),
                    draftBeforeEdit = null,
                    preflightWarnings = preflight.warnings
                )
            }
            viewModelScope.launch {
                conversationStore.saveConversations(updatedList)
            }
            return
        }

        val pendingAssistant = ChatMessage(
            id = UUID.randomUUID().toString(),
            correlationId = correlationId,
            role = MessageRole.ASSISTANT,
            content = "",
            createdAtEpochMs = now + 1,
            modelId = state.relayConfig.modelId,
            status = MessageStatus.DISPATCHED,
            requestProfile = com.hydrogen.ai.model.ReplyRequestProfile.from(normalizeReasoning(state.relayConfig))
        )

        val stagedConversation = active.copy(
            title = deriveTitle(active, trimmedDraft),
            updatedAtEpochMs = now,
            // The chat's model is the one that answers this message, not the default it was created with.
            modelId = state.relayConfig.modelId,
            route = state.relayConfig.route,
            messages = markedHistoryWithUser + pendingAssistant
        )
        val stagedList = upsertConversation(state.conversations, stagedConversation)

        _uiState.update {
            it.copy(
                isSending = true,
                conversations = stagedList,
                activeConversationId = stagedConversation.id,
                draftText = state.draftBeforeEdit?.text.orEmpty(),
                stagedAttachments = state.draftBeforeEdit?.attachments.orEmpty(),
                draftBeforeEdit = null,
                preflightWarnings = preflight.warnings
            )
        }

        activeSendTarget = stagedConversation.id to pendingAssistant.id
        activeSendJob = viewModelScope.launch {
            try {
                conversationStore.saveConversations(stagedList)
                val dispatchMessages = withInstructions(
                    fitToContextWindow(
                        com.hydrogen.ai.model.CompareModelsPolicy.context(markedHistoryWithUser).filter {
                            it.role == MessageRole.SYSTEM || it.role == MessageRole.USER ||
                                (it.role == MessageRole.ASSISTANT &&
                                    it.status == MessageStatus.COMPLETED &&
                                    (it.content.isNotBlank() || it.turnMetadata?.toolCalls?.isNotEmpty() == true))
                        },
                        stagedConversation,
                        preflight.omittedAttachmentIds,
                        state.relayConfig.route
                    ),
                    stagedConversation
                )
                val result = sendWithKeyRelay(
                    request = ChatDispatchRequest(
                        correlationId = correlationId,
                        modelId = state.relayConfig.modelId,
                        messages = dispatchMessages,
                        attachments = preflight.includedImages,
                        omittedAttachmentIds = preflight.omittedAttachmentIds,
                        stream = state.relayConfig.streamingEnabled
                    ),
                    config = state.relayConfig,
                    onStreamDelta = { delta ->
                        applyStreamDelta(
                            conversationId = stagedConversation.id,
                            assistantMessageId = pendingAssistant.id,
                            delta = delta
                        )
                    }
                )
                applyDispatchResult(
                    conversationId = stagedConversation.id,
                    assistantMessageId = pendingAssistant.id,
                    dispatchConfig = state.relayConfig,
                    result = result,
                    hadAttachments = attachments.isNotEmpty()
                )
            } catch (_: CancellationException) {
                withContext(NonCancellable) {
                    markMessageCancelled(stagedConversation.id, pendingAssistant.id)
                }
            } catch (e: Exception) {
                withContext(NonCancellable) {
                    applyDispatchResult(
                        conversationId = stagedConversation.id,
                        assistantMessageId = pendingAssistant.id,
                        dispatchConfig = state.relayConfig,
                        result = ChatDispatchResult.Failure(
                            ProviderError(
                                category = ProviderErrorCategory.UNKNOWN_PROTOCOL,
                                userMessage = CLIENT_FAILURE_MESSAGE,
                                technicalDetail = "${e::class.java.simpleName}: ${e.message.orEmpty()}",
                                sourceLayer = "client"
                            )
                        ),
                        hadAttachments = attachments.isNotEmpty()
                    )
                }
            } finally {
                if (activeSendTarget == (stagedConversation.id to pendingAssistant.id)) {
                    activeSendJob = null
                    activeSendTarget = null
                }
            }
        }
    }

    fun cancelActiveSend() {
        val job = activeSendJob
        val target = activeSendTarget
        activeSendJob = null
        activeSendTarget = null
        val state = _uiState.value
        val resolvedTarget = target ?: state.activeConversation?.let { active ->
            active.messages.lastOrNull {
                it.role == MessageRole.ASSISTANT &&
                    (it.status == MessageStatus.DISPATCHED || it.status == MessageStatus.STREAMING)
            }?.let { active.id to it.id }
        }
        if (resolvedTarget != null) {
            viewModelScope.launch {
                markMessageCancelled(resolvedTarget.first, resolvedTarget.second)
            }
        } else {
            _uiState.update { it.copy(isSending = false) }
        }
        job?.cancel()
    }

    private fun backupLabels(config: RelayConfig): List<String> =
        runCatching { credentialStore.getBackupTokens(config) }.getOrDefault(emptyList()).map(::maskSecretToken)

    /**
     * Sends with the service's main key. If that key hits a limit, runs out of credits or is turned down before any
     * part of the reply arrives, the very same request (same messages, same context) goes out again with the next
     * backup key saved for this service. Keys are only ever tried on the service they were saved for. The key that
     * worked becomes the main key, and the ones that failed move to the end of the list.
     */
    private suspend fun sendWithKeyRelay(
        request: ChatDispatchRequest,
        config: RelayConfig,
        onStreamDelta: suspend (StreamDelta) -> Unit
    ): ChatDispatchResult {
        val main = credentialStore.getToken(config)
        val backups = if (main.isNullOrBlank()) emptyList() else credentialStore.getBackupTokens(config)
        val keys: List<String?> = listOf(main) + backups
        var index = 0
        while (true) {
            var streamed = false
            val result = backend.sendChat(request, config, keys[index]) { delta ->
                streamed = true
                onStreamDelta(delta)
            }
            val tryNext = result is ChatDispatchResult.Failure && !streamed &&
                index < keys.lastIndex && result.error.category in KEY_RELAY_CATEGORIES
            if (tryNext) {
                index++
                continue
            }
            if (index == 0) return result
            if (result is ChatDispatchResult.Success) {
                val order = keys.drop(index) + keys.take(index)
                credentialStore.setToken(config, order.first())
                credentialStore.setBackupTokens(config, order.drop(1).filterNotNull())
                _uiState.update {
                    if (it.relayConfig != config) it else it.copy(
                        maskedTokenLabel = maskSecretToken(order.first()),
                        backupKeyLabels = backupLabels(config),
                        notice = "Your main key for ${currentServiceName(config) ?: "this service"} hit a limit or " +
                            "stopped working, so Orbit used backup key $index. It is now your main key."
                    )
                }
                return result
            }
            if (result is ChatDispatchResult.Failure && index == keys.lastIndex &&
                result.error.category in KEY_RELAY_CATEGORIES
            ) {
                return result.copy(
                    error = result.error.copy(
                        userMessage = result.error.userMessage +
                            " Orbit also tried your ${keys.size - 1} backup ${if (keys.size == 2) "key" else "keys"}."
                    )
                )
            }
            return result
        }
    }

    /** Drops the oldest turns when a long chat would overflow the model's context window. */
    private fun fitToContextWindow(
        history: List<ChatMessage>,
        conversation: Conversation,
        omittedAttachmentIds: Collection<String>,
        route: ProviderRoute
    ): List<ChatMessage> = com.hydrogen.ai.model.ContextWindowTrimmer.trim(
        messages = history,
        // Budget for the whole system message, PC hint included, since withInstructions() sends all of it.
        systemPrompt = AskPcProposals.withSystemHint(instructionsFor(conversation), askPcHintEnabled(_uiState.value)),
        omittedAttachmentIds = omittedAttachmentIds,
        budgetChars = com.hydrogen.ai.model.ContextWindowTrimmer.budgetFor(route)
    )

    fun retryMessage(messageId: String) {
        val state = _uiState.value
        if (state.isSending) return
        val active = state.activeConversation ?: return
        val targetIndex = active.messages.indexOfFirst { it.id == messageId }
        if (targetIndex < 0) return
        val failedMessage = active.messages[targetIndex]
        val mode = RegeneratePolicy.modeFor(active.messages, messageId) ?: return
        if (failedMessage.comparison) {
            // A compared answer is asked again of its own model, not the chat's current one.
            val without = active.copy(messages = active.messages.filterNot { it.id == messageId })
            if (!com.hydrogen.ai.model.CompareModelsPolicy.canCompare(without.messages)) return
            _uiState.update { it.copy(conversations = upsertConversation(it.conversations, without)) }
            compareWith(failedMessage.modelId)
            return
        }

        val now = clock()
        val regenerating = mode == RegeneratePolicy.Mode.REGENERATE
        val preservePartial = !regenerating && targetIndex == active.messages.lastIndex && failedMessage.role == MessageRole.ASSISTANT &&
            failedMessage.content.isNotBlank() && failedMessage.turnMetadata?.toolCalls.isNullOrEmpty()
        val profile = com.hydrogen.ai.model.ReplyRequestProfile.from(normalizeReasoning(state.relayConfig))
        val changedTarget = failedMessage.requestProfile?.let { !it.sameRequestTarget(profile) } ?: false
        val retryingAssistant = failedMessage.copy(
            // A regenerated reply is a new version beside the old one, so it gets its own id and time. It also needs a
            // fresh idempotency key, otherwise the relay returns the stored reply. A retry of a failed reply reuses all three.
            id = if (regenerating || preservePartial) UUID.randomUUID().toString() else failedMessage.id,
            correlationId = if (regenerating || changedTarget) UUID.randomUUID().toString() else failedMessage.correlationId,
            modelId = state.relayConfig.modelId,
            requestProfile = profile,
            createdAtEpochMs = if (regenerating) now else failedMessage.createdAtEpochMs,
            status = MessageStatus.DISPATCHED,
            content = "",
            turnMetadata = null,
            error = null,
            thinkingMs = null
        )
        val historyBeforeFailed = active.messages.take(targetIndex)
        val preflight = PreflightValidator.assess(historyBeforeFailed, state.relayConfig)
        val markedHistoryBeforeFailed = applyOmittedAttachmentMarks(historyBeforeFailed, preflight)
        if (preflight.blockingError != null) {
            val blocked = failedMessage.copy(error = preflight.blockingError)
            val blockedMessages = (markedHistoryBeforeFailed + blocked + active.messages.drop(targetIndex + 1))
            val updatedConversation = active.copy(updatedAtEpochMs = now, messages = blockedMessages)
            val updatedList = upsertConversation(state.conversations, updatedConversation)
            _uiState.update { it.copy(conversations = updatedList, preflightWarnings = preflight.warnings) }
            viewModelScope.launch { conversationStore.saveConversations(updatedList) }
            return
        }

        val updatedMessages = if (regenerating || preservePartial) {
            // The old answer stays as an earlier version of this turn, behind the "< 1 / 2 >" arrows.
            MessageVersions.addVersion(markedHistoryBeforeFailed + active.messages.drop(targetIndex), targetIndex, retryingAssistant)
        } else {
            markedHistoryBeforeFailed + retryingAssistant + active.messages.drop(targetIndex + 1)
        }
        val stagedConversation = active.copy(updatedAtEpochMs = now, messages = updatedMessages,
            modelId = state.relayConfig.modelId, route = state.relayConfig.route)
        val stagedList = upsertConversation(state.conversations, stagedConversation)

        _uiState.update {
            it.copy(
                isSending = true,
                conversations = stagedList,
                preflightWarnings = preflight.warnings
            )
        }

        activeSendTarget = stagedConversation.id to retryingAssistant.id
        activeSendJob = viewModelScope.launch {
            try {
                conversationStore.saveConversations(stagedList)
                val dispatchMessages = withInstructions(
                    fitToContextWindow(
                        com.hydrogen.ai.model.CompareModelsPolicy.context(markedHistoryBeforeFailed).filter {
                            it.role == MessageRole.SYSTEM || it.role == MessageRole.USER ||
                                (it.role == MessageRole.ASSISTANT &&
                                    it.status == MessageStatus.COMPLETED &&
                                    (it.content.isNotBlank() || it.turnMetadata?.toolCalls?.isNotEmpty() == true))
                        },
                        stagedConversation,
                        preflight.omittedAttachmentIds,
                        state.relayConfig.route
                    ),
                    stagedConversation
                )
                val result = sendWithKeyRelay(
                    request = ChatDispatchRequest(
                        correlationId = retryingAssistant.correlationId,
                        modelId = state.relayConfig.modelId,
                        messages = dispatchMessages,
                        attachments = preflight.includedImages,
                        omittedAttachmentIds = preflight.omittedAttachmentIds,
                        stream = state.relayConfig.streamingEnabled
                    ),
                    config = state.relayConfig,
                    onStreamDelta = { delta ->
                        applyStreamDelta(
                            conversationId = stagedConversation.id,
                            assistantMessageId = retryingAssistant.id,
                            delta = delta
                        )
                    }
                )
                applyDispatchResult(
                    conversationId = stagedConversation.id,
                    assistantMessageId = retryingAssistant.id,
                    dispatchConfig = state.relayConfig,
                    result = result,
                    hadAttachments = historyBeforeFailed.lastOrNull()?.attachments?.isNotEmpty() == true
                )
            } catch (_: CancellationException) {
                withContext(NonCancellable) {
                    markMessageCancelled(stagedConversation.id, retryingAssistant.id)
                }
            } catch (e: Exception) {
                withContext(NonCancellable) {
                    applyDispatchResult(
                        conversationId = stagedConversation.id,
                        assistantMessageId = retryingAssistant.id,
                        dispatchConfig = state.relayConfig,
                        result = ChatDispatchResult.Failure(
                            ProviderError(
                                category = ProviderErrorCategory.UNKNOWN_PROTOCOL,
                                userMessage = CLIENT_FAILURE_MESSAGE,
                                technicalDetail = "${e::class.java.simpleName}: ${e.message.orEmpty()}",
                                sourceLayer = "client"
                            )
                        ),
                        hadAttachments = historyBeforeFailed.lastOrNull()?.attachments?.isNotEmpty() == true
                    )
                }
            } finally {
                if (activeSendTarget == (stagedConversation.id to retryingAssistant.id)) {
                    activeSendJob = null
                    activeSendTarget = null
                }
            }
        }
    }

    /**
     * Asks [modelId] the latest question again and adds its answer after the existing ones, marked as a
     * comparison (shown, never sent as context). Uses the same provider and key as the chat.
     */
    fun compareWith(modelId: String) {
        val model = modelId.trim()
        val state = _uiState.value
        if (model.isEmpty() || state.isSending) return
        val active = state.activeConversation ?: return
        if (!com.hydrogen.ai.model.CompareModelsPolicy.canCompare(active.messages)) return
        val question = com.hydrogen.ai.model.CompareModelsPolicy.promptHistory(active.messages)
        val config = normalizeReasoning(state.relayConfig.copy(modelId = model))
        val now = clock()
        val preflight = PreflightValidator.assess(question, config)
        val answer = ChatMessage(
            id = UUID.randomUUID().toString(),
            correlationId = UUID.randomUUID().toString(),
            role = MessageRole.ASSISTANT,
            content = "",
            createdAtEpochMs = now,
            modelId = model,
            status = if (preflight.blockingError != null) MessageStatus.FAILED else MessageStatus.DISPATCHED,
            error = preflight.blockingError,
            comparison = true,
            requestProfile = com.hydrogen.ai.model.ReplyRequestProfile.from(config)
        )
        val stagedConversation = active.copy(updatedAtEpochMs = now, messages = active.messages + answer)
        val stagedList = upsertConversation(state.conversations, stagedConversation)
        if (preflight.blockingError != null) {
            _uiState.update { it.copy(conversations = stagedList, preflightWarnings = preflight.warnings) }
            viewModelScope.launch { conversationStore.saveConversations(stagedList) }
            return
        }
        _uiState.update { it.copy(isSending = true, conversations = stagedList, preflightWarnings = preflight.warnings) }
        val markedQuestion = applyOmittedAttachmentMarks(question, preflight)
        val hadAttachments = question.lastOrNull()?.attachments?.isNotEmpty() == true

        activeSendTarget = stagedConversation.id to answer.id
        activeSendJob = viewModelScope.launch {
            try {
                conversationStore.saveConversations(stagedList)
                val dispatchMessages = withInstructions(
                    fitToContextWindow(
                        markedQuestion.filter {
                            it.role == MessageRole.SYSTEM || it.role == MessageRole.USER ||
                                (it.role == MessageRole.ASSISTANT &&
                                    it.status == MessageStatus.COMPLETED &&
                                    (it.content.isNotBlank() || it.turnMetadata?.toolCalls?.isNotEmpty() == true))
                        },
                        stagedConversation,
                        preflight.omittedAttachmentIds,
                        config.route
                    ),
                    stagedConversation
                )
                val result = sendWithKeyRelay(
                    request = ChatDispatchRequest(
                        correlationId = answer.correlationId,
                        modelId = model,
                        messages = dispatchMessages,
                        attachments = preflight.includedImages,
                        omittedAttachmentIds = preflight.omittedAttachmentIds,
                        stream = config.streamingEnabled
                    ),
                    config = config,
                    onStreamDelta = { delta ->
                        applyStreamDelta(conversationId = stagedConversation.id, assistantMessageId = answer.id, delta = delta)
                    }
                )
                applyDispatchResult(
                    conversationId = stagedConversation.id,
                    assistantMessageId = answer.id,
                    dispatchConfig = config,
                    result = result,
                    hadAttachments = hadAttachments
                )
            } catch (_: CancellationException) {
                withContext(NonCancellable) { markMessageCancelled(stagedConversation.id, answer.id) }
            } catch (e: Exception) {
                withContext(NonCancellable) {
                    applyDispatchResult(
                        conversationId = stagedConversation.id,
                        assistantMessageId = answer.id,
                        dispatchConfig = config,
                        result = ChatDispatchResult.Failure(
                            ProviderError(
                                category = ProviderErrorCategory.UNKNOWN_PROTOCOL,
                                userMessage = CLIENT_FAILURE_MESSAGE,
                                technicalDetail = "${e::class.java.simpleName}: ${e.message.orEmpty()}",
                                sourceLayer = "client"
                            )
                        ),
                        hadAttachments = hadAttachments
                    )
                }
            } finally {
                if (activeSendTarget == (stagedConversation.id to answer.id)) {
                    activeSendJob = null
                    activeSendTarget = null
                }
            }
        }
    }

    private fun applyStreamDelta(
        conversationId: String,
        assistantMessageId: String,
        delta: StreamDelta
    ) {
        if (delta.textDelta.isEmpty() &&
            delta.thinkingDelta.isEmpty() &&
            delta.thoughtSignature.isNullOrBlank() &&
            delta.toolCalls.isEmpty() &&
            delta.finishReason.isNullOrBlank()
        ) {
            return
        }
        val now = clock()
        if (delta.thinkingDelta.isNotBlank()) thinkingStartedAt.putIfAbsent(assistantMessageId, now)
        val thoughtSince = thinkingStartedAt[assistantMessageId]
        _uiState.update { state ->
            val conversation = state.conversations.firstOrNull { it.id == conversationId } ?: return@update state
            var modified = false
            val updatedMessages = conversation.messages.map { msg ->
                if (msg.id != assistantMessageId) {
                    msg
                } else if (msg.status != MessageStatus.DISPATCHED && msg.status != MessageStatus.STREAMING) {
                    msg
                } else {
                    modified = true
                    msg.copy(
                        status = MessageStatus.STREAMING,
                        content = msg.content + delta.textDelta,
                        turnMetadata = mergeTurnMetadata(msg.turnMetadata, delta),
                        // The thinking ends with the answer's first word.
                        thinkingMs = msg.thinkingMs ?: if (thoughtSince != null && delta.textDelta.isNotBlank()) {
                            (now - thoughtSince).coerceAtLeast(0)
                        } else {
                            null
                        }
                    )
                }
            }
            if (!modified) return@update state
            val updatedConversation = conversation.copy(
                updatedAtEpochMs = now,
                messages = updatedMessages
            )
            state.copy(conversations = upsertConversation(state.conversations, updatedConversation))
        }
    }

    private suspend fun markMessageCancelled(
        conversationId: String,
        assistantMessageId: String
    ) {
        val now = clock()
        thinkingStartedAt.remove(assistantMessageId)
        var changed = false
        _uiState.update { state ->
            changed = false
            val conversation = state.conversations.firstOrNull { it.id == conversationId } ?: return@update state
            val target = conversation.messages.firstOrNull { it.id == assistantMessageId } ?: return@update state
            if (target.status !in setOf(MessageStatus.DISPATCHED, MessageStatus.STREAMING)) return@update state
            val updated = conversation.copy(updatedAtEpochMs = now, messages = conversation.messages.map {
                if (it.id == assistantMessageId) it.copy(status = MessageStatus.CANCELLED) else it
            })
            changed = true
            val newerRequest = activeSendTarget != null && activeSendTarget != (conversationId to assistantMessageId)
            state.copy(
                conversations = upsertConversation(state.conversations, updated),
                isSending = newerRequest,
                readiness = if (!newerRequest && state.activeConversationId == conversationId) state.readiness.copy(
                    lastVerifiedAtEpochMs = now, statusDetail = "Stopped. Anything already written is kept."
                ) else state.readiness
            )
        }
        if (changed) conversationStore.saveConversations(_uiState.value.conversations)
    }

    private suspend fun applyDispatchResult(
        conversationId: String,
        assistantMessageId: String,
        dispatchConfig: RelayConfig,
        result: ChatDispatchResult,
        hadAttachments: Boolean
    ) {
        val now = clock()
        val thoughtSince = thinkingStartedAt.remove(assistantMessageId)
        var completedConversation: Conversation? = null
        var completedModelId: String? = null
        _uiState.update { currentState ->
            completedConversation = null
            completedModelId = null
            val conversation = currentState.conversations.firstOrNull { it.id == conversationId } ?: return@update currentState
            val target = conversation.messages.firstOrNull { it.id == assistantMessageId } ?: return@update currentState
            if (target.status !in setOf(MessageStatus.DISPATCHED, MessageStatus.STREAMING)) return@update currentState
            val updatedMessages = conversation.messages.map { msg ->
                if (msg.id != assistantMessageId) {
                    msg
                } else {
                    when (result) {
                        is ChatDispatchResult.Success -> msg.copy(
                            status = MessageStatus.COMPLETED,
                            content = result.assistantText,
                            modelId = result.modelId,
                            turnMetadata = result.turnMetadata,
                            error = null,
                            // A reply that thought but wrote no words (only tool calls) thought until the end.
                            thinkingMs = if (result.turnMetadata?.thinkingSummary.isNullOrBlank()) {
                                null
                            } else {
                                msg.thinkingMs ?: thoughtSince?.let { (now - it).coerceAtLeast(0) }
                            }
                        )
                        is ChatDispatchResult.Failure -> msg.copy(
                            status = MessageStatus.FAILED,
                            content = msg.content,
                            error = result.error
                        )
                    }
                }
            }

            val updatedConversation = conversation.copy(
                updatedAtEpochMs = now,
                messages = updatedMessages
            )
            val updatedList = upsertConversation(currentState.conversations, updatedConversation)
            val updatedReadiness = if (currentState.relayConfig != dispatchConfig) currentState.readiness else when (result) {
                is ChatDispatchResult.Success -> {
                    val verifiedFeatures = buildList {
                        add("text")
                        if (result.wasStreamed) add("streaming")
                        if (result.usedVision) add("vision")
                        if (!result.turnMetadata?.toolCalls.isNullOrEmpty()) add("tools")
                    }.joinToString(", ")
                    val attachmentNote = if (hadAttachments && !result.usedVision) {
                        " Attachments exceeded route limits and were preserved locally."
                    } else {
                        ""
                    }
                    currentState.readiness.copy(
                        configured = currentState.relayConfig.isConfigured &&
                            credentialReady(currentState.relayConfig, currentState.hasToken),
                        relayReachable = VerificationState.PASS,
                        // A local loopback/emulator server (e.g. LM Studio) may ignore Authorization entirely, so its answer proves
                        // reachability, not that a (placeholder) token was verified; keep the health result, as /v1/models does.
                        authenticated = if (currentState.hasToken && !isLocalRelay(currentState.relayConfig)) {
                            VerificationState.PASS
                        } else {
                            currentState.readiness.authenticated
                        },
                        textGeneration = VerificationState.PASS,
                        vision = if (result.usedVision) VerificationState.PASS else currentState.readiness.vision,
                        streaming = if (result.wasStreamed) VerificationState.PASS else currentState.readiness.streaming,
                        tools = if (!result.turnMetadata?.toolCalls.isNullOrEmpty()) VerificationState.PASS else currentState.readiness.tools,
                        lastVerifiedAtEpochMs = now,
                        statusDetail = "Verified $verifiedFeatures on ${result.modelId}.$attachmentNote"
                    )
                }
                is ChatDispatchResult.Failure -> updateReadinessForError(
                    current = currentState.readiness,
                    config = currentState.relayConfig,
                    hasToken = currentState.hasToken,
                    error = result.error,
                    now = now
                )
            }
            completedConversation = conversation
            completedModelId = target.modelId
            currentState.copy(
                isSending = activeSendTarget != null && activeSendTarget != (conversationId to assistantMessageId),
                conversations = updatedList,
                readiness = updatedReadiness
            )
        }
        val completed = completedConversation ?: return
        conversationStore.saveConversations(_uiState.value.conversations)
        if (result is ChatDispatchResult.Failure) {
            recordError(
                error = result.error,
                config = dispatchConfig,
                modelId = completedModelId?.ifBlank { null } ?: dispatchConfig.modelId,
                temporary = completed.temporary
            )
        }
    }

    private fun mergeTurnMetadata(
        previous: GeminiTurnMetadata?,
        delta: StreamDelta
    ): GeminiTurnMetadata? {
        val mergedThinking = listOfNotNull(
            previous?.thinkingSummary?.takeIf { it.isNotEmpty() },
            delta.thinkingDelta.takeIf { it.isNotEmpty() }
        ).joinToString("").ifBlank { null }
        val mergedSignature = delta.thoughtSignature?.takeIf { it.isNotBlank() } ?: previous?.thoughtSignature
        val mergedTools = mergeToolCalls(previous?.toolCalls.orEmpty(), delta.toolCalls)
        val mergedFinish = delta.finishReason?.takeIf { it.isNotBlank() } ?: previous?.finishReason
        val truncated = previous?.isTruncated == true ||
            mergedFinish == "MAX_TOKENS" ||
            mergedFinish.equals("length", ignoreCase = true)
        if (mergedSignature == null && mergedThinking == null && mergedTools.isEmpty() && mergedFinish == null && !truncated) {
            return null
        }
        return GeminiTurnMetadata(
            thoughtSignature = mergedSignature,
            thinkingSummary = mergedThinking,
            toolCalls = mergedTools,
            finishReason = mergedFinish,
            isTruncated = truncated
        )
    }

    private fun mergeToolCalls(
        existing: List<ToolCallRecord>,
        incoming: List<ToolCallRecord>
    ): List<ToolCallRecord> {
        if (incoming.isEmpty()) return existing
        val result = existing.toMutableList()
        for (item in incoming) {
            val idx = result.indexOfFirst { it.id == item.id }
            if (idx >= 0) result[idx] = item else result += item
        }
        return result
    }

    private fun updateReadinessForError(
        current: ProviderReadiness,
        config: RelayConfig,
        hasToken: Boolean,
        error: ProviderError,
        now: Long
    ): ProviderReadiness {
        val configured = config.isConfigured && credentialReady(config, hasToken)
        return when (error.category) {
            com.hydrogen.ai.model.ProviderErrorCategory.UNCONFIGURED -> current.copy(
                configured = false,
                relayReachable = VerificationState.UNKNOWN,
                authenticated = VerificationState.UNKNOWN,
                lastVerifiedAtEpochMs = now,
                // The chat error says "tap Set up"; this text is also shown inside Set up, so use the
                // neutral setup status rather than repeating that instruction there.
                statusDetail = buildBaselineReadiness(config, hasToken).statusDetail
            )
            com.hydrogen.ai.model.ProviderErrorCategory.NETWORK_TIMEOUT -> current.copy(
                configured = configured,
                relayReachable = VerificationState.FAIL,
                lastVerifiedAtEpochMs = now,
                statusDetail = error.userMessage
            )
            com.hydrogen.ai.model.ProviderErrorCategory.UNAUTHORIZED_RELAY_401,
            com.hydrogen.ai.model.ProviderErrorCategory.UNAUTHORIZED_UPSTREAM_401,
            com.hydrogen.ai.model.ProviderErrorCategory.FORBIDDEN_403 -> current.copy(
                configured = configured,
                relayReachable = VerificationState.PASS,
                authenticated = VerificationState.FAIL,
                lastVerifiedAtEpochMs = now,
                statusDetail = error.userMessage
            )
            com.hydrogen.ai.model.ProviderErrorCategory.QUOTA_DAILY_429,
            com.hydrogen.ai.model.ProviderErrorCategory.QUOTA_RATE_TPM_RPM_429 -> current.copy(
                configured = configured,
                relayReachable = VerificationState.PASS,
                authenticated = VerificationState.PASS,
                lastVerifiedAtEpochMs = now,
                statusDetail = error.userMessage
            )
            com.hydrogen.ai.model.ProviderErrorCategory.SERVER_5XX -> current.copy(
                configured = configured,
                relayReachable = VerificationState.PASS,
                lastVerifiedAtEpochMs = now,
                statusDetail = error.userMessage
            )
            else -> current.copy(
                configured = configured,
                lastVerifiedAtEpochMs = now,
                statusDetail = error.userMessage
            )
        }
    }

    // "New Chat" is the pre-Nov-19 placeholder still stored in older saved chats.
    private val PLACEHOLDER_TITLES = setOf(ConversationTitler.DEFAULT_TITLE, "New Chat")

    /**
     * Auto-titles a chat from its first meaningful prompt (Markdown, code fences, URLs and attachment
     * labels are skipped). A placeholder title keeps waiting for a later prompt; user renames are kept.
     */
    private fun deriveTitle(conversation: Conversation, firstPrompt: String): String {
        if (conversation.title !in PLACEHOLDER_TITLES || firstPrompt.isBlank()) return conversation.title
        return ConversationTitler.titleFor(firstPrompt)
    }

    private fun upsertConversation(
        existing: List<Conversation>,
        updated: Conversation
    ): List<Conversation> {
        val replaced = if (existing.any { it.id == updated.id }) {
            existing.map { if (it.id == updated.id) updated else it }
        } else {
            listOf(updated) + existing
        }
        return replaced.sortedByDescending { it.updatedAtEpochMs }
    }

    private fun newBlankConversation(
        now: Long,
        config: RelayConfig = _uiState.value.relayConfig
    ): Conversation = Conversation(
        id = UUID.randomUUID().toString(),
        title = ConversationTitler.DEFAULT_TITLE,
        createdAtEpochMs = now,
        updatedAtEpochMs = now,
        modelId = config.modelId,
        route = config.route,
        preferredConnection = ChatConnectionPreference.capture(config)
    )

    /**
     * Relay route pointed at the user's own model server (e.g. LM Studio), where a token is optional: on this PC
     * (loopback/emulator) or on the home Wi-Fi, where a token is never sent at all.
     */
    private fun isLocalRelay(config: RelayConfig): Boolean =
        config.route == ProviderRoute.GEMINI_HYDROGEN_RELAY &&
            (HttpGeminiHydrogenRelayBackend.isLoopbackOrEmulatorHost(config.effectiveBaseUrl) ||
                HttpGeminiHydrogenRelayBackend.isWifiModelHost(config.effectiveBaseUrl))

    /** True when a token is set, or the route is a local relay that does not need one. Never true for remote hosts without a token. */
    private fun credentialReady(config: RelayConfig, hasToken: Boolean): Boolean =
        hasToken || isLocalRelay(config)

    private fun buildBaselineReadiness(
        config: RelayConfig,
        hasToken: Boolean,
        workMode: VerificationState = VerificationState.UNAVAILABLE
    ): ProviderReadiness {
        val configured = config.isConfigured && credentialReady(config, hasToken)
        val detail = when (config.route) {
            ProviderRoute.LOCAL_OFFLINE ->
                "The on-device model isn't installed yet. Pick another way to run your AI."
            ProviderRoute.ANTHROPIC_MESSAGES -> when {
                !hasToken -> "Add your Anthropic-compatible API key to start chatting."
                else -> "Set to ${config.modelId}. The Anthropic-format connection hasn't been tested yet."
            }
            ProviderRoute.GOOGLE_AI_STUDIO_DIRECT -> when {
                !hasToken -> "Add your Google AI Studio API key to start chatting."
                else -> "Set to ${config.modelId} with Google AI Studio. The connection hasn't been tested yet."
            }
            ProviderRoute.GEMINI_HYDROGEN_RELAY -> when {
                config.baseUrl.isBlank() -> "Orbit isn't connected to an AI yet. Choose a provider to start chatting."
                !hasToken && isLocalRelay(config) ->
                    "Set to ${config.modelId} on your own computer. The connection hasn't been tested yet."
                !hasToken -> "Server address saved. Add its access token to start chatting."
                else -> "Set to ${config.modelId}. The connection hasn't been tested yet."
            }
        }
        return ProviderReadiness(
            configured = configured,
            workMode = workMode,
            statusDetail = detail
        )
    }

    private fun reconcileInterruptedConversations(
        conversations: List<Conversation>,
        now: Long
    ): Pair<List<Conversation>, Boolean> {
        var anyChanged = false
        val reconciled = conversations.map { conv ->
            var convChanged = false
            val updatedMessages = conv.messages.map { msg ->
                if (msg.role == MessageRole.ASSISTANT &&
                    (msg.status == MessageStatus.DISPATCHED || msg.status == MessageStatus.STREAMING)
                ) {
                    convChanged = true
                    anyChanged = true
                    msg.copy(
                        status = MessageStatus.FAILED,
                        error = ProviderError(
                            category = ProviderErrorCategory.NETWORK_TIMEOUT,
                            userMessage = "Orbit closed before this reply finished. Tap Retry to ask again.",
                            technicalDetail = "Previous status was ${msg.status.name}; partial response preserved without fabrication.",
                            sourceLayer = "local_lifecycle"
                        )
                    )
                } else {
                    msg
                }
            }
            if (convChanged) {
                conv.copy(
                    updatedAtEpochMs = maxOf(conv.updatedAtEpochMs, now),
                    messages = updatedMessages
                )
            } else {
                conv
            }
        }
        return reconciled to anyChanged
    }

    private fun applyOmittedAttachmentMarks(
        messages: List<ChatMessage>,
        preflight: PreflightAssessment
    ): List<ChatMessage> {
        val omittedById = preflight.omittedImages.associateBy { it.id }
        return messages.map { msg ->
            if (msg.attachments.isEmpty()) {
                msg
            } else {
                msg.copy(
                    attachments = msg.attachments.map { att ->
                        val omitted = omittedById[att.id]
                        if (omitted != null) {
                            att.copy(
                                omittedFromRemote = true,
                                omissionReason = omitted.omissionReason
                            )
                        } else if (att.omittedFromRemote) {
                            att.copy(omittedFromRemote = false, omissionReason = null)
                        } else {
                            att
                        }
                    }
                )
            }
        }
    }

    private fun isAttachmentUriReferenced(
        uri: String?,
        conversations: List<Conversation>,
        staged: List<AttachmentManifestItem>
    ): Boolean {
        if (uri.isNullOrBlank()) return false
        if (staged.any { it.localUri == uri }) return true
        if (_uiState.value.draftBeforeEdit?.attachments.orEmpty().any { it.localUri == uri }) return true
        if (_uiState.value.parkedDrafts.values.any { draft -> draft.attachments.any { it.localUri == uri } }) return true
        // Hidden versions count: flipping back to one must still find its files.
        return conversations.any { conv ->
            MessageVersions.everyMessage(conv.messages).any { msg ->
                msg.attachments.any { it.localUri == uri }
            }
        }
    }

    class Factory(
        private val conversationStore: ConversationStore,
        private val configStore: RelayConfigStore,
        private val credentialStore: RelayCredentialStore,
        private val backend: ChatBackend,
        private val companionClient: CompanionClient = HttpCompanionClient(),
        private val companionSessionStore: CompanionSessionStore = InMemoryCompanionSessionStore(),
        private val onDeleteLocalAttachment: (String) -> Unit = {},
        private val companionPollConfig: CompanionPollConfig = CompanionPollConfig(),
        private val memoryStore: UserMemoryStore = InMemoryUserMemoryStore(),
        private val personalStore: PersonalStore = InMemoryPersonalStore(),
        private val projectStore: ProjectStore = InMemoryProjectStore(),
        private val appVersionStore: AppVersionStore = InMemoryAppVersionStore(),
        private val errorLogStore: ErrorLogStore = InMemoryErrorLogStore(),
        private val clock: () -> Long = { System.currentTimeMillis() }
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                return ChatViewModel(
                    conversationStore = conversationStore,
                    configStore = configStore,
                    credentialStore = credentialStore,
                    backend = backend,
                    companionClient = companionClient,
                    companionSessionStore = companionSessionStore,
                    onDeleteLocalAttachment = onDeleteLocalAttachment,
                    companionPollConfig = companionPollConfig,
                    clock = clock,
                    memoryStore = memoryStore,
                    personalStore = personalStore,
                    projectStore = projectStore,
                    appVersionStore = appVersionStore,
                    errorLogStore = errorLogStore
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
