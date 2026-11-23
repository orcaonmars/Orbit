package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakableTextTest {

    @Test
    fun stripsMarkdownSymbols() {
        val spoken = SpeakableText.fromMarkdown("## Title\n\n**Bold** and *italic* with `code` and ~~old~~.\n- one\n- two")
        assertEquals("Title\n\nBold and italic with code and old.\none\ntwo", spoken)
    }

    @Test
    fun replacesCodeBlocksAndUrls() {
        val spoken = SpeakableText.fromMarkdown(
            "Run this:\n```python\nprint('hi')\n```\nSee [the docs](https://example.com) or https://x.y/z"
        )
        assertTrue(spoken.contains(SpeakableText.CODE_BLOCK_NOTE))
        assertFalse(spoken.contains("print"))
        assertFalse(spoken.contains("https"))
        assertTrue(spoken.contains("See the docs or link"))
    }

    @Test
    fun keepsUnderscoresInsideWords() {
        assertEquals("use snake_case_names here", SpeakableText.fromMarkdown("use snake_case_names here"))
    }

    @Test
    fun tablesBecomeCommaLists() {
        val spoken = SpeakableText.fromMarkdown("| A | B |\n|---|---|\n| 1 | 2 |")
        assertEquals("A, B\n\n1, 2", spoken)
    }

    @Test
    fun chunksStayUnderLimitAndKeepAllWords() {
        val sentence = "This is a fairly ordinary sentence. "
        val text = sentence.repeat(400)
        val chunks = SpeakableText.chunk(text, max = 500)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 500 })
        assertEquals(text.trim().split(Regex("\\s+")).size, chunks.sumOf { it.split(Regex("\\s+")).size })
    }

    @Test
    fun hugeSentenceIsHardSplit() {
        val chunks = SpeakableText.chunk("word ".repeat(300), max = 100)
        assertTrue(chunks.all { it.length <= 100 })
        assertEquals(300, chunks.sumOf { it.split(' ').size })
    }

    @Test
    fun emptyTextHasNoChunks() {
        assertTrue(SpeakableText.chunk("   ").isEmpty())
    }
}
