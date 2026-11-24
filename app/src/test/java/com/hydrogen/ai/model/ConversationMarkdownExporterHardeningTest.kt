package com.hydrogen.ai.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for malformed-attachment and Markdown-escaping hardening.
 * Covers: heading injection via '#' in title/modelId, inline punctuation in
 * attachment display names, and null/empty attachment list safety.
 */
class ConversationMarkdownExporterHardeningTest {

    private fun conv(
        title: String,
        modelId: String = "m",
        messages: List<ChatMessage>
    ) = Conversation(
        id = "x", title = title, createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L, modelId = modelId, messages = messages
    )

    private fun msg(
        content: String,
        attachments: List<AttachmentManifestItem> = emptyList()
    ) = ChatMessage(
        id = "1", correlationId = "c-1", role = MessageRole.USER,
        content = content, createdAtEpochMs = 1L, modelId = "m",
        status = MessageStatus.COMPLETED, attachments = attachments
    )

    private fun att(displayName: String) = AttachmentManifestItem(
        id = "a1", displayName = displayName,
        mimeType = "application/octet-stream", sizeBytes = 0L,
        localUri = "file:///data/secret"
    )

    // ── heading injection ─────────────────────────────────────────────────────

    @Test
    fun titleWithLeadingHashesDoesNotProduceExtraHeadings() {
        val md = ConversationMarkdownExporter.export(
            conv("## Injected heading", messages = listOf(msg("hello")))
        )
        // The document should have exactly one H1 (the title line) and one H2 (the role)
        val h1count = Regex("^# ", RegexOption.MULTILINE).findAll(md).count()
        val h2count = Regex("^## ", RegexOption.MULTILINE).findAll(md).count()
        assertTrue("Expected 1 H1, got $h1count:\n$md", h1count == 1)
        assertTrue("Expected 1 H2, got $h2count:\n$md", h2count == 1)
        // The stripped title text should still appear
        assertTrue(md.contains("Injected heading"))
    }

    @Test
    fun modelIdWithHashesInHeadingIsSanitised() {
        val md = ConversationMarkdownExporter.export(
            conv("Chat", modelId = "## evil", messages = listOf(msg("hi")))
        )
        // No raw "## evil" sequence anywhere
        assertFalse("Unsanitised modelId heading leaked:\n$md", md.contains("## evil"))
    }

    // ── attachment display-name escaping ──────────────────────────────────────

    @Test
    fun attachmentNameWithMarkdownSpecialCharsIsEscaped() {
        val md = ConversationMarkdownExporter.export(
            conv("T", messages = listOf(msg("q", listOf(att("file_[1]*.pdf")))))
        )
        // Unescaped special chars must not appear bare
        assertFalse("Bare underscore leaked", md.contains("file_["))
        // Escaped versions should be present
        assertTrue("Escaped chars missing:\n$md", md.contains("file\\_"))
    }

    @Test
    fun blankDisplayNameFallsBackToAttachmentLabel() {
        val md = ConversationMarkdownExporter.export(
            conv("T", messages = listOf(msg("q", listOf(att("   ")))))
        )
        assertTrue(md.contains("Attachments: attachment"))
    }

    // ── null / empty attachment list safety ───────────────────────────────────

    @Test
    fun emptyAttachmentListProducesNoAttachmentsLine() {
        val md = ConversationMarkdownExporter.export(
            conv("T", messages = listOf(msg("hello", emptyList())))
        )
        assertFalse(md.contains("Attachments:"))
    }
}
