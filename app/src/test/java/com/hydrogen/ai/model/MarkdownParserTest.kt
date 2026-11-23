package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    private fun plain(spans: List<MdSpan>) = spans.joinToString("") { it.text }

    @Test
    fun fencedCodeKeepsContentVerbatimAndLanguage() {
        val md = "Here:\n```kotlin\nval x = **not bold**\n\n  indented()\n```\nDone."
        val blocks = MarkdownParser.parse(md)
        assertEquals(3, blocks.size)
        val code = blocks[1] as MdBlock.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("val x = **not bold**\n\n  indented()", code.code)
        assertTrue(code.closed)
        assertEquals("Done.", plain((blocks[2] as MdBlock.Paragraph).spans))
    }

    @Test
    fun unclosedFenceWhileStreamingStillShowsCode() {
        val blocks = MarkdownParser.parse("```python\nprint('hi')")
        val code = blocks.single() as MdBlock.CodeBlock
        assertEquals("python", code.language)
        assertEquals("print('hi')", code.code)
        assertFalse(code.closed)
    }

    @Test
    fun oneLineTripleBackticksDoNotSwallowTheReply() {
        val blocks = MarkdownParser.parse("```inline``` text\nnext line")
        assertTrue(blocks.single() is MdBlock.Paragraph)
    }

    @Test
    fun blockStructuresAreRecognised() {
        val md = "# Title\n- one\n  - nested\n2. two\n> quoted\n---\nplain line\nsecond line"
        val blocks = MarkdownParser.parse(md)
        assertEquals(1, (blocks[0] as MdBlock.Heading).level)
        val bullet = blocks[1] as MdBlock.ListItem
        assertEquals("•", bullet.marker)
        assertEquals(0, bullet.indent)
        assertEquals(1, (blocks[2] as MdBlock.ListItem).indent)
        assertEquals("2.", (blocks[3] as MdBlock.ListItem).marker)
        assertEquals("quoted", plain((blocks[4] as MdBlock.Quote).spans))
        assertEquals(MdBlock.Rule, blocks[5])
        assertEquals("plain line\nsecond line", plain((blocks[6] as MdBlock.Paragraph).spans))
    }

    @Test
    fun linesUnderAListItemStayInThatItem() {
        val md = "1. 1/2 + 1/4\n   **Answer:** 3/4\n2. 2/3 + 1/6\n\n   **Answer:** 5/6\n\nAfter the list\n- a\nlazy line\n- b\n| x | y |\n|---|---|\n| 1 | 2 |"
        val blocks = MarkdownParser.parse(md)
        val first = blocks[0] as MdBlock.ListItem
        assertEquals("1/2 + 1/4\nAnswer: 3/4", plain(first.spans))
        assertTrue(first.spans.first { it.text == "Answer:" }.bold)
        assertEquals("2/3 + 1/6\n\nAnswer: 5/6", plain((blocks[1] as MdBlock.ListItem).spans))
        assertEquals("After the list", plain((blocks[2] as MdBlock.Paragraph).spans))
        assertEquals("a\nlazy line", plain((blocks[3] as MdBlock.ListItem).spans))
        assertEquals("b", plain((blocks[4] as MdBlock.ListItem).spans))
        assertTrue(blocks[5] is MdBlock.Table)
        assertEquals(6, blocks.size)
    }

    @Test
    fun inlineFormattingIsParsed() {
        val spans = MarkdownParser.parseInline("a **bold** and *it* and `code()` end")
        assertEquals("a bold and it and code() end", plain(spans))
        assertTrue(spans.first { it.text == "bold" }.bold)
        assertTrue(spans.first { it.text == "it" }.italic)
        assertTrue(spans.first { it.text == "code()" }.code)
        assertFalse(spans.first { it.text == "a " }.bold)
    }

    @Test
    fun ordinaryTextIsNotMangled() {
        for (text in listOf("my_file_name.txt", "2 * 3 * 4 = 24", "a ** b", "price_usd and x_y", "unclosed **bold")) {
            val spans = MarkdownParser.parseInline(text)
            assertEquals(text, plain(spans))
            assertTrue(text, spans.none { it.bold || it.italic })
        }
    }

    @Test
    fun onlyWebLinksAreClickable() {
        val ok = MarkdownParser.parseInline("see [docs](https://example.com/a) now")
        assertEquals("see docs now", plain(ok))
        assertEquals("https://example.com/a", ok.first { it.text == "docs" }.url)
        val bad = MarkdownParser.parseInline("[click](javascript:alert(1))")
        assertNull(bad.first { it.text == "click" }.url)
        assertFalse(MarkdownParser.isSafeUrl("intent://scan#Intent;end"))
    }

    @Test
    fun plainReplyRoundTripsAsOneParagraph() {
        val blocks = MarkdownParser.parse("Hello there.\nHow can I help?")
        assertEquals("Hello there.\nHow can I help?", plain((blocks.single() as MdBlock.Paragraph).spans))
        assertTrue(MarkdownParser.parse("").isEmpty())
    }
}
