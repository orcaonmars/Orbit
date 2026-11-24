package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSearchPunctuationTest {

    @Test
    fun matchesCurlyApostropheWithStraightQuote() {
        val text = "Don’t stop believing"
        val ranges = ChatSearch.matchRanges(text, "don't")
        assertEquals(listOf(0..4), ranges)
        assertTrue(ChatSearch.hasMatch(text, "don't"))
        assertTrue(ChatSearch.hasMatch("Don't stop", "don’t"))
    }

    @Test
    fun matchesSmartDoubleQuotesWithStraightDoubleQuotes() {
        val text = "He said “hello world” loudly"
        val ranges = ChatSearch.matchRanges(text, "\"hello world\"")
        assertEquals(listOf(8..20), ranges)
        assertTrue(ChatSearch.hasMatch(text, "\"hello world\""))
    }

    @Test
    fun matchesDashesAndNonBreakingSpaces() {
        val text = "state–of—the‑art\u00A0design"
        val ranges = ChatSearch.matchRanges(text, "state-of-the-art design")
        assertEquals(listOf(0..22), ranges)
        assertTrue(ChatSearch.hasMatch(text, "state-of-the-art design"))
    }
}
