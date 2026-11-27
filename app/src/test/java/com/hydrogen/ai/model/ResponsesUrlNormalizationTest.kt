package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** The /responses suffix is trimmed like /chat/completions, so pasted docs addresses never double up. */
class ResponsesUrlNormalizationTest {

    @Test
    fun pastedResponsesAddressesNormalizeToTheBase() {
        assertEquals("https://api.example.com", ProviderPresets.normalizeBaseUrl("https://api.example.com/v1/responses"))
        assertEquals("https://api.example.com", ProviderPresets.normalizeBaseUrl("https://api.example.com/v1/responses/"))
        assertEquals("https://api.example.com/openai", ProviderPresets.normalizeBaseUrl("https://api.example.com/openai/responses"))
    }

    @Test
    fun responsesEndpointHonorsAVersionedBase() {
        assertEquals("https://api.example.com/v1/responses",
            ProviderPresets.openAiEndpoint("https://api.example.com", "responses"))
        assertEquals("https://example.com/api/paas/v4/responses",
            ProviderPresets.openAiEndpoint("https://example.com/api/paas/v4/", "responses"))
    }

    @Test
    fun modelListEndpointIsUnchangedByTheResponsesProtocol() {
        assertEquals("https://api.example.com/v1/models",
            ProviderPresets.openAiEndpoint(ProviderPresets.normalizeBaseUrl("https://api.example.com/v1/responses"), "models"))
        assertEquals("https://api.example.com/v1/responses",
            ProviderPresets.openAiEndpoint(ProviderPresets.normalizeBaseUrl("https://api.example.com/v1/models"), "responses"))
    }

    @Test
    fun responsesProtocolCanBeKeptOnTheSameOriginAsTheKey() {
        val chat = RelayConfig(route = ProviderRoute.GEMINI_HYDROGEN_RELAY, baseUrl = "https://api.example.com",
            modelId = "m")
        val responses = chat.copy(openAiProtocol = OpenAiProtocol.RESPONSES)
        assertEquals(ProviderPresets.originOf(chat.effectiveBaseUrl), ProviderPresets.originOf(responses.effectiveBaseUrl))
    }
}
