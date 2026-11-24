package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterFreePresetTest {
    @Test fun newOpenRouterProfilesDefaultToZeroCreditFreeRouter() {
        assertEquals("openrouter/free", ProviderPreset.OPENROUTER.defaultModelId)
        assertEquals(listOf("openrouter/free"), ProviderPreset.OPENROUTER.suggestedModels)
        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            ProviderPresets.openAiEndpoint(ProviderPreset.OPENROUTER.defaultBaseUrl, "chat/completions")
        )
    }

    @Test fun freeModeHidesPaidModelIdsAndKeepsFreeRouterFirst() {
        val catalog = listOf(
            "anthropic/claude-sonnet-4",
            "nvidia/nemotron:free",
            "openrouter/free",
            "google/gemini-pro",
            "qwen/qwen-coder:free"
        )
        assertEquals(
            listOf("openrouter/free", "nvidia/nemotron:free", "qwen/qwen-coder:free"),
            ProviderPresets.openRouterModels(catalog, freeOnly = true)
        )
        assertEquals(catalog, ProviderPresets.openRouterModels(catalog, freeOnly = false))
    }

    @Test fun noFetchedModelsStillOffersFreeRouterWithoutCredits() {
        assertEquals(listOf("openrouter/free"), ProviderPresets.openRouterModels(emptyList(), freeOnly = true))
    }

    @Test fun explicitlyFreeIdsDoNotConfuseZeroPriceWithFreeVariant() {
        assertTrue(ProviderPresets.isOpenRouterFreeModel("openrouter/free"))
        assertTrue(ProviderPresets.isOpenRouterFreeModel(" qwen/some-model:free "))
        assertFalse(ProviderPresets.isOpenRouterFreeModel("qwen/some-model"))
        assertFalse(ProviderPresets.isOpenRouterFreeModel("something/free"))
        assertFalse(ProviderPresets.isOpenRouterFreeModel(""))
    }
}
