package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMarkdownExporterTest {

    private fun msg(
        id: String,
        role: MessageRole,
        content: String,
        status: MessageStatus = MessageStatus.COMPLETED,
        attachments: List<AttachmentManifestItem> = emptyList()
    ) = ChatMessage(
        id = id,
        correlationId = "c-$id",
        role = role,
        content = content,
        createdAtEpochMs = 1L,
        modelId = "local-model",
        status = status,
        attachments = attachments
    )

    @Test
    fun exportsTitleRolesContentAndAttachmentNamesWithoutLocalUris() {
        val conversation = Conversation(
            id = "conv",
            title = "Colors\nquestion",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 2L,
            modelId = "local-model",
            messages = listOf(
                msg(
                    "1", MessageRole.USER, "What color is this?",
                    attachments = listOf(
                        AttachmentManifestItem(
                            id = "a1",
                            displayName = "apple.jpg",
                            mimeType = "image/jpeg",
                            sizeBytes = 10L,
                            localUri = "file:///data/user/0/com.hydrogen.ai/files/secret/apple.jpg"
                        )
                    )
                ),
                msg("2", MessageRole.ASSISTANT, "  Red  ")
            )
        )

        val md = ConversationMarkdownExporter.export(conversation)

        assertTrue(md.startsWith("# Colors question\n"))
        assertTrue(md.contains("## You\n\nAttachments: apple.jpg\n\nWhat color is this?\n"))
        assertTrue(md.contains("## Assistant (local-model)\n\nRed\n"))
        assertFalse(md.contains("file://"))
        assertFalse(md.contains("secret"))
    }

    @Test
    fun skipsEmptyAndStagedMessagesAndMarksFailures() {
        val conversation = Conversation(
            id = "conv",
            title = " ",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 2L,
            messages = listOf(
                msg("1", MessageRole.USER, "hello"),
                msg("2", MessageRole.ASSISTANT, "", status = MessageStatus.FAILED),
                msg("3", MessageRole.USER, "draft", status = MessageStatus.STAGED),
                msg("4", MessageRole.ASSISTANT, "partial", status = MessageStatus.CANCELLED)
            )
        )

        val md = ConversationMarkdownExporter.export(conversation)

        assertTrue(md.startsWith("# Orbit chat\n"))
        assertFalse(md.contains("draft"))
        assertEquals(2, Regex("^## ", RegexOption.MULTILINE).findAll(md).count())
        assertTrue(md.contains("partial\n\n_(cancelled; partial output)_"))
        assertEquals("hi", ConversationMarkdownExporter.messageText(msg("5", MessageRole.USER, " hi \n")))
    }

    @Test
    fun exportAllPutsNewestFirstAndSkipsEmptyChats() {
        fun conv(id: String, title: String, updated: Long, messages: List<ChatMessage>) = Conversation(
            id = id, title = title, createdAtEpochMs = 1L, updatedAtEpochMs = updated, messages = messages
        )
        val older = conv("a", "Older", 10L, listOf(msg("1", MessageRole.USER, "first question")))
        val newer = conv("b", "Newer", 20L, listOf(msg("2", MessageRole.USER, "second question")))
        val empty = conv("c", "Empty", 30L, emptyList())
        val onlyDraft = conv("d", "Draft", 40L, listOf(msg("3", MessageRole.USER, "x", status = MessageStatus.STAGED)))

        val md = ConversationMarkdownExporter.exportAll(listOf(older, empty, newer, onlyDraft))

        assertTrue(md.startsWith("# Newer\n"))
        assertTrue(md.indexOf("# Older") > md.indexOf("second question"))
        assertTrue(md.contains("\n\n---\n\n# Older\n"))
        assertFalse(md.contains("# Empty"))
        assertFalse(md.contains("# Draft"))
        assertTrue(md.endsWith("first question\n"))
        assertEquals("", ConversationMarkdownExporter.exportAll(listOf(empty)))
    }

    @Test fun exportOmitsHiddenSystemAndPcActionPayloads() {
        val conv = Conversation(id = "x", title = "Public chat", createdAtEpochMs = 1L,
            updatedAtEpochMs = 2L, messages = listOf(
                msg("s", MessageRole.SYSTEM, "PRIVATE SYSTEM PROMPT"),
                msg("u", MessageRole.USER, "Explain <pdf> literally"),
                msg("a", MessageRole.ASSISTANT, "Visible answer.\n\n```orbit-pc\nPRIVATE ACTION\n```\n\n<pdf>Document text</pdf>")
            ))
        val result = ConversationMarkdownExporter.export(conv)
        assertFalse(result.contains("PRIVATE SYSTEM PROMPT"))
        assertFalse(result.contains("PRIVATE ACTION"))
        assertTrue(result.contains("Visible answer."))
        assertTrue(result.contains("Document text"))
        assertTrue(result.contains("Explain <pdf> literally"))
        assertFalse(result.contains("<pdf>Document"))
        assertTrue(ConversationMarkdownExporter.export(conv.copy(temporary = true)).contains("Visible answer."))
        assertEquals("", ConversationMarkdownExporter.exportAll(listOf(conv.copy(temporary = true))))
        assertEquals("", ConversationMarkdownExporter.exportAll(listOf(conv.copy(messages = conv.messages.take(1)))))
    }

    @Test fun metadataCannotInjectMarkdownImagesOrRawHtml() {
        val conv = Conversation(id = "x", title = "![pixel](https://example.test) <img>",
            createdAtEpochMs = 1L, updatedAtEpochMs = 2L, messages = listOf(msg("u", MessageRole.USER, "Text")))
        val result = ConversationMarkdownExporter.export(conv)
        assertFalse(result.contains("<img>"))
        assertTrue(result.contains("&lt;img&gt;"))
        assertTrue(result.contains("\\!\\[pixel\\]"))
    }
}
