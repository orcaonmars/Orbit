package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelCatalogSearchTest {
    @Test fun activeQueryRemainsClearableAfterCatalogShrinks() {
        assertEquals(true, ModelCatalogSearch.shouldShowSearch(100, 40, ""))
        assertEquals(true, ModelCatalogSearch.shouldShowSearch(3, 40, "nemotron"))
        assertEquals(true, ModelCatalogSearch.shouldShowSearch(0, 40, "nemotron"))
        assertEquals(false, ModelCatalogSearch.shouldShowSearch(3, 40, ""))
    }

    @Test fun searchMatchesEveryTermWithoutChangingProviderOrder() {
        val ids = listOf("qwen/qwen-3:free", "google/gemini-4", "qwen/qwen-3", "qwen/qwen-2:free")
        assertEquals(listOf(ids[0], ids[3]), ModelCatalogSearch.filter(ids, "  QWEN  free "))
        assertEquals(ids, ModelCatalogSearch.filter(ids, " "))
        assertEquals(emptyList<String>(), ModelCatalogSearch.filter(ids, "missing"))
    }
}
