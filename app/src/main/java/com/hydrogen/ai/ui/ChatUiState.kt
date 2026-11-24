package com.hydrogen.ai.ui

import com.hydrogen.ai.companion.CompanionApproval
import com.hydrogen.ai.companion.CompanionConfig
import com.hydrogen.ai.companion.CompanionHealthSnapshot
import com.hydrogen.ai.companion.CompanionSession
import com.hydrogen.ai.companion.WorkActionType
import com.hydrogen.ai.companion.WorkTask
import com.hydrogen.ai.model.AttachmentManifestItem
import com.hydrogen.ai.model.Conversation
import com.hydrogen.ai.model.MemoryItem
import com.hydrogen.ai.model.PersonalProfile
import com.hydrogen.ai.model.ProviderCapabilities
import com.hydrogen.ai.model.ProviderReadiness
import com.hydrogen.ai.model.RelayConfig

data class CompanionUiState(
    val config: CompanionConfig = CompanionConfig(),
    val session: CompanionSession? = null,
    val health: CompanionHealthSnapshot? = null,
    val isCheckingHealth: Boolean = false,
    val isPairing: Boolean = false,
    val isRevoking: Boolean = false,
    val activeApproval: CompanionApproval? = null,
    val activeApprovalTargetPath: String = "",
    val activeApprovalAction: WorkActionType = WorkActionType.LIST_DIRECTORY,
    val pendingWorkOffsetBytes: Int? = null,
    val activeTask: WorkTask? = null,
    val isRequestingApproval: Boolean = false,
    val isPollingApproval: Boolean = false,
    val isRunningWorkTask: Boolean = false,
    val isCancellingWorkTask: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    /** "Ask your PC" cards by assistant message id, after the user tapped Send (in memory only). */
    val askPcSends: Map<String, AskPcSend> = emptyMap()
) {
    val isPaired: Boolean
        get() = session != null

    val isWorkBusy: Boolean
        get() = isRequestingApproval || isPollingApproval || isRunningWorkTask || isCancellingWorkTask
}

/** What happened after the user tapped Send on one "Ask your PC" card. */
data class AskPcSend(
    val sending: Boolean = false,
    val approvalId: String? = null,
    val errorMessage: String? = null
)

/** Model ids last listed by the server at [baseUrl]; the settings sheet only offers them for that same URL. */
data class ModelListUiState(
    val isLoading: Boolean = false,
    val baseUrl: String = "",
    val modelIds: List<String> = emptyList(),
    val message: String? = null
)

data class ParkedChatDraft(val text: String, val attachments: List<AttachmentManifestItem>)

data class ChatUiState(
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val isCheckingHealth: Boolean = false,
    val conversations: List<Conversation> = emptyList(),
    val activeConversationId: String? = null,
    val draftText: String = "",
    val parkedDrafts: Map<String, ParkedChatDraft> = emptyMap(),
    val stagedAttachments: List<AttachmentManifestItem> = emptyList(),
    val preflightWarnings: List<String> = emptyList(),
    val relayConfig: RelayConfig = RelayConfig(),
    val hasToken: Boolean = false,
    val maskedTokenLabel: String = "Not set",
    /** Masked labels of the active service's backup keys, in the order they are tried. */
    val backupKeyLabels: List<String> = emptyList(),
    val capabilities: ProviderCapabilities = RelayConfig().capabilities(),
    val readiness: ProviderReadiness = ProviderReadiness(),
    val companion: CompanionUiState = CompanionUiState(),
    val modelList: ModelListUiState = ModelListUiState(),
    /** A sent message of yours being edited in the composer; sending makes a new version of it (the old one is kept). */
    val editingMessageId: String? = null,
    val draftBeforeEdit: ParkedChatDraft? = null,
    /** Notes the user asked Orbit to remember; stored only on this device and added to the system message. */
    val memoryItems: List<MemoryItem> = emptyList(),
    val memoryEnabled: Boolean = true,
    /** A short one-off notice (for example "Saved to memory"), shown once and then cleared. */
    val notice: String? = null,
    /** Show the one-time "What's new" sheet: this phone last ran an older Orbit (see WhatsNew.shouldShow). */
    val whatsNew: Boolean = false,
    /** Settings → Personal: your name, what Orbit should know about you and how it should reply. */
    val personal: PersonalProfile = PersonalProfile(),
    /** Projects: chats that share instructions and files. Device-local. */
    val projects: List<com.hydrogen.ai.model.Project> = emptyList(),
    /** Settings → Error log: recent failures, newest first. Device-local, with no chat text or keys. */
    val errorLog: List<com.hydrogen.ai.model.ErrorLogEntry> = emptyList()
) {
    /** The open chat's project, if it has one that still exists. */
    val activeProject: com.hydrogen.ai.model.Project?
        get() = com.hydrogen.ai.model.Projects.projectOf(activeConversation, projects)

    val activeConversation: Conversation?
        get() = conversations.firstOrNull { it.id == activeConversationId }

    /** The chat list, search and counts: everything except a temporary chat. */
    val savedConversations: List<Conversation>
        get() = conversations.filterNot { it.temporary }

    val isTemporaryChat: Boolean
        get() = activeConversation?.temporary == true
}
