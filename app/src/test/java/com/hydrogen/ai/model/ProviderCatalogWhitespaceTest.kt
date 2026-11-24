package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCatalogWhitespaceTest {
    private val sample by lazy { ProviderPreset.entries.first { it.title.contains(' ') } }

    @Test fun tabsBetweenTermsFindTheSameProvidersAsSpaces() {
        val plain = ProviderCatalog.search(sample.title)
        assertTrue("Expected a title to match its own provider", plain.contains(sample))
        assertEquals(plain, ProviderCatalog.search(sample.title.replace(' ', '\t')))
    }

    @Test fun newlinesAndRepeatedSpacesFindTheSameProviders() {
        val plain = ProviderCatalog.search(sample.title)
        val noisy = sample.title.replace(" ", " \n\t ")
        assertEquals(plain, ProviderCatalog.search(noisy))
    }

    @Test fun groupedSearchRetainsProviderGroupsAndOrder() {
        val plain = ProviderCatalog.grouped(sample.title)
        val withNewlines = ProviderCatalog.grouped(sample.title.replace(' ', '\n'))
        assertEquals(plain, withNewlines)
    }

    @Test fun whitespaceOnlyQueryShowsAllPresets() {
        assertEquals(ProviderPreset.entries, ProviderCatalog.search("\t\n\r   "))
    }
}
