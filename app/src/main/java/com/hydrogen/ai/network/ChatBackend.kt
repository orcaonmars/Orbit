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
    val stream: Boolean = true
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

sealed interface ChatDispatchResult {
    data class Success(
        val assistantText: String,
        val modelId: String,
        val turnMetadata: GeminiTurnMetadata? = null,
        val usedVision: Boolean = false,
        val wasStreamed: Boolean = false
    ) : ChatDispatchResult

    data class Failure(val error: ProviderError) : ChatDispatchResult
}

sealed interface ModelListResult {
    data class Success(val modelIds: List<String>) : ModelListResult
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

