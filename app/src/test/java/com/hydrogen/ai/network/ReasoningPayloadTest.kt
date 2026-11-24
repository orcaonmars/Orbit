package com.hydrogen.ai.network

import com.hydrogen.ai.model.*
import com.hydrogen.ai.ui.reasoningOptions
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ReasoningPayloadTest {
    private val request = ChatDispatchRequest("test", "test",
        listOf(ChatMessage("u", "test", MessageRole.USER, "Hello", 1L)), emptyList())

    @Test fun extraHighIsOnlyOfferedWhereSupported() {
        val extra = ReasoningMode.EXTRA_HIGH
        assertEquals("xhigh", ReasoningPolicy.openAiEffort("https://api.openai.com", "gpt-6.2-sol", extra))
        assertNull(ReasoningPolicy.openAiEffort("https://api.openai.com", "gpt-5", extra))
        assertNull(ReasoningPolicy.googleLevel("gemini-3.8-flash", extra))
        assertEquals("xhigh", ReasoningPolicy.anthropicEffort("https://api.anthropic.com", "claude-sonnet-5-5", extra))
        assertNull(ReasoningPolicy.anthropicEffort("https://example.org", "claude-sonnet-5-5", extra))
        assertFalse(extra in reasoningOptions(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT, "", "gemini-3.8-flash"))
    }

    @Test fun openRouterReasoningDoesNotEnableUnknownOrFreeAutoRoutes() {
        assertEquals("high", ReasoningPolicy.openRouterEffort("https://openrouter.ai/api", "openai/gpt-5.4", ReasoningMode.HIGH))
        assertNull(ReasoningPolicy.openRouterEffort("https://openrouter.ai.evil.example/api", "openai/gpt-5.4", ReasoningMode.HIGH))
        assertNull(ReasoningPolicy.openRouterEffort("https://openrouter.ai/api", "openrouter/free", ReasoningMode.HIGH))
        assertNull(ReasoningPolicy.openRouterEffort("https://openrouter.ai/api", "openai/gpt-5.4", ReasoningMode.AUTO))
    }

    @Test fun openAiAndOpenRouterPayloadsUseDifferentFields() = runTest {
        val backend = HttpGeminiHydrogenRelayBackend()
        val ai = backend.buildPayload("gpt-5.4", true, request.copy(reasoningEffort = "xhigh")).body!!
        assertEquals("xhigh", ai["reasoning_effort"]!!.jsonPrimitive.content)
        assertFalse(ai.containsKey("reasoning"))
        val router = backend.buildPayload("openai/gpt-5.4", true, request.copy(openRouterReasoningEffort = "high")).body!!
        assertEquals("high", router["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertTrue(router["provider"]!!.jsonObject["require_parameters"]!!.jsonPrimitive.boolean)
        assertFalse(router.containsKey("reasoning_effort"))
        val auto = backend.buildPayload("unknown", false, request).body!!
        assertFalse(auto.containsKey("reasoning"))
        assertFalse(auto.containsKey("reasoning_effort"))
    }

    @Test fun geminiAndAnthropicPayloadsAndSafeFallback() = runTest {
        val google = HttpGoogleAiStudioBackend().buildPayload(request, thinkingLevel = "HIGH").body!!
        assertEquals("HIGH", google["generationConfig"]!!.jsonObject["thinkingConfig"]!!.jsonObject["thinkingLevel"]!!.jsonPrimitive.content)
        assertFalse(HttpGoogleAiStudioBackend().buildPayload(request).body!!.containsKey("generationConfig"))
        val claude = HttpAnthropicMessagesBackend()
        val deep = claude.buildPayload(request, "claude-sonnet-5-5", safe = false, effort = "xhigh").body!!
        assertEquals("xhigh", deep["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        val safe = claude.buildPayload(request, "claude-sonnet-5-5", safe = true, effort = "xhigh").body!!
        assertFalse(safe.containsKey("output_config"))
        assertFalse(safe.containsKey("thinking"))
    }

    @Test fun publicCatalogCapabilityDetectionDoesNotGuessFromName() {
        val body = """{"data":[{"id":"nvidia/model:free","supported_parameters":["reasoning","tools"]},
            {"id":"fake-reasoning-name","supported_parameters":["tools"]},{"id":"openrouter/free","supported_parameters":["reasoning"]},
            {"id":"malformed","supported_parameters":"reasoning"},null]}"""
        val models = HttpGeminiHydrogenRelayBackend.parseOpenRouterReasoningModels(body)
        assertEquals(setOf("nvidia/model:free", "openrouter/free"), models)
        val url = "https://openrouter.ai/api"
        assertEquals("medium", ReasoningPolicy.openRouterEffort(url, "nvidia/model:free", ReasoningMode.MEDIUM, models))
        assertNull(ReasoningPolicy.openRouterEffort(url, "nvidia/model:free", ReasoningMode.EXTRA_HIGH, models))
        assertNull(ReasoningPolicy.openRouterEffort(url, "openrouter/free", ReasoningMode.HIGH, models))
        assertNull(ReasoningPolicy.openRouterEffort("https://other.example/api", "nvidia/model:free", ReasoningMode.HIGH, models))
        assertEquals(listOf(ReasoningMode.AUTO, ReasoningMode.LOW, ReasoningMode.MEDIUM, ReasoningMode.HIGH),
            reasoningOptions(ProviderRoute.GEMINI_HYDROGEN_RELAY, url, "nvidia/model:free", models))
    }

    @Test fun groqOnlyUsesDocumentedEffortLevels() {
        val url = "https://api.groq.com/openai"
        assertEquals("high", ReasoningPolicy.compatibleEffort(url, "openai/gpt-oss-120b", ReasoningMode.HIGH))
        assertEquals("medium", ReasoningPolicy.compatibleEffort(url, "qwen/qwen3.8-27b", ReasoningMode.MEDIUM))
        assertEquals("xhigh", ReasoningPolicy.compatibleEffort(url, "meta-models/muse-spark-open", ReasoningMode.EXTRA_HIGH))
        assertNull(ReasoningPolicy.compatibleEffort(url, "openai/gpt-oss-120b", ReasoningMode.EXTRA_HIGH))
        assertNull(ReasoningPolicy.compatibleEffort(url, "qwen/qwen3-32b", ReasoningMode.LOW))
        assertNull(ReasoningPolicy.compatibleEffort("https://api.groq.com.fake.example", "openai/gpt-oss-120b", ReasoningMode.HIGH))
    }

    @Test fun modelCatalogKeepsHundredsOfSearchableIdsAndFreeModels() {
        val ids = (0 until 600).map { "provider/model-%04d".format(it) } + listOf("z/free-model:free", "z/embed")
        val normalized = HttpGeminiHydrogenRelayBackend.normalizeModelIds(ids)
        assertEquals(601, normalized.size)
        assertTrue("z/free-model:free" in normalized)
        assertFalse("z/embed" in normalized)
    }

    @Test fun keyProbeDoesNotMistakePublicCatalogOrEchoedKeyForVerification() {
        assertNull(HttpGeminiHydrogenRelayBackend.openRouterKeyStatus("""{"data":[{"id":"model"}]}"""))
        assertNull(HttpGeminiHydrogenRelayBackend.openRouterKeyStatus("""{"data":{"is_free_tier":"true"}}"""))
        val status = HttpGeminiHydrogenRelayBackend.openRouterKeyStatus("""{"data":{"is_free_tier":true,"label":"sensitive-label"}}""")!!
        assertTrue(status.contains("free tier"))
        assertFalse(status.contains("sensitive-label"))
    }

    @Test fun rejectedReasoningSettingHasActionableNonRetryableError() {
        val error = RelayErrorClassifier.classifyHttpError(400, """{"error":{"message":"Unsupported reasoning_effort"}}""")
        assertEquals(ProviderErrorCategory.BAD_REQUEST_400, error.category)
        assertEquals("provider_reasoning", error.sourceLayer)
        assertTrue(error.userMessage.contains("Automatic"))
        assertFalse(RelayErrorClassifier.isTransientError(error.category))
    }

    @Test fun cleanApisBudgetUsesDocumentedParameterOnly() = runTest {
        val base = "https://cleanapis.com/v1"
        val model = "claude-opus-4.8"
        assertEquals(ReasoningMode.entries.toList(),
            reasoningOptions(ProviderRoute.GEMINI_HYDROGEN_RELAY, base, model))
        assertNull(ReasoningPolicy.cleanApisBudget(base, ReasoningMode.AUTO))
        assertEquals(2048, ReasoningPolicy.cleanApisBudget(base, ReasoningMode.LOW))
        assertEquals(8192, ReasoningPolicy.cleanApisBudget(base, ReasoningMode.MEDIUM))
        assertEquals(16000, ReasoningPolicy.cleanApisBudget(base, ReasoningMode.HIGH))
        assertEquals(32000, ReasoningPolicy.cleanApisBudget(base, ReasoningMode.EXTRA_HIGH))
        assertEquals(64000, ReasoningPolicy.cleanApisBudget(base, ReasoningMode.ULTRA))
        assertNull(ReasoningPolicy.cleanApisBudget("https://cleanapis.com.evil.example", ReasoningMode.HIGH))
        assertNull(ReasoningPolicy.cleanApisBudget("http://cleanapis.com", ReasoningMode.HIGH))
        val body = HttpGeminiHydrogenRelayBackend().buildPayload(model, true,
            request.copy(completionTokenBudget = 16000)).body!!
        assertEquals(16000, body["max_tokens"]!!.jsonPrimitive.int)
        assertFalse(body.containsKey("reasoning_effort"))
        assertFalse(body.containsKey("reasoning"))
        val auto = HttpGeminiHydrogenRelayBackend().buildPayload(model, false, request).body!!
        assertFalse(auto.containsKey("max_tokens"))
    }

    @Test fun ultraIsNativeMaxOnlyOnKnownCompatibleClaudeModels() = runTest {
        val ultra = ReasoningMode.ULTRA
        val url = "https://api.anthropic.com"
        assertEquals("max", ReasoningPolicy.anthropicEffort(url, "claude-opus-5-5", ultra))
        assertEquals("max", ReasoningPolicy.anthropicEffort(url, "claude-opus-4-6", ultra))
        assertNull(ReasoningPolicy.anthropicEffort(url, "claude-opus-4-6", ReasoningMode.EXTRA_HIGH))
        assertNull(ReasoningPolicy.anthropicEffort("https://example.com", "claude-opus-5-5", ultra))
        assertNull(ReasoningPolicy.openAiEffort("https://api.openai.com", "gpt-6.2-sol", ultra))
        assertNull(ReasoningPolicy.openRouterEffort("https://openrouter.ai/api", "openai/gpt-5.4", ultra))
        assertNull(ReasoningPolicy.compatibleEffort("https://api.groq.com/openai", "meta-models/muse-spark-open", ultra))
        assertNull(ReasoningPolicy.googleLevel("gemini-3.8-flash", ultra))
        val body = HttpAnthropicMessagesBackend().buildPayload(request, "claude-opus-5-5", safe = false, effort = "max").body!!
        assertEquals("max", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals(64000, body["max_tokens"]!!.jsonPrimitive.int)
        assertEquals(ultra, ReasoningMode.fromStored("ULTRA"))
        assertEquals(ReasoningMode.LOW, ReasoningMode.fromStored("LOW"))
    }
    @Test fun unsupportedReasoningIsNormalizedWhenSwitchingModels() {
        val clean = RelayConfig(route = ProviderRoute.GEMINI_HYDROGEN_RELAY, baseUrl = "https://cleanapis.com",
            modelId = "opus", reasoningMode = ReasoningMode.ULTRA)
        assertEquals(ReasoningMode.ULTRA, com.hydrogen.ai.ui.normalizeReasoning(clean).reasoningMode)
        val unknown = clean.copy(baseUrl = "https://example.com", modelId = "unknown")
        assertEquals(ReasoningMode.AUTO, com.hydrogen.ai.ui.normalizeReasoning(unknown).reasoningMode)
        val google = clean.copy(route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT, modelId = "gemini-3.8-flash")
        assertEquals(ReasoningMode.AUTO, com.hydrogen.ai.ui.normalizeReasoning(google).reasoningMode)
    }
}
