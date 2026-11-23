package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditResendPolicyTest {

    private fun msg(id: String, role: MessageRole, sideEffects: Boolean = false) =
        ChatMessage(
            id = id,
            correlationId = id,
            role = role,
            content = "x",
            createdAtEpochMs = 1L,
            hasSideEffectsInProgress = sideEffects
        )

    private val chat = listOf(
        msg("u1", MessageRole.USER),
        msg("a1", MessageRole.ASSISTANT),
        msg("u2", MessageRole.USER),
        msg("a2", MessageRole.ASSISTANT)
    )

    @Test
    fun onlyYourOwnMessagesCanBeEdited() {
        assertTrue(EditResendPolicy.canEdit(chat, "u1", isSending = false))
        assertFalse(EditResendPolicy.canEdit(chat, "a1", isSending = false))
        assertFalse(EditResendPolicy.canEdit(chat, "missing", isSending = false))
    }

    @Test
    fun notWhileAReplyIsStreaming() {
        assertFalse(EditResendPolicy.canEdit(chat, "u2", isSending = true))
    }

    @Test
    fun notAroundPcWorkInProgress() {
        val busy = chat.dropLast(1) + msg("a2", MessageRole.ASSISTANT, sideEffects = true)
        assertFalse(EditResendPolicy.canEdit(busy, "u1", isSending = false))
    }

    @Test
    fun historyKeepsOnlyEarlierMessages() {
        assertEquals(listOf("u1", "a1"), EditResendPolicy.historyBefore(chat, "u2")!!.map { it.id })
        assertEquals(emptyList<String>(), EditResendPolicy.historyBefore(chat, "u1")!!.map { it.id })
        assertNull(EditResendPolicy.historyBefore(chat, "nope"))
    }

    @Test
    fun replacedCountIncludesTheEditedMessage() {
        assertEquals(4, EditResendPolicy.replacedCount(chat, "u1"))
        assertEquals(2, EditResendPolicy.replacedCount(chat, "u2"))
        assertEquals(0, EditResendPolicy.replacedCount(chat, "nope"))
    }
}
