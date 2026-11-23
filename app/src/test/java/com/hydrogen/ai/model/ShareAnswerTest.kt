package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareAnswerTest {

    private fun msg(id: String, role: MessageRole, content: String, status: MessageStatus = MessageStatus.COMPLETED) =
        ChatMessage(id = id, correlationId = id, role = role, content = content, createdAtEpochMs = 0, status = status)

    @Test
    fun answer_is_shared_with_its_question() {
        val chat = listOf(msg("1", MessageRole.USER, " Why is the sky blue? "), msg("2", MessageRole.ASSISTANT, "Rayleigh scattering.\n"))
        assertEquals("Q: Why is the sky blue?\n\nA: Rayleigh scattering.", ShareAnswer.text(chat, 1))
    }

    @Test
    fun the_closest_earlier_question_is_used() {
        val chat = listOf(
            msg("1", MessageRole.USER, "First"),
            msg("2", MessageRole.ASSISTANT, "One"),
            msg("3", MessageRole.USER, "Second"),
            msg("4", MessageRole.ASSISTANT, "Two"),
            msg("5", MessageRole.ASSISTANT, "Two, by another model")
        )
        assertEquals("Q: Second\n\nA: Two, by another model", ShareAnswer.text(chat, 4))
    }

    @Test
    fun image_only_questions_are_skipped() {
        val chat = listOf(msg("1", MessageRole.USER, "Hi"), msg("2", MessageRole.USER, "  "), msg("3", MessageRole.ASSISTANT, "A cat"))
        assertEquals("Q: Hi\n\nA: A cat", ShareAnswer.text(chat, 2))
    }

    @Test
    fun answer_without_a_question_is_shared_alone() {
        assertEquals("Hello!", ShareAnswer.text(listOf(msg("1", MessageRole.ASSISTANT, "Hello!")), 0))
    }

    @Test
    fun only_finished_non_empty_answers_can_be_shared() {
        val chat = listOf(
            msg("1", MessageRole.USER, "Q"),
            msg("2", MessageRole.ASSISTANT, "partial", MessageStatus.STREAMING),
            msg("3", MessageRole.ASSISTANT, "   "),
            msg("4", MessageRole.ASSISTANT, "err", MessageStatus.FAILED)
        )
        assertNull(ShareAnswer.text(chat, 0))
        assertNull(ShareAnswer.text(chat, 1))
        assertNull(ShareAnswer.text(chat, 2))
        assertNull(ShareAnswer.text(chat, 3))
        assertNull(ShareAnswer.text(chat, 9))
        assertNull(ShareAnswer.text(chat, -1))
    }

    @Test
    fun long_question_and_answer_are_capped() {
        val chat = listOf(msg("1", MessageRole.USER, "q".repeat(2_000)), msg("2", MessageRole.ASSISTANT, "a".repeat(100_000)))
        val text = ShareAnswer.text(chat, 1)!!
        assertEquals(ShareAnswer.MAX_TOTAL, text.length)
        assertTrue(text.endsWith("…"))
        val question = text.substringAfter("Q: ").substringBefore("\n\nA: ")
        assertEquals(ShareAnswer.MAX_QUESTION, question.length)
        assertTrue(question.endsWith("…"))
    }
}
