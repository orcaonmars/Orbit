package com.hydrogen.ai.network

import com.hydrogen.ai.model.ProviderError
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig

/** Sends each request to the backend for the connection's format: OpenAI-compatible, Google, or Anthropic. */
class RoutingChatBackend(
    private val relayBackend: ChatBackend,
    private val googleAiStudioBackend: ChatBackend,
    private val anthropicBackend: ChatBackend = HttpAnthropicMessagesBackend()
) : ChatBackend {

    override suspend fun checkHealth(config: RelayConfig, bearerToken: String?): RelayHealthResult {
        return when (config.route) {
            ProviderRoute.GEMINI_HYDROGEN_RELAY -> relayBackend.checkHealth(config, bearerToken)
            ProviderRoute.GOOGLE_AI_STUDIO_DIRECT -> googleAiStudioBackend.checkHealth(config, bearerToken)
            ProviderRoute.ANTHROPIC_MESSAGES -> anthropicBackend.checkHealth(config, bearerToken)
            ProviderRoute.LOCAL_OFFLINE -> RelayHealthResult.Failure(localUnavailableError())
        }
    }

    override suspend fun listModels(config: RelayConfig, bearerToken: String?): ModelListResult {
        return when (config.route) {
            ProviderRoute.GEMINI_HYDROGEN_RELAY -> relayBackend.listModels(config, bearerToken)
            ProviderRoute.GOOGLE_AI_STUDIO_DIRECT -> googleAiStudioBackend.listModels(config, bearerToken)
            ProviderRoute.ANTHROPIC_MESSAGES -> anthropicBackend.listModels(config, bearerToken)
            ProviderRoute.LOCAL_OFFLINE -> ModelListResult.Failure(localUnavailableError().userMessage)
        }
    }

    override suspend fun sendChat(
        request: ChatDispatchRequest,
        config: RelayConfig,
        bearerToken: String?,
        onStreamDelta: suspend (StreamDelta) -> Unit
    ): ChatDispatchResult {
        return when (config.route) {
            ProviderRoute.GEMINI_HYDROGEN_RELAY ->
                relayBackend.sendChat(request, config, bearerToken, onStreamDelta)
            ProviderRoute.GOOGLE_AI_STUDIO_DIRECT ->
                googleAiStudioBackend.sendChat(request, config, bearerToken, onStreamDelta)
            ProviderRoute.ANTHROPIC_MESSAGES ->
                anthropicBackend.sendChat(request, config, bearerToken, onStreamDelta)
            ProviderRoute.LOCAL_OFFLINE ->
                ChatDispatchResult.Failure(localUnavailableError())
        }
    }

    private fun localUnavailableError(): ProviderError = ProviderError(
        category = ProviderErrorCategory.UNSUPPORTED_CAPABILITY,
        userMessage = "The on-device model isn't installed yet. Pick another way to run your AI in Set up.",
        sourceLayer = "local_model"
    )
}
