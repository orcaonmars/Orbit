package com.hydrogen.ai.model

/** Search suggestions are source-backed presets, not proof that a service is reachable. */
object ProviderCatalog {
    /**
     * Preserve the provider's curated order. Search all group names, titles, descriptions,
     * and endpoint hostnames, without ever matching API keys or user chat content.
     *
     * Case, underscores and hyphens are folded on both sides, so "lm-studio", "LM_Studio" and "lm studio" find the
     * same card and a hostname copied from a service's docs matches however it is punctuated.
     */
    fun search(query: String, presets: List<ProviderPreset> = ProviderPreset.entries): List<ProviderPreset> {
        val terms = fold(query).split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) return presets
        return presets.filter { preset ->
            val searchable = fold(
                listOf(preset.title, preset.subtitle, preset.group.label, preset.name, preset.hostLabel)
                    .joinToString(" ")
            )
            terms.all { term -> term in searchable }
        }
    }

    /**
     * Groups of matching providers in catalogue order. A group with no match is left out, so the sheet never shows an
     * empty section under a label.
     */
    fun grouped(
        query: String,
        presets: List<ProviderPreset> = ProviderPreset.entries
    ): Map<PresetGroup, List<ProviderPreset>> {
        val matching = search(query, presets)
        return PresetGroup.entries.associateWith { group ->
            matching.filter { it.group == group }
        }.filterValues { it.isNotEmpty() }
    }

    /** Lowercases and turns separators into single spaces so a query and the indexed text fold the same way. */
    private fun fold(text: String): String =
        text.lowercase().replace('-', ' ').replace('_', ' ').replace('.', ' ').replace(':', ' ')
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ")
}
