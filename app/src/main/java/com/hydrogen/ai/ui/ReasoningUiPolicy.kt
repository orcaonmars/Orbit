package com.hydrogen.ai.ui

import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.ReasoningMode
import com.hydrogen.ai.model.ReasoningPolicy
import com.hydrogen.ai.network.HttpAnthropicMessagesBackend

internal fun reasoningOptions(route: ProviderRoute, baseUrl: String, model: String,
    catalogReasoningModels: Set<String> = emptySet()): List<ReasoningMode> =
    ReasoningMode.entries.filter { mode ->
        mode == ReasoningMode.AUTO || when (route) {
            ProviderRoute.GOOGLE_AI_STUDIO_DIRECT -> ReasoningPolicy.googleLevel(model, mode) != null
            ProviderRoute.GEMINI_HYDROGEN_RELAY -> ReasoningPolicy.compatibleEffort(baseUrl, model, mode) != null ||
                ReasoningPolicy.openRouterEffort(baseUrl, model, mode, catalogReasoningModels) != null ||
                (model.isNotBlank() && ReasoningPolicy.cleanApisBudget(baseUrl, mode) != null)
            ProviderRoute.ANTHROPIC_MESSAGES -> HttpAnthropicMessagesBackend.supportsAdaptiveThinking(model) &&
                ReasoningPolicy.anthropicEffort(baseUrl, model, mode) != null
            ProviderRoute.LOCAL_OFFLINE -> false
        }
    }

internal fun reasoningDetail(mode: ReasoningMode, cleanApisBudget: Boolean): String =
    if (!cleanApisBudget) mode.description else
        ReasoningPolicy.cleanApisBudget("https://cleanapis.com", mode)?.let { "Up to ${it} completion tokens" }
            ?: "Provider default (8,192 tokens)"

internal fun normalizeReasoning(config: com.hydrogen.ai.model.RelayConfig): com.hydrogen.ai.model.RelayConfig =
    if (config.reasoningMode in reasoningOptions(config.route, config.effectiveBaseUrl, config.modelId, config.openRouterReasoningModels)) config
    else config.copy(reasoningMode = ReasoningMode.AUTO)

internal fun reasoningAvailable(route: ProviderRoute, baseUrl: String, model: String): Boolean =
    reasoningOptions(route, baseUrl, model).size > 1
