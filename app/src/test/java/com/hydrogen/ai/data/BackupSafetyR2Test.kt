package com.hydrogen.ai.data

import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.Conversation
import com.hydrogen.ai.model.MessageBranch
import com.hydrogen.ai.model.MessageRole
import org.junit.Assert.*
import org.junit.Test

class BackupSafetyR2Test {
    private fun msg(id: String) = ChatMessage(
        id = id, correlationId = id, role = MessageRole.USER,
        content = id, createdAtEpochMs = 1L
    )

    private fun chat(vararg messages: ChatMessage) = Conversation(
        id = "chat", title = "Backup", createdAtEpochMs = 1L, updatedAtEpochMs = 1L,
        messages = messages.toList(), projectId = "another-device-project"
    )

    private fun decode(conversation: Conversation) =
        ChatBackupCodec.decode(ChatBackupCodec.encode(listOf(conversation), 2L))

    @Test fun missingEnvelopeAndUnsupportedVersionFail() {
        for (raw in listOf("{}", """{"format":"hydrogen-chat-backup","version":1}""",
            """{"format":"hydrogen-chat-backup","version":0,"conversations":[]}""")) {
            assertTrue(raw, ChatBackupCodec.decode(raw) is ChatBackupCodec.DecodeResult.Failure)
        }
    }

    @Test fun duplicateVisibleIdsAreRejected() {
        assertTrue(decode(chat(msg("same"), msg("same"))) is ChatBackupCodec.DecodeResult.Failure)
    }

    @Test fun branchesCannotDuplicateEarlierVisibleIds() {
        val second = msg("second").copy(otherVersions = listOf(MessageBranch(listOf(msg("first")))))
        assertTrue(decode(chat(msg("first"), second)) is ChatBackupCodec.DecodeResult.Failure)
    }

    @Test fun validAlternativeCanReuseItsOwnIdAndEmptyVersionsAreRemoved() {
        val alternative = msg("second").copy(content = "previous")
        val current = msg("second").copy(otherVersions = listOf(MessageBranch(), MessageBranch(listOf(alternative))), versionIndex = 1)
        val result = decode(chat(msg("first"), current)) as ChatBackupCodec.DecodeResult.Success
        val restored = result.conversations.single()
        assertEquals("another-device-project", restored.projectId)
        assertEquals(0, restored.messages.last().versionIndex)
        assertEquals("previous", restored.messages.last().otherVersions.single().messages.single().content)
    }

    @Test fun mergeDeduplicatesTheImportedList() {
        val item = chat(msg("first"))
        val result = ChatBackupCodec.merge(emptyList(), listOf(item, item))
        assertEquals(1, result.added)
        assertEquals(1, result.skipped)
    }

    @Test fun excessivelyNestedVersionsFailCleanly() {
        var node = msg("leaf")
        repeat(66) { i -> node = msg("n$i").copy(otherVersions = listOf(MessageBranch(listOf(node)))) }
        assertTrue(decode(chat(node)) is ChatBackupCodec.DecodeResult.Failure)
    }
}