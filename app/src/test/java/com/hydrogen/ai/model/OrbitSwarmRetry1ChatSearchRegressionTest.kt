package com.hydrogen.ai.model

import org.junit.Test
import org.junit.Assert.*

class OrbitSwarmRetry1ChatSearchRegressionTest {
    @Test
    fun testMatchRangesWithNewlines() {
        val text = "Hello\nWorld"
        val query = "world"
        // Before the patch, matchRanges would incorrectly treat the newline as ASCII and skip folding,
        // causing the range to be empty. After the patch, the newline is excluded from the ASCII fast path
        // and folding is applied, yielding a correct match.
        val ranges = ChatSearch.matchRanges(text, query)
        assertTrue("Expected a match across the newline", ranges.isNotEmpty())
        assertEquals(6, ranges[0].first)
        assertEquals(10, ranges[0].last)
    }
}