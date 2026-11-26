package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private data class SyntheticChat(val title: String, val messages: List<String>)

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
        assertEquals("Option, Cost Train, Low Day 2 Morning: train quoted 1. One", ChatSearch.plain(md))
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

    @Test
    fun matchIgnoresDiacriticsInEitherDirection() {
        assertEquals(listOf(0..5), ChatSearch.matchRanges("Résumé writing", "resume"))
        assertEquals(listOf(0..5), ChatSearch.matchRanges("resume writing", "résumé"))
        assertEquals(listOf(0..3), ChatSearch.matchRanges("CAFÉ au lait", "cafe"))
        assertEquals(listOf(0..5), ChatSearch.matchRanges("RÉSUMÉ writing", "resume"))
        // Ranges still point into the original text after an accented character shifts nothing.
        assertEquals(listOf(3..3), ChatSearch.matchRanges("aé b", "b"))
        assertEquals(listOf(0..0, 8..8), ChatSearch.matchRanges("é orbit é", "é"))
    }

    @Test
    fun decomposedAccentHighlightsTheWholeLetter() {
        val text = "cafe\u0301 time"
        assertEquals(10, text.length)
        assertEquals(listOf(0..4), ChatSearch.matchRanges(text, "cafe"))
        assertEquals(listOf(0..4), ChatSearch.matchRanges(text, "café"))
    }

    @Test
    fun letterExpansionsMapBackToOriginalRangesWithoutOverlap() {
        assertEquals(listOf(0..5), ChatSearch.matchRanges("Straße", "strasse"))
        assertEquals(listOf(0..9), ChatSearch.matchRanges("smørrebrød", "smorrebrod"))
        assertEquals(listOf(0..4), ChatSearch.matchRanges("Œuvre", "oeuvre"))
        assertEquals(listOf(0..2), ChatSearch.matchRanges("ﬁle", "file"))
        // "ß" folds to "ss", so a one-letter query can offer the same source character twice; never overlap.
        assertEquals(listOf(0..0, 1..1), ChatSearch.matchRanges("ßs", "s"))
    }

    @Test
    fun hasMatchIsCaseAndDiacriticInsensitiveAndRejectsBlanks() {
        assertTrue(ChatSearch.hasMatch("Résumé", "RESUME"))
        assertTrue(ChatSearch.hasMatch("resume", "Résumé"))
        assertTrue(ChatSearch.hasMatch("cafe\u0301", "café"))
        assertFalse(ChatSearch.hasMatch("Résumé", "orbit"))
        assertFalse(ChatSearch.hasMatch("Résumé", "   "))
        assertFalse(ChatSearch.hasMatch("", "orbit"))
    }

    @Test
    fun nonSpacingMarksFoldButIndicVowelSignsStay() {
        // The Devanagari vowel signs are spacing marks, part of the letter, so "की" must not match "कि".
        assertTrue(ChatSearch.hasMatch("कि", "कि"))
        assertFalse(ChatSearch.hasMatch("कि", "की"))
    }

    @Test
    fun snippetFindsAccentedTextAndHighlightsIt() {
        val md = "Le café est bon"
        val s = ChatSearch.snippet(md, "cafe")!!
        assertEquals(md, s)
        val ranges = ChatSearch.snippetRanges(s, "CAFE")
        assertEquals(listOf(3..6), ranges)
        assertEquals("café", s.substring(ranges.single()))
    }

    @Test
    fun snippetFindsDiacriticsInsideMarkdown() {
        val s = ChatSearch.snippet("Use **résumé** tips", "resume")!!
        assertEquals("Use résumé tips", s)
        val range = ChatSearch.snippetRanges(s, "resume").single()
        assertEquals("résumé", s.substring(range))
    }

    @Test
    fun thousandSyntheticChatsSearchCorrectlyAndQuickly() {
        val chats = (0 until 1_000).map { i ->
            val title = if (i % 100 == 0) "Orbit review $i" else "Conversation $i"
            val messages = (0 until 3).map { m ->
                val key = i + m
                when {
                    key % 250 == 0 -> "The orbit path for $i/$m is stable."
                    key % 97 == 0 -> "A résumé of the plan for $i/$m."
                    else -> "Message $m in chat $i with ordinary words and details."
                }
            }
            SyntheticChat(title, messages)
        }

        fun search(query: String): List<SyntheticChat> = chats.filter { chat ->
            ChatSearch.hasMatch(chat.title, query) ||
                chat.messages.any { ChatSearch.hasMatch(ChatSearch.plain(it), query) }
        }

        // Expected count is derived from the construction rules, not from ChatSearch.
        val expected = (0 until 1_000).count { i ->
            i % 100 == 0 || (0 until 3).any { m -> (i + m) % 250 == 0 }
        }
        assertEquals(expected, search("orbit").size)
        assertTrue(search("resume").isNotEmpty())

        repeat(2) { search("orbit") } // warm up JIT and regex caches
        val startedAt = System.nanoTime()
        val found = search("orbit")
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0
        assertEquals(expected, found.size)
        assertTrue("searching 1,000 chats took ${elapsedMs}ms", elapsedMs < 200.0)
    }
    @Test
    fun fencedCodePreservesSearchableOperatorsAndIdentifiers() {
        val fence = String(CharArray(3) { 96.toChar() })
        val md = "$fence" + "kotlin\nval snake_case = a * b\n$fence"
        assertEquals("val snake_case = a * b", ChatSearch.plain(md))
        assertTrue(ChatSearch.hasMatch(ChatSearch.plain(md), "snake_case"))
    }

    @Test
    fun snippetOfLongUnbrokenTextRemainsBounded() {
        val md = "a".repeat(20000) + "TARGET" + "b".repeat(20000)
        val snippet = ChatSearch.snippet(md, "TARGET")!!
        assertTrue(snippet.length <= 140)
        assertTrue(snippet.contains("TARGET"))
        assertTrue(snippet.startsWith(e) && snippet.endsWith(e))
    }
}
