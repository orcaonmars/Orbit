package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSearchTest {

    private val e = ChatSearch.ELLIPSIS
    private fun words(from: Int, until: Int) = (from until until).joinToString(" ") { "word$it" }

    @Test
    fun noMatchGivesNoRanges() {
        assertEquals(emptyList<IntRange>(), ChatSearch.matchRanges("hello there", "orbit"))
    }

    @Test
    fun oneMatch() {
        assertEquals(listOf(6..10), ChatSearch.matchRanges("hello world", "world"))
    }

    @Test
    fun matchIsCaseInsensitive() {
        assertEquals(listOf(0..4, 6..10), ChatSearch.matchRanges("orbit ORBIT", "Orbit"))
    }

    @Test
    fun twoMatchesLeftToRight() {
        assertEquals(listOf(0..2, 8..10), ChatSearch.matchRanges("cat and cat", "cat"))
    }

    @Test
    fun overlappingPatternGivesNonOverlappingRanges() {
        assertEquals(listOf(0..1, 2..3), ChatSearch.matchRanges("aaaa", "aa"))
    }

    @Test
    fun blankQueryGivesNoRangesAndNoSnippet() {
        assertEquals(emptyList<IntRange>(), ChatSearch.matchRanges("anything", "   "))
        assertEquals(emptyList<IntRange>(), ChatSearch.matchRanges("anything", ""))
        assertNull(ChatSearch.snippet("anything", " "))
    }

    @Test
    fun queryIsTrimmed() {
        assertEquals(listOf(4..8), ChatSearch.matchRanges("say hello", "  hello "))
    }

    @Test
    fun plainDropsFenceLinesButKeepsCode() {
        val md = "Run this:\n```kotlin\nval x = 1\n```\nDone."
        assertEquals("Run this: val x = 1 Done.", ChatSearch.plain(md))
    }

    @Test
    fun plainReadsTablesAndListsAsWords() {
        val md = "| Option | Cost |\n|---|:---:|\n| Train | Low |\n\n## Day 2\n- **Morning:** train\n> quoted\n---\n1. One"
        assertEquals("Option · Cost Train · Low Day 2 Morning: train quoted 1. One", ChatSearch.plain(md))
    }

    @Test
    fun plainKeepsCodeLinesAsWritten() {
        val md = "```diff\n- old line\n| not a table |\n```"
        assertEquals("- old line | not a table |", ChatSearch.plain(md))
    }

    @Test
    fun plainStripsEmphasisAndHeadingsAndCollapsesNewlines() {
        val md = "# Title\n\n**bold** and _it_ with `code`\n\n\nend"
        assertEquals("Title bold and it with code end", ChatSearch.plain(md))
    }

    @Test
    fun snippetNearStartHasNoLeadingEllipsis() {
        val md = "Orbit is a calm assistant. " + words(0, 30)
        val s = ChatSearch.snippet(md, "orbit")
        assertNotNull(s)
        assertTrue(s!!.startsWith("Orbit is"))
        assertTrue(s.endsWith(e))
    }

    @Test
    fun snippetInMiddleOfLongTextHasBothEllipses() {
        val md = words(0, 30) + " TARGET " + words(30, 60)
        val s = ChatSearch.snippet(md, "target")!!
        assertTrue(s.startsWith(e))
        assertTrue(s.endsWith(e))
        assertTrue(s.contains("TARGET"))
        assertTrue(s.length <= 2 * ChatSearch.SNIPPET_RADIUS + "TARGET".length + 2 * 8 + 2)
    }

    @Test
    fun snippetNearEndHasNoTrailingEllipsis() {
        val md = words(0, 30) + " the end Orbit"
        val s = ChatSearch.snippet(md, "orbit")!!
        assertTrue(s.startsWith(e))
        assertFalse(s.endsWith(e))
        assertTrue(s.endsWith("the end Orbit"))
    }

    @Test
    fun shortTextSnippetIsWholeText() {
        assertEquals("Hi **there** friend".let { ChatSearch.plain(it) }, ChatSearch.snippet("Hi **there** friend", "there"))
    }

    @Test
    fun queryWithMarkdownSymbolsStillGetsASnippet() {
        assertEquals("use snake_case names", ChatSearch.snippet("use snake_case\nnames", "snake_case"))
        assertEquals("the **bold** part", ChatSearch.snippet("the **bold** part", "**bold**"))
    }

    @Test
    fun noMatchSnippetIsNull() {
        assertNull(ChatSearch.snippet(words(0, 50), "orbit"))
    }

    @Test
    fun snippetNeverCutsAWord() {
        val all = (0 until 80).map { "word$it" }.toSet() + "TARGET"
        val md = words(0, 40) + " TARGET " + words(40, 80)
        val s = ChatSearch.snippet(md, "target")!!
        val body = s.removePrefix(e).removeSuffix(e)
        body.split(' ').forEach { token -> assertTrue("cut word: $token", token in all) }
    }

    @Test
    fun snippetRangesFindQueryInsideSnippet() {
        val md = words(0, 30) + " find Orbit here " + words(30, 60)
        val s = ChatSearch.snippet(md, "ORBIT")!!
        val ranges = ChatSearch.snippetRanges(s, "ORBIT")
        assertEquals(1, ranges.size)
        assertEquals("Orbit", s.substring(ranges[0]))
    }
}
