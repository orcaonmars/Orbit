package com.hydrogen.ai.model

object ModelCatalogSearch {
    /** Keep the clear control available when a refresh or free-only filter shrinks the catalog. */
    fun shouldShowSearch(modelCount: Int, visibleLimit: Int, query: String): Boolean =
        modelCount > visibleLimit || query.isNotBlank()

    fun filter(ids: List<String>, query: String): List<String> {
        val terms = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return ids.filter { id -> terms.all { id.contains(it, ignoreCase = true) } }
    }
}
