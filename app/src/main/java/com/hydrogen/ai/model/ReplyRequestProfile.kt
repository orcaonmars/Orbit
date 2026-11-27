package com.hydrogen.ai.model

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/** Local audit metadata only. Never stores a key, raw endpoint, or prompt. */
@Serializable
data class ReplyRequestProfile(
    val route: ProviderRoute,
    val endpointFingerprint: String,
    val requestedModel: String,
    val providerLabel: String,
    val reasoningMode: ReasoningMode,
    val completionBudget: Int? = null,
    /** Which endpoint the reply came from. Defaults to Chat Completions for records saved before 0.3.5. */
    val openAiProtocol: OpenAiProtocol = OpenAiProtocol.CHAT_COMPLETIONS
) {
    fun sameRequestTarget(other: ReplyRequestProfile): Boolean =
        route == other.route && endpointFingerprint == other.endpointFingerprint &&
            requestedModel == other.requestedModel && reasoningMode == other.reasoningMode &&
            completionBudget == other.completionBudget && openAiProtocol == other.openAiProtocol

    /**
     * What was asked of the model beyond the defaults, as its own short line under the reply header; null when
     * nothing was (the header already names the model and service).
     */
    fun requestNote(): String? = when {
        completionBudget != null -> "Asked for a $completionBudget token budget"
        reasoningMode != ReasoningMode.AUTO -> "Asked for ${reasoningMode.label.lowercase()} effort"
        else -> null
    }

    companion object {
        fun from(config: RelayConfig): ReplyRequestProfile {
            val endpoint = config.effectiveBaseUrl
            val fingerprint = MessageDigest.getInstance("SHA-256")
                .digest(endpoint.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            val provider = ProviderPresets.serviceNameFor(config.route, endpoint)
                ?: if (config.route == ProviderRoute.LOCAL_OFFLINE) "Offline" else "Custom provider"
            return ReplyRequestProfile(config.route, fingerprint, config.modelId, provider,
                config.reasoningMode, ReasoningPolicy.cleanApisBudget(endpoint, config.reasoningMode),
                openAiProtocol = config.openAiProtocol)
        }
    }
}
