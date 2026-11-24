package com.hydrogen.ai.model

import org.junit.Test
import org.junit.Assert.assertEquals

class OrbitSwarmRetry2NightUserMemoryModelRegressionTest {
    @Test
    fun testHyphenatedDuplicateDetection() {
        val items = emptyList<com.hydrogen.ai.model.MemoryItem>()
        val first = UserMemory.add(items, "cooperate", "1", 0L)
        // Adding hyphenated version should be considered duplicate and not added
        val second = UserMemory.add(first, "co-operate", "2", 1L)
        assertEquals(1, second.size)
        assertEquals("cooperate", second[0].text)
    }
}