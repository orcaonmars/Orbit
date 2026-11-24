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
    val completionBudget: Int? = null
) {
    fun sameRequestTarget(other: ReplyRequestProfile): Boolean =
        route == other.route && endpointFingerprint == other.endpointFingerprint &&
            requestedModel == other.requestedModel && reasoningMode == other.reasoningMode &&
            completionBudget == other.completionBudget

    fun detail(): String = buildString {
        append(providerLabel)
        if (completionBudget != null) append(" / requested $completionBudget token budget")
        else if (reasoningMode != ReasoningMode.AUTO) append(" / requested ${reasoningMode.label} effort")
    }

    companion object {
        fun from(config: RelayConfig): ReplyRequestProfile {
            val endpoint = config.effectiveBaseUrl
            val fingerprint = MessageDigest.getInstance("SHA-256")
                .digest(endpoint.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            val provider = ProviderPresets.serviceNameFor(config.route, endpoint)
                ?: if (config.route == ProviderRoute.LOCAL_OFFLINE) "Offline" else "Custom provider"
            return ReplyRequestProfile(config.route, fingerprint, config.modelId, provider,
                config.reasoningMode, ReasoningPolicy.cleanApisBudget(endpoint, config.reasoningMode))
        }
    }
}
