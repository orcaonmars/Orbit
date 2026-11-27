package com.hydrogen.ai.network

import com.hydrogen.ai.model.AttachmentManifestItem
import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.GeminiTurnMetadata
import com.hydrogen.ai.model.HydrogenDefaults
import com.hydrogen.ai.model.ProviderError
import com.hydrogen.ai.model.RelayConfig
import com.hydrogen.ai.model.ToolCallRecord

data class ChatDispatchRequest(
    val correlationId: String,
    val modelId: String = HydrogenDefaults.PRIMARY_MODEL_ID,
    val messages: List<ChatMessage>,
    val attachments: List<AttachmentManifestItem>,
    val omittedAttachmentIds: Set<String> = emptySet(),
    val stream: Boolean = true,
    internal val reasoningEffort: String? = null,
    internal val openRouterReasoningEffort: String? = null,
    internal val completionTokenBudget: Int? = null
)

data class StreamDelta(
    val textDelta: String = "",
    val thinkingDelta: String = "",
    val thoughtSignature: String? = null,
    val toolCalls: List<ToolCallRecord> = emptyList(),
    val finishReason: String? = null
)

sealed interface RelayHealthResult {
    data class Reachable(
        val authenticated: Boolean,
        val detail: String
    ) : RelayHealthResult

    data class Failure(val error: ProviderError) : RelayHealthResult
}

/** Token counts a server reported for one reply. Null fields were not reported. */
data class TokenUsage(
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val reasoningTokens: Int? = null
)

sealed interface ChatDispatchResult {
    data class Success(
        val assistantText: String,
        val modelId: String,
        val turnMetadata: GeminiTurnMetadata? = null,
        val usedVision: Boolean = false,
        val wasStreamed: Boolean = false,
        /** Only the Responses API reports this today; Chat Completions replies leave it null. */
        val usage: TokenUsage? = null
    ) : ChatDispatchResult

    data class Failure(val error: ProviderError) : ChatDispatchResult
}

sealed interface ModelListResult {
    data class Success(val modelIds: List<String>, val reasoningModelIds: Set<String>? = null) : ModelListResult
    data class Failure(val message: String) : ModelListResult
}

interface ChatBackend {
    suspend fun checkHealth(config: RelayConfig, bearerToken: String?): RelayHealthResult

    /** Lists model ids the configured server offers. Providers without a model list report it as unsupported. */
    suspend fun listModels(config: RelayConfig, bearerToken: String?): ModelListResult =
        ModelListResult.Failure("This provider does not offer a model list; enter the model id manually.")

    suspend fun sendChat(
        request: ChatDispatchRequest,
        config: RelayConfig,
        bearerToken: String?,
        onStreamDelta: suspend (StreamDelta) -> Unit = {}
    ): ChatDispatchResult
}

