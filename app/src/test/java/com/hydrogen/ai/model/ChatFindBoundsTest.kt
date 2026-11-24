package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFindBoundsTest {
    private fun message(text: String) = ChatMessage(
        id = "m1",
        correlationId = "corr",
        role = MessageRole.ASSISTANT,
        content = text,
        createdAtEpochMs = 1L
    )

    @Test fun longQueriesNeverBecomeFalsePositivePrefixSearches() {
        val query = "x".repeat(ChatFind.MAX_QUERY_LENGTH + 1)
        val text = "x".repeat(ChatFind.MAX_QUERY_LENGTH)
        assertTrue(ChatFind.matches(listOf(message(text)), query).isEmpty())
    }

    @Test fun boundaryLengthQueryStillMatches() {
        val query = "A".repeat(ChatFind.MAX_QUERY_LENGTH)
        assertEquals(listOf(0), ChatFind.matches(listOf(message(query.lowercase())), query))
    }

    @Test fun generatedExcerptsAreBoundedEvenIfCallerRequestsMillionsOfCharacters() {
        val content = "intro ".repeat(1000) + "SEARCH HERE" + " ending".repeat(1000)
        val excerpt = ChatFind.preview(message(content), "SEARCH HERE", maxChars = Int.MAX_VALUE)
        assertTrue(excerpt.contains("SEARCH HERE"))
        assertTrue(excerpt.length <= 246)
    }

    @Test fun zeroLengthOrOverlongQueriesYieldNoPreview() {
        assertEquals("", ChatFind.preview(message("A B"), "", 150))
        assertEquals("", ChatFind.preview(message("A B"), "x".repeat(1000), 150))
        assertEquals("", ChatFind.preview(message("A B"), "B", 0))
    }
}
