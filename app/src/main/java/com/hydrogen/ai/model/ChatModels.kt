package com.hydrogen.ai.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

object HydrogenDefaults {
    const val PRIMARY_PROVIDER_ID = "gemini-hydrogen-relay"
    const val PRIMARY_MODEL_ID = "gemini-4-hydrogen"
    const val GOOGLE_AI_STUDIO_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    const val GOOGLE_AI_STUDIO_DEFAULT_MODEL = PRIMARY_MODEL_ID
    /** Anthropic's official Messages API host. Keys for the Anthropic card are only ever sent here. */
    const val ANTHROPIC_BASE_URL = "https://api.anthropic.com"
    const val DEFAULT_CONTEXT_BUDGET_TOKENS = 120_000
    const val DEFAULT_MAX_IMAGES_PER_REQUEST = 8
    /** Photos are shrunk before sending, so this only rules out files too big to read; it matches import. */
    const val DEFAULT_MAX_IMAGE_BYTES = 20L * 1024L * 1024L
}

@Serializable
enum class ProviderRoute(val displayName: String) {
    GEMINI_HYDROGEN_RELAY("OpenAI-compatible server"),
    GOOGLE_AI_STUDIO_DIRECT("Google AI Studio (BYOK)"),
    LOCAL_OFFLINE("Local Offline Model (Not Installed)"),
    /** Anthropic's Messages API format: Anthropic itself, or another service that speaks the same format. */
    ANTHROPIC_MESSAGES("Anthropic Messages API")
}

@Serializable
enum class VerificationState(val label: String) {
    UNKNOWN("Untested"),
    PASS("Verified"),
    FAIL("Failed"),
    UNAVAILABLE("Unavailable")
}

@Serializable
data class ProviderCapabilities(
    val supportsText: Boolean = true,
    val supportsVision: Boolean = true,
    val supportsStreaming: Boolean = true,
    val supportsToolUse: Boolean = false,
    val supportsThinking: Boolean = false,
    val maxContextTokens: Int = HydrogenDefaults.DEFAULT_CONTEXT_BUDGET_TOKENS,
    val maxImagesPerRequest: Int = HydrogenDefaults.DEFAULT_MAX_IMAGES_PER_REQUEST,
    val sharedQuotaNote: String = "Keys under the same Google project share one quota pool and do not multiply capacity."
)

@Serializable
data class ProviderReadiness(
    val configured: Boolean = false,
    val relayReachable: VerificationState = VerificationState.UNKNOWN,
    val authenticated: VerificationState = VerificationState.UNKNOWN,
    val textGeneration: VerificationState = VerificationState.UNKNOWN,
    val vision: VerificationState = VerificationState.UNKNOWN,
    val streaming: VerificationState = VerificationState.UNKNOWN,
    val tools: VerificationState = VerificationState.UNKNOWN,
    val workMode: VerificationState = VerificationState.UNAVAILABLE,
    val lastVerifiedAtEpochMs: Long? = null,
    val statusDetail: String = "Orbit isn't connected to an AI yet."
)

@Serializable
data class RelayConfig(
    val route: ProviderRoute = ProviderRoute.GEMINI_HYDROGEN_RELAY,
    val baseUrl: String = "",
    val modelId: String = HydrogenDefaults.PRIMARY_MODEL_ID,
    val maxContextTokensBudget: Int = HydrogenDefaults.DEFAULT_CONTEXT_BUDGET_TOKENS,
    /** 0 means automatic: the provider's own limit from [ImageLimits]. */
    val maxImagesPerRequest: Int = ImageLimits.AUTOMATIC,
    val maxImageBytes: Long = HydrogenDefaults.DEFAULT_MAX_IMAGE_BYTES,
    val streamingEnabled: Boolean = true,
    val rememberTokenInKeystore: Boolean = false,
    /** Google AI Studio only: let Gemini search the web (Google Search grounding) and cite its sources. */
    val webSearchEnabled: Boolean = false,
    val reasoningMode: ReasoningMode = ReasoningMode.AUTO,
    val openRouterReasoningModels: Set<String> = emptySet()
) {
    val effectiveBaseUrl: String
        get() = when (route) {
            ProviderRoute.GEMINI_HYDROGEN_RELAY -> baseUrl.trim().trimEnd('/')
            // Direct API keys must only be sent to Google's official HTTPS endpoint.
            // Never reuse a relay URL when the user switches provider routes.
            ProviderRoute.GOOGLE_AI_STUDIO_DIRECT -> HydrogenDefaults.GOOGLE_AI_STUDIO_BASE_URL
            ProviderRoute.LOCAL_OFFLINE -> ""
            // Blank means Anthropic itself; any other https address is a service using the same format.
            ProviderRoute.ANTHROPIC_MESSAGES ->
                baseUrl.trim().trimEnd('/').ifEmpty { HydrogenDefaults.ANTHROPIC_BASE_URL }
        }

    val isConfigured: Boolean
        get() = when (route) {
            ProviderRoute.GEMINI_HYDROGEN_RELAY -> baseUrl.isNotBlank() && modelId.isNotBlank()
            ProviderRoute.GOOGLE_AI_STUDIO_DIRECT -> modelId.isNotBlank()
            ProviderRoute.LOCAL_OFFLINE -> false
            ProviderRoute.ANTHROPIC_MESSAGES -> modelId.isNotBlank()
        }

    fun capabilities(): ProviderCapabilities = when (route) {
        ProviderRoute.ANTHROPIC_MESSAGES -> ProviderCapabilities(
            supportsText = true,
            supportsVision = true,
            supportsStreaming = true,
            supportsToolUse = false,
            supportsThinking = true,
            maxContextTokens = maxContextTokensBudget,
            maxImagesPerRequest = ImageLimits.effectiveCap(this),
            sharedQuotaNote = "Limits and billing come from your account with this service. Orbit never pays for anything."
        )
        ProviderRoute.GEMINI_HYDROGEN_RELAY -> ProviderCapabilities(
            supportsText = true,
            supportsVision = true,
            supportsStreaming = true,
            supportsToolUse = false,
            supportsThinking = true,
            maxContextTokens = maxContextTokensBudget,
            maxImagesPerRequest = ImageLimits.effectiveCap(this)
        )
        ProviderRoute.GOOGLE_AI_STUDIO_DIRECT -> ProviderCapabilities(
            supportsText = true,
            supportsVision = true,
            supportsStreaming = true,
            supportsToolUse = true,
            supportsThinking = true,
            maxContextTokens = maxContextTokensBudget,
            maxImagesPerRequest = ImageLimits.effectiveCap(this)
        )
        ProviderRoute.LOCAL_OFFLINE -> ProviderCapabilities(
            supportsText = false,
            supportsVision = false,
            supportsStreaming = false,
            supportsToolUse = false,
            supportsThinking = false,
            maxContextTokens = 0,
            maxImagesPerRequest = 0,
            sharedQuotaNote = "Local offline models require downloaded weights and device hardware check (not installed)."
        )
    }
}

@Serializable
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

@Serializable
enum class MessageStatus(val label: String) {
    STAGED("Staged locally"),
    DISPATCHED("Waiting for relay"),
    STREAMING("Streaming"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

@Serializable
data class AttachmentManifestItem(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localUri: String,
    val isFromToolResult: Boolean = false,
    val omittedFromRemote: Boolean = false,
    val omissionReason: String? = null
)

@Serializable
data class ToolCallRecord(
    val id: String,
    val name: String,
    val argumentsJson: String = "{}",
    val thoughtSignature: String? = null,
    val executed: Boolean = false
)

@Serializable
data class GeminiTurnMetadata(
    val thoughtSignature: String? = null,
    val thinkingSummary: String? = null,
    val toolCalls: List<ToolCallRecord> = emptyList(),
    val finishReason: String? = null,
    val isTruncated: Boolean = false
)

/**
 * [title] is the plain-language heading shown on a failed message. Status codes and raw server text belong in
 * [ProviderError.httpStatus] / [ProviderError.technicalDetail], which the chat shows only under "Details".
 * Serialized by enum name, so titles can change freely.
 */
@Serializable
enum class ProviderErrorCategory(val title: String) {
    UNCONFIGURED("Not connected yet"),
    PREFLIGHT_BLOCKED("Can't send this"),
    UNSUPPORTED_CAPABILITY("Not supported by this model"),
    BAD_REQUEST_400("Request not accepted"),
    UNAUTHORIZED_RELAY_401("Key not accepted"),
    UNAUTHORIZED_UPSTREAM_401("API key not accepted"),
    FORBIDDEN_403("Access denied"),
    CONTENT_FILTERED("Blocked by a safety filter"),
    QUOTA_DAILY_429("Daily limit reached"),
    QUOTA_RATE_TPM_RPM_429("Too many requests"),
    SERVER_5XX("Server problem"),
    NETWORK_TIMEOUT("Can't reach the server"),
    CANCELLED("Stopped"),
    UNKNOWN_PROTOCOL("Unexpected reply"),
    OUT_OF_CREDITS("Out of credits"),
    MODEL_NOT_FOUND("Model not found"),
    CONTEXT_TOO_LONG("Chat too long for this model"),
    REQUEST_TOO_LARGE("Message too large"),
    OVERLOADED("Service is busy"),
    REGION_UNAVAILABLE("Not available in your region"),
    INTERRUPTED("Reply cut off")
}

@Serializable
data class ProviderError(
    val category: ProviderErrorCategory,
    val httpStatus: Int? = null,
    val userMessage: String,
    val technicalDetail: String = "",
    val retryAfterSeconds: Int? = null,
    val sourceLayer: String = "client"
)

@Serializable
data class ChatMessage(
    val id: String,
    val correlationId: String,
    val role: MessageRole,
    val content: String,
    val createdAtEpochMs: Long,
    val modelId: String = HydrogenDefaults.PRIMARY_MODEL_ID,
    val status: MessageStatus = MessageStatus.COMPLETED,
    val attachments: List<AttachmentManifestItem> = emptyList(),
    val turnMetadata: GeminiTurnMetadata? = null,
    val hasSideEffectsInProgress: Boolean = false,
    val error: ProviderError? = null,
    /** Another model's answer to the same question, shown for comparison but never sent as context. */
    val comparison: Boolean = false,
    /**
     * How long the model thought before its first word, measured on this phone. Null when it didn't think, the
     * reply wasn't streamed, or the chat was saved before this existed.
     */
    val thinkingMs: Long? = null,
    /**
     * Earlier and later versions of the turn that starts with this message (an edit, or a regenerated reply), each
     * holding this spot and everything after it. Empty for almost every message. See [MessageVersions].
     */
    val otherVersions: List<MessageBranch> = emptyList(),
    /** Where this version sits among [otherVersions], from 0. */
    val versionIndex: Int = 0,
    val requestProfile: ReplyRequestProfile? = null
)

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val modelId: String = HydrogenDefaults.PRIMARY_MODEL_ID,
    val route: ProviderRoute = ProviderRoute.GEMINI_HYDROGEN_RELAY,
    val messages: List<ChatMessage> = emptyList(),
    /** Per-chat instructions sent as a leading SYSTEM message; blank means none. Older saved chats default to blank. */
    val systemPrompt: String = "",
    /** Pinned chats stay at the top of the list. Older saved chats default to unpinned. */
    val pinned: Boolean = false,
    /** Archived chats leave the list but stay on the device; sending a message brings one back. */
    val archived: Boolean = false,
    /** The project this chat belongs to (its instructions and files apply); null or an unknown id means none. */
    val projectId: String? = null,
    /**
     * A temporary chat lives only in memory: it is never written to storage or a backup, stays out of the chat list
     * and skips memory. Transient, so the flag itself can never be saved or read back from a file.
     */
    @Transient val temporary: Boolean = false,
    /** Device-local endpoint/model choice. Portable backups deliberately strip this field. */
    val preferredConnection: ChatConnectionPreference? = null
)
