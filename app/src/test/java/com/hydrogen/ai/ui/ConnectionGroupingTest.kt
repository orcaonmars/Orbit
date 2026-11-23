package com.hydrogen.ai.ui

import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.SavedConnection
import com.hydrogen.ai.model.SavedConnections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The model menu groups models under their server (docs/ORBIT_DESIGN.md, "Header"). */
class ConnectionGroupingTest {
    private val studio = "http://10.0.2.2:1234/v1"
    private val other = "http://192.168.1.20:11434/v1"

    private fun relay(url: String, model: String) = SavedConnection(ProviderRoute.GEMINI_HYDROGEN_RELAY, url, model)

    @Test
    fun modelsOnOneServerShareAGroup() {
        val list = listOf(relay(studio, "gemma-3"), relay(studio, "qwen2.5"), relay(other, "llama3"))
        val groups = groupByServer(list)
        assertEquals(2, groups.size)
        assertEquals(listOf("gemma-3", "qwen2.5"), groups[0].second.map { it.modelId })
        assertEquals(listOf("llama3"), groups[1].second.map { it.modelId })
        assertEquals(SavedConnections.serverLabel(list[0]), groups[0].first)
    }

    @Test
    fun theMostRecentServerComesFirst() {
        // Most recent first: llama3 was used last, then gemma-3, then qwen2.5.
        val list = listOf(relay(other, "llama3"), relay(studio, "gemma-3"), relay(other, "phi4"), relay(studio, "qwen2.5"))
        val groups = groupByServer(list)
        assertEquals(listOf(SavedConnections.serverLabel(list[0]), SavedConnections.serverLabel(list[1])), groups.map { it.first })
        assertEquals(listOf("llama3", "phi4"), groups[0].second.map { it.modelId })
        assertEquals(listOf("gemma-3", "qwen2.5"), groups[1].second.map { it.modelId })
    }

    @Test
    fun noModelsMeansNoGroups() {
        assertTrue(groupByServer(emptyList()).isEmpty())
    }
}
