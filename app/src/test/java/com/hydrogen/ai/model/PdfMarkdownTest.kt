package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfMarkdownTest {

    @Test
    fun a_reply_without_tags_is_one_text_part() {
        assertEquals(listOf(ReplyPart.Text("Just text.")), PdfMarkdown.splitReply("Just text."))
        assertFalse(PdfMarkdown.hasDocument("A <b>tag</b> and the word pdf."))
    }

    @Test
    fun a_worksheet_reply_splits_into_text_and_a_document() {
        val reply = "Here is your worksheet.\n\n<pdf title=\"Fractions worksheet\">\n# Fractions\n\n1. ½ + ¼\n   **Answer:** ¾\n</pdf>\n\nCheck question 1."
        val parts = PdfMarkdown.splitReply(reply)
        assertEquals(3, parts.size)
        assertEquals(ReplyPart.Text("Here is your worksheet."), parts[0])
        val doc = parts[1] as ReplyPart.Document
        assertEquals("Fractions worksheet", doc.title)
        assertEquals("# Fractions\n\n1. ½ + ¼\n   **Answer:** ¾", doc.markdown)
        assertTrue(doc.complete)
        assertEquals(ReplyPart.Text("Check question 1."), parts[2])
    }

    @Test
    fun an_unclosed_document_is_incomplete_and_keeps_the_rest() {
        val parts = PdfMarkdown.splitReply("Working on it.\n<PDF title='Notes'>\n## Part A\nStill writ")
        val doc = parts.last() as ReplyPart.Document
        assertEquals("Notes", doc.title)
        assertEquals("## Part A\nStill writ", doc.markdown)
        assertFalse(doc.complete)
    }

    @Test
    fun a_missing_title_comes_from_the_first_heading_or_line() {
        val fromHeading = PdfMarkdown.splitReply("<pdf>\n# **Lab report**: week 3\nText\n</pdf>").single() as ReplyPart.Document
        assertEquals("Lab report: week 3", fromHeading.title)
        val fromLine = PdfMarkdown.splitReply("<pdf>Dear Sam,\nThanks.</pdf>").single() as ReplyPart.Document
        assertEquals("Dear Sam,", fromLine.title)
        assertEquals("Document", PdfMarkdown.titleOf("  \n "))
    }

    @Test
    fun a_document_wrapped_in_one_code_fence_is_unwrapped() {
        val doc = PdfMarkdown.splitReply("<pdf title=\"A\">\n```markdown\n# A\n- one\n```\n</pdf>").single() as ReplyPart.Document
        assertEquals("# A\n- one", doc.markdown)
    }

    @Test
    fun two_documents_in_one_reply() {
        val parts = PdfMarkdown.splitReply("<pdf title=\"One\">1</pdf> and <pdf title=\"Two\">2</pdf>")
        assertEquals(listOf("One", "Two"), parts.filterIsInstance<ReplyPart.Document>().map { it.title })
        assertEquals(ReplyPart.Text("and"), parts[1])
    }

    @Test
    fun plain_text_drops_only_the_tags() {
        assertEquals("Here.\n\n# Title\nBody", PdfMarkdown.plainText("Here.\n\n<pdf title=\"Title\"># Title\nBody</pdf>"))
        assertEquals("  untouched  ", PdfMarkdown.plainText("  untouched  "))
    }

    @Test
    fun file_names_are_safe() {
        assertEquals("Math HW 3 answers.pdf", PdfMarkdown.fileName("Math: HW/3 \"answers\""))
        assertEquals("Document.pdf", PdfMarkdown.fileName(" ... "))
        assertEquals(64, PdfMarkdown.fileName("x".repeat(200)).length)
    }

    @Test
    fun a_pdf_is_offered_only_when_asked_for_and_missing() {
        fun msg(id: String, role: MessageRole, content: String, status: MessageStatus = MessageStatus.COMPLETED) =
            ChatMessage(id = id, correlationId = id, role = role, content = content, createdAtEpochMs = 1L, status = status)
        val asked = listOf(
            msg("u", MessageRole.USER, "Solve this and give me a PDF"),
            msg("a", MessageRole.ASSISTANT, "x = 4")
        )
        assertTrue(PdfMarkdown.offersPdf(asked, 1))
        assertFalse(PdfMarkdown.offersPdf(asked, 0))
        val made = asked.toMutableList().apply { this[1] = msg("a", MessageRole.ASSISTANT, "<pdf>x = 4</pdf>") }
        assertFalse(PdfMarkdown.offersPdf(made, 1))
        val streaming = asked.toMutableList().apply { this[1] = msg("a", MessageRole.ASSISTANT, "x", MessageStatus.STREAMING) }
        assertFalse(PdfMarkdown.offersPdf(streaming, 1))
        val notAsked = asked.toMutableList().apply { this[0] = msg("u", MessageRole.USER, "Solve this, I'm updating a spdf file") }
        assertFalse(PdfMarkdown.offersPdf(notAsked, 1))
    }

    @Test
    fun the_default_style_asks_for_documents_in_tags() {
        assertTrue(OrbitInstructions.DEFAULT_STYLE.contains("<pdf title="))
        assertTrue(PdfMarkdown.hasDocument("<pdf title=\"Short title\">x</pdf>"))
    }
}
