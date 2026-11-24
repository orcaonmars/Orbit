package com.hydrogen.ai.model

import kotlinx.serialization.Serializable

/** Automatic preserves the provider's original behavior. Low is not a promise of zero thinking. */
@Serializable
enum class ReasoningMode(val label: String, val effort: String?, val description: String) {
    AUTO("Auto", null, "Use the model's default"),
    LOW("Low", "low", "Fast answers and simple tasks"),
    MEDIUM("Medium", "medium", "Balanced speed and depth"),
    HIGH("High", "high", "Hard problems and coding"),
    EXTRA_HIGH("Extra High", "xhigh", "Extended analysis and planning"),
    ULTRA("Ultra", "max", "Maximum supported effort; slowest");

    val isExtended: Boolean get() = this == EXTRA_HIGH || this == ULTRA

    companion object {
        fun fromStored(value: String?): ReasoningMode = entries.firstOrNull { it.name == value } ?: AUTO
    }
}

/** Explicit controls are intentionally restricted to documented model families and official endpoints. */
object ReasoningPolicy {
    fun googleLevel(model: String, mode: ReasoningMode): String? {
        val id = model.trim().lowercase().removePrefix("models/")
        if (id !in setOf("gemini-4-hydrogen", "gemini-4-argon", "gemini-3.8-flash", "gemini-3.1-pro-preview")) return null
        return mode.effort?.takeUnless { mode.isExtended }?.uppercase()
    }

    fun openAiEffort(baseUrl: String, model: String, mode: ReasoningMode): String? {
        val endpoint = runCatching { java.net.URI(baseUrl) }.getOrNull() ?: return null
        if (endpoint.scheme != "https" || !endpoint.host.equals("api.openai.com", ignoreCase = true) ||
            endpoint.userInfo != null || endpoint.port !in listOf(-1, 443)) return null
        val id = model.trim().lowercase()
        if (!Regex("^(gpt-5(?:[.-][a-z0-9.-]+)?|gpt-6(?:[.-][a-z0-9.-]+)?|o[134](?:-[a-z0-9.-]+)?)$").matches(id)) return null
        if (id.contains("chat") || id.contains("pro") || id.contains("codex") || id.contains("nano")) return null
        if (mode == ReasoningMode.ULTRA) return null
        if (mode == ReasoningMode.EXTRA_HIGH && !supportsExtraOpenAi(id)) return null
        return mode.effort
    }

    private fun supportsExtraOpenAi(id: String): Boolean =
        Regex("^(gpt-5\\.(4|5|6)(?:-[0-9-]+)?|gpt-6(?:\\.[0-9]+)?(?:-[a-z0-9.-]+)?)$").matches(id)

    private val routerKnownExtra = setOf("openai/gpt-5.4", "anthropic/claude-sonnet-5.5", "anthropic/claude-opus-5.5")

    // Clean APIs documents max_tokens, not a native reasoning-effort parameter.
    fun isCleanApis(baseUrl: String): Boolean =
        officialHost(baseUrl, "cleanapis.com") || officialHost(baseUrl, "www.cleanapis.com")

    fun cleanApisBudget(baseUrl: String, mode: ReasoningMode): Int? =
        if (!isCleanApis(baseUrl)) null else when (mode) {
            ReasoningMode.AUTO -> null
            ReasoningMode.LOW -> 2048
            ReasoningMode.MEDIUM -> 8192
            ReasoningMode.HIGH -> 16000
            ReasoningMode.EXTRA_HIGH -> 32000
            ReasoningMode.ULTRA -> 64000
        }

    fun isOpenRouter(baseUrl: String): Boolean = officialHost(baseUrl, "openrouter.ai")

    fun openRouterEffort(baseUrl: String, model: String, mode: ReasoningMode,
        catalogReasoningModels: Set<String> = emptySet()): String? {
        if (!isOpenRouter(baseUrl)) return null
        val id = model.trim().lowercase()
        if (id == "openrouter/free" || id == "openrouter/auto") return null
        if (mode == ReasoningMode.ULTRA) return null
        if (id in routerKnownExtra) return mode.effort
        if (id !in catalogReasoningModels || mode == ReasoningMode.EXTRA_HIGH) return null
        return mode.effort
    }

    /** These endpoints document the same top-level reasoning_effort field as OpenAI. */
    fun compatibleEffort(baseUrl: String, model: String, mode: ReasoningMode): String? {
        openAiEffort(baseUrl, model, mode)?.let { return it }
        val id = model.trim().lowercase()
        if (mode == ReasoningMode.ULTRA) return null
        if (officialHost(baseUrl, "api.groq.com")) {
            if (id == "meta-models/muse-spark-open") return mode.effort
            if (id in setOf("openai/gpt-oss-20b", "openai/gpt-oss-120b", "qwen/qwen3.8-27b") &&
                mode != ReasoningMode.EXTRA_HIGH) return mode.effort
        }
        return null
    }

    fun anthropicEffort(baseUrl: String, model: String, mode: ReasoningMode): String? {
        if (!mode.isExtended) return mode.effort
        if (!officialHost(baseUrl, "api.anthropic.com")) return null
        val id = model.trim().lowercase().replace('.', '-')
        val both = setOf("claude-sonnet-5-5", "claude-opus-5-5", "claude-haiku-5-5",
            "claude-sonnet-5", "claude-opus-5", "claude-opus-4-8", "claude-opus-4-7",
            "claude-fable-5", "claude-fable-5-1", "claude-fable-5-2")
        val maxOnly = setOf("claude-opus-4-6", "claude-sonnet-4-6")
        return mode.effort.takeIf { id in both || (mode == ReasoningMode.ULTRA && id in maxOnly) }
    }

    private fun officialHost(baseUrl: String, host: String): Boolean {
        val endpoint = runCatching { java.net.URI(baseUrl) }.getOrNull() ?: return false
        return endpoint.scheme == "https" && endpoint.host.equals(host, ignoreCase = true) &&
            endpoint.userInfo == null && endpoint.port in listOf(-1, 443)
    }
}
