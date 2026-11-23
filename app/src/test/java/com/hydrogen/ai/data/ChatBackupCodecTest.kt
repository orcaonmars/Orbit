package com.hydrogen.ai.data

import com.hydrogen.ai.model.AttachmentManifestItem
import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.Conversation
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatBackupCodecTest {

    private fun message(id: String, status: MessageStatus = MessageStatus.COMPLETED, withAttachment: Boolean = false) =
        ChatMessage(
            id = id,
            correlationId = "c-$id",
            role = MessageRole.USER,
            content = "text $id",
            createdAtEpochMs = 1L,
            status = status,
            hasSideEffectsInProgress = status == MessageStatus.STREAMING,
            attachments = if (withAttachment) {
                listOf(
                    AttachmentManifestItem(
                        id = "a-$id",
                        displayName = "photo.jpg",
                        mimeType = "image/jpeg",
                        sizeBytes = 3L,
                        localUri = "file:///data/user/0/com.hydrogen.ai/files/hydrogen_attachments/photo.jpg"
                    )
                )
            } else {
                emptyList()
            }
        )

    private fun conversation(id: String, updated: Long, vararg messages: ChatMessage) = Conversation(
        id = id,
        title = "Chat $id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = updated,
        messages = messages.toList()
    )

    @Test
    fun roundTripStripsLocalPathsSanitizesInFlightStateAndSkipsEmptyChats() {
        val source = listOf(
            conversation(
                "a", 5L,
                message("1", withAttachment = true),
                message("2", status = MessageStatus.STREAMING),
                message("3", status = MessageStatus.STAGED)
            ),
            conversation("empty", 9L)
        )

        val encoded = ChatBackupCodec.encode(source, nowEpochMs = 42L)
        assertFalse(encoded.contains("file://"))
        assertTrue(encoded.contains("\"format\":\"hydrogen-chat-backup\""))

        val decoded = ChatBackupCodec.decode(encoded) as ChatBackupCodec.DecodeResult.Success
        assertEquals(listOf("a"), decoded.conversations.map { it.id })
        val messages = decoded.conversations.single().messages
        assertEquals(listOf("1", "2"), messages.map { it.id })
        assertTrue(messages.all { it.attachments.isEmpty() && !it.hasSideEffectsInProgress })
        assertEquals(MessageStatus.CANCELLED, messages[1].status)
    }

    @Test
    fun rejectsForeignNewerOrMalformedFiles() {
        assertTrue(ChatBackupCodec.decode("") is ChatBackupCodec.DecodeResult.Failure)
        assertTrue(ChatBackupCodec.decode("not json") is ChatBackupCodec.DecodeResult.Failure)
        assertTrue(
            ChatBackupCodec.decode("""{"format":"other","version":1}""") is ChatBackupCodec.DecodeResult.Failure
        )
        assertTrue(
            ChatBackupCodec.decode("""{"format":"hydrogen-chat-backup","version":99}""")
                is ChatBackupCodec.DecodeResult.Failure
        )
    }

    @Test
    fun aChatMarkedTemporaryInAHandmadeFileImportsAsASavedChat() {
        // Conversation.temporary is @Transient: never written, and a hand-added key is ignored on the way in.
        val encoded = ChatBackupCodec.encode(listOf(conversation("t", 5L, message("1"))), nowEpochMs = 42L)
        assertFalse(encoded.contains("\"temporary\""))
        val edited = encoded.replace("\"id\":\"t\"", "\"id\":\"t\",\"temporary\":true")
        assertTrue(edited.contains("\"temporary\":true"))

        val decoded = ChatBackupCodec.decode(edited) as ChatBackupCodec.DecodeResult.Success

        assertEquals(listOf("t"), decoded.conversations.map { it.id })
        assertFalse(decoded.conversations.single().temporary)
    }

    @Test
    fun mergeNeverOverwritesExistingConversations() {
        val existing = listOf(conversation("a", 5L, message("1")))
        val imported = listOf(
            conversation("a", 50L, message("x")),
            conversation("b", 7L, message("2"))
        )

        val result = ChatBackupCodec.merge(existing, imported)

        assertEquals(1, result.added)
        assertEquals(1, result.skipped)
        assertEquals(listOf("b", "a"), result.conversations.map { it.id })
        assertEquals("1", result.conversations.first { it.id == "a" }.messages.single().id)
    }
}
