package com.hydrogen.ai.model

import java.net.URI
import kotlinx.serialization.Serializable

/** Local-only chat routing choice. Does not contain tokens, quota data or credential settings. */
@Serializable
data class ChatConnectionPreference(
    val route: ProviderRoute,
    val baseUrl: String,
    val modelId: String,
    val reasoningMode: ReasoningMode = ReasoningMode.AUTO,
    val webSearchEnabled: Boolean = false
) {
    fun applyTo(global: RelayConfig): RelayConfig? {
        val candidate = global.copy(
            route = route, baseUrl = baseUrl, modelId = modelId,
            reasoningMode = reasoningMode,
            webSearchEnabled = route == ProviderRoute.GOOGLE_AI_STUDIO_DIRECT && webSearchEnabled,
            openRouterReasoningModels = if (route == global.route && baseUrl == global.effectiveBaseUrl)
                global.openRouterReasoningModels else emptySet()
        )
        return candidate.takeIf { capture(it) != null && capture(it) == this }
    }

    companion object {
        fun capture(config: RelayConfig): ChatConnectionPreference? {
            if (!config.isConfigured || config.route == ProviderRoute.LOCAL_OFFLINE) return null
            val model = config.modelId.trim()
            val url = config.effectiveBaseUrl.trimEnd('/')
            if (model.isEmpty() || model.length > 300 || url.length > 500) return null
            if (model.any { it.isISOControl() }) return null
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            if (uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
            val secure = uri.scheme.equals("https", ignoreCase = true)
            val local = config.route == ProviderRoute.GEMINI_HYDROGEN_RELAY && LocalServerPresets.isPrivateServer(url)
            if (!secure && !local) return null
            return ChatConnectionPreference(config.route, url, model, config.reasoningMode,
                config.route == ProviderRoute.GOOGLE_AI_STUDIO_DIRECT && config.webSearchEnabled)
        }
    }
}
