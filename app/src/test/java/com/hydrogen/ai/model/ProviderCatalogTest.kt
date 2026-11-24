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

    @Test fun separators_and_case_fold_on_both_sides() {
        // A product name typed the way the vendor writes it, and a hostname pasted with the wrong separator.
        assertEquals(listOf(ProviderPreset.LM_STUDIO), ProviderCatalog.search("lm-studio"))
        assertEquals(listOf(ProviderPreset.LM_STUDIO), ProviderCatalog.search("LM_Studio"))
        // The custom Anthropic-compatible route also matches both search terms.
        assertEquals(listOf(ProviderPreset.ANTHROPIC, ProviderPreset.CUSTOM_ANTHROPIC), ProviderCatalog.search("api-anthropic"))
        assertEquals(listOf(ProviderPreset.OPENROUTER), ProviderCatalog.search("OPENROUTER"))
        // "Another service" is matched by its own hyphenated form, in the group it is filed under.
        assertEquals(
            listOf(ProviderPreset.CUSTOM, ProviderPreset.CUSTOM_ANTHROPIC),
            ProviderCatalog.search("another-service")
        )
        assertEquals(
            listOf(ProviderPreset.CUSTOM, ProviderPreset.CUSTOM_ANTHROPIC),
            ProviderCatalog.grouped("another-service")[PresetGroup.OTHER]
        )
    }

    @Test fun dotted_and_colon_separated_names_match_without_reordering() {
        assertEquals(listOf(ProviderPreset.LM_STUDIO), ProviderCatalog.search("LM.Studio"))
        assertEquals(listOf(ProviderPreset.GOOGLE_GEMINI), ProviderCatalog.search("google:gemini"))
        assertEquals(ProviderCatalog.search("google gemini"), ProviderCatalog.search("google" + "\t" + "gemini"))
        assertEquals(ProviderPreset.entries, ProviderCatalog.search(" : . _ - "))
    }

    @Test fun separator_only_query_is_still_a_blank_query() {
        assertEquals(ProviderPreset.entries, ProviderCatalog.search(" - _ "))
        assertEquals(ProviderPreset.entries.size, ProviderCatalog.grouped(" - ").values.sumOf { it.size })
    }

    @Test fun grouping_keeps_the_supplied_catalog_and_group_order() {
        val subset = listOf(ProviderPreset.ANTHROPIC, ProviderPreset.HYDROGEN_RELAY)
        val result = ProviderCatalog.grouped("", subset)
        assertEquals(listOf(PresetGroup.HOSTED, PresetGroup.PC), result.keys.toList())
        assertEquals(listOf(ProviderPreset.ANTHROPIC), result[PresetGroup.HOSTED])
        assertEquals(listOf(ProviderPreset.HYDROGEN_RELAY), result[PresetGroup.PC])
        assertEquals(ProviderPresets.inGroup(PresetGroup.HOSTED), ProviderCatalog.grouped("")[PresetGroup.HOSTED])
    }

    @Test fun cleanApisIsSearchableFeaturedAndUsesFixedEndpoint() {
        val preset = ProviderPreset.CLEAN_APIS
        assertEquals(listOf(preset), ProviderCatalog.search("cleanapi"))
        assertTrue(preset in ProviderPresets.FEATURED_HOSTED)
        assertEquals(preset, ProviderPresets.hostedPresetFor("https://cleanapis.com/v1"))
        assertEquals("https://cleanapis.com/v1/chat/completions",
            ProviderPresets.openAiEndpoint(preset.defaultBaseUrl, "chat/completions"))
        assertFalse(preset.showsBaseUrl)
        assertEquals(listOf(ProviderPreset.CUSTOM, ProviderPreset.CUSTOM_ANTHROPIC),
            ProviderCatalog.search("custom providers"))
    }
}
