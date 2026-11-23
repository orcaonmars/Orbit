package com.hydrogen.ai.model

/** Search suggestions are source-backed presets, not proof that a service is reachable. */
object ProviderCatalog {
    /**
     * Preserve the provider's curated order. Search all group names, titles, descriptions,
     * and endpoint hostnames, without ever matching API keys or user chat content.
     */
    fun search(query: String, presets: List<ProviderPreset> = ProviderPreset.entries): List<ProviderPreset> {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return presets
        return presets.filter { preset ->
            val searchable = listOf(
                preset.title,
                preset.subtitle,
                preset.group.label,
                preset.name.replace('_', ' '),
                preset.hostLabel
            ).joinToString(" ").lowercase()
            terms.all { term -> term in searchable }
        }
    }

    /** Available labels are stable even when there are no matches in a group. */
    fun grouped(query: String): Map<PresetGroup, List<ProviderPreset>> {
        val matching = search(query)
        return PresetGroup.entries.associateWith { group ->
            matching.filter { it.group == group }
        }.filterValues { it.isNotEmpty() }
    }
}
