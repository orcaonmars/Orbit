package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegeneratePolicyTest {

    private fun msg(id: String, role: MessageRole, status: MessageStatus = MessageStatus.COMPLETED) =
        ChatMessage(id = id, correlationId = id, role = role, content = "x", createdAtEpochMs = 1L, status = status)

    @Test
    fun latestCompletedReplyCanBeRegenerated() {
        val chat = listOf(msg("u1", MessageRole.USER), msg("a1", MessageRole.ASSISTANT))
        assertEquals(RegeneratePolicy.Mode.REGENERATE, RegeneratePolicy.modeFor(chat, "a1"))
    }

    @Test
    fun olderRepliesUserMessagesAndUnknownIdsCannot() {
        val chat = listOf(
            msg("u1", MessageRole.USER), msg("a1", MessageRole.ASSISTANT),
            msg("u2", MessageRole.USER), msg("a2", MessageRole.ASSISTANT)
        )
        assertNull(RegeneratePolicy.modeFor(chat, "a1"))
        assertNull(RegeneratePolicy.modeFor(chat, "u2"))
        assertNull(RegeneratePolicy.modeFor(chat, "missing"))
        assertNull(RegeneratePolicy.modeFor(listOf(msg("a0", MessageRole.ASSISTANT)), "a0"))
    }

    @Test
    fun failedAndCancelledRepliesAreRetriesNotRegenerations() {
        val failed = listOf(msg("u1", MessageRole.USER), msg("a1", MessageRole.ASSISTANT, MessageStatus.FAILED))
        assertEquals(RegeneratePolicy.Mode.RETRY, RegeneratePolicy.modeFor(failed, "a1"))
        val cancelled = listOf(msg("u1", MessageRole.USER), msg("a1", MessageRole.ASSISTANT, MessageStatus.CANCELLED))
        assertEquals(RegeneratePolicy.Mode.RETRY, RegeneratePolicy.modeFor(cancelled, "a1"))
        val streaming = listOf(msg("u1", MessageRole.USER), msg("a1", MessageRole.ASSISTANT, MessageStatus.STREAMING))
        assertNull(RegeneratePolicy.modeFor(streaming, "a1"))
    }

    @Test
    fun repliesWithToolCallsOrSideEffectsAreNeverRerun() {
        val withTools = msg("a1", MessageRole.ASSISTANT).copy(
            turnMetadata = GeminiTurnMetadata(toolCalls = listOf(ToolCallRecord(id = "t1", name = "read_file")))
        )
        assertNull(RegeneratePolicy.modeFor(listOf(msg("u1", MessageRole.USER), withTools), "a1"))
        val busy = msg("a1", MessageRole.ASSISTANT, MessageStatus.FAILED).copy(hasSideEffectsInProgress = true)
        assertNull(RegeneratePolicy.modeFor(listOf(msg("u1", MessageRole.USER), busy), "a1"))
    }
}
