package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCatalogTest {
    @Test fun blank_query_returns_ordered_catalog() {
        assertEquals(ProviderPreset.entries, ProviderCatalog.search("   "))
    }

    @Test fun search_matches_service_host_description_and_group() {
        assertEquals(listOf(ProviderPreset.OPENROUTER), ProviderCatalog.search("openrouter"))
        assertEquals(listOf(ProviderPreset.ANTHROPIC), ProviderCatalog.search("api.anthropic.com"))
        assertTrue(ProviderPreset.LM_STUDIO in ProviderCatalog.search("pc"))
        assertTrue(ProviderPreset.LM_STUDIO in ProviderCatalog.search("on your pc"))
        assertEquals(emptyList<ProviderPreset>(), ProviderCatalog.search("definitelynotaservice"))
    }

    @Test fun two_words_must_match_same_provider_and_no_cross_group_leak() {
        assertEquals(listOf(ProviderPreset.GOOGLE_GEMINI), ProviderCatalog.search("google gemini"))
        assertFalse(ProviderPreset.CUSTOM in ProviderCatalog.search("anthropic"))
        assertEquals(ProviderPreset.entries.size, ProviderCatalog.grouped("").values.sumOf { it.size })
    }

    @Test fun grouping_removes_empty_sections_but_keeps_catalog_order() {
        val result = ProviderCatalog.grouped("groq")
        assertEquals(listOf(PresetGroup.HOSTED), result.keys.toList())
        assertEquals(listOf(ProviderPreset.GROQ), result[PresetGroup.HOSTED])
    }
}
