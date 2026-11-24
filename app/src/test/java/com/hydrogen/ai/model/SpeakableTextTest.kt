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

    @Test
    fun linksKeepSpokenTextAndDropDestinations() {
        val spoken = SpeakableText.fromMarkdown(
            "See [the docs](https://example.com/a_(b) \"Docs\") and " +
                "[site](<https://example.com/x y>) or https://x.y/z?q=1&b=2."
        )
        assertEquals("See the docs and site or link.", spoken)
    }

    @Test
    fun referenceLinksAndImagesKeepTheirLabels() {
        val spoken = SpeakableText.fromMarkdown(
            "Read [the guide][g] and ![diagram](img.png).\n\n[g]: https://example.com"
        )
        assertEquals("Read the guide and diagram.", spoken)
    }

    @Test
    fun fencedCodeHandlesTildesUnclosedFencesAndInlineRuns() {
        val spoken = SpeakableText.fromMarkdown(
            "Before\n~~~kotlin\nval x = 1\n~~~\nBetween\n```\nstreaming"
        )
        assertEquals(
            "Before\n${SpeakableText.CODE_BLOCK_NOTE}\nBetween\n${SpeakableText.CODE_BLOCK_NOTE}",
            spoken
        )
        assertFalse(spoken.contains("val x"))
        assertFalse(spoken.contains("streaming"))

        // Triple backticks on one line are inline code, not a fence.
        assertEquals("use x here", SpeakableText.fromMarkdown("use ```x``` here"))
    }

    @Test
    fun tablesHandleEscapedPipesAndRowsWithoutOuterPipes() {
        assertEquals(
            "Name, Value\n\nA | B, 3",
            SpeakableText.fromMarkdown("Name | Value\n--- | ---\nA \\| B | 3")
        )
        assertEquals(
            "Step\n\nInstall",
            SpeakableText.fromMarkdown("| Step |\n| --- |\n| Install |")
        )
    }

    @Test
    fun commonSymbolsAndEmojiAreSpoken() {
        assertEquals(
            "Save 50 percent and go to next",
            SpeakableText.fromMarkdown("Save 50% & go -> next \uD83D\uDE00")
        )
        assertEquals(
            "A to B, C from D, 2 times 3",
            SpeakableText.fromMarkdown("A -> B, C <- D, 2 \u00D7 3")
        )
        assertEquals("Tom and Jerry", SpeakableText.fromMarkdown("Tom &amp; Jerry"))
        assertEquals("R and D", SpeakableText.fromMarkdown("R&D"))
    }

    @Test
    fun chunkKeepsAbbreviationsAndInitialsTogether() {
        assertEquals(
            listOf("Use e.g. this one.", "Dr. Smith agrees."),
            SpeakableText.chunk("Use e.g. this one. Dr. Smith agrees.", max = 20)
        )
        assertEquals(
            listOf("J. K. Rowling wrote it.", "Next sentence."),
            SpeakableText.chunk("J. K. Rowling wrote it. Next sentence.", max = 30)
        )
    }

    @Test
    fun chunkDoesNotSplitDecimalsOrUrls() {
        assertEquals(
            listOf("Pi is 3.14 today.", "Next."),
            SpeakableText.chunk("Pi is 3.14 today. Next.", max = 18)
        )
        assertEquals(
            listOf("Go to https://example.com/a.b now.", "Then stop."),
            SpeakableText.chunk("Go to https://example.com/a.b now. Then stop.", max = 40)
        )
    }

    @Test
    fun defaultChunksAreShortAndKeepEveryWord() {
        val text = "This is a short sentence. ".repeat(40)
        val chunks = SpeakableText.chunk(text)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= SpeakableText.MAX_SENTENCE_CHUNK })
        assertEquals(200, chunks.sumOf { it.split(Regex("\\s+")).size })
    }
}
