package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceConversationTest {
    private fun msg(id: String, role: MessageRole, content: String, status: MessageStatus = MessageStatus.COMPLETED) =
        ChatMessage(id = id, correlationId = id, role = role, content = content, createdAtEpochMs = 0L, status = status)

    private val earlier = listOf(
        msg("u0", MessageRole.USER, "hi"),
        msg("a0", MessageRole.ASSISTANT, "Hello there")
    )

    @Test
    fun stop_phrases_end_the_loop_but_real_messages_do_not() {
        assertTrue(VoiceConversation.isStopPhrase("Stop"))
        assertTrue(VoiceConversation.isStopPhrase("  goodbye! "))
        assertTrue(VoiceConversation.isStopPhrase("That's all."))
        assertFalse(VoiceConversation.isStopPhrase("stop the timer at five"))
        assertFalse(VoiceConversation.isStopPhrase("how do I say goodbye in French"))
    }

    @Test
    fun waits_while_the_reply_is_coming_and_never_rereads_an_old_one() {
        assertEquals(VoiceConversation.Next.Wait, VoiceConversation.afterSend(earlier, isSending = false, sentAfterCount = 2))
        val sending = earlier + msg("u1", MessageRole.USER, "why is the sky blue")
        assertEquals(VoiceConversation.Next.Wait, VoiceConversation.afterSend(sending, isSending = true, sentAfterCount = 2))
        val streaming = sending + msg("a1", MessageRole.ASSISTANT, "Because", MessageStatus.STREAMING)
        assertEquals(VoiceConversation.Next.Wait, VoiceConversation.afterSend(streaming, isSending = false, sentAfterCount = 2))
    }

    @Test
    fun a_finished_reply_is_spoken() {
        val reply = msg("a1", MessageRole.ASSISTANT, "**Rayleigh** scattering.")
        val next = VoiceConversation.afterSend(earlier + msg("u1", MessageRole.USER, "q") + reply, false, 2)
        assertEquals(VoiceConversation.Next.Speak(reply), next)
    }

    @Test
    fun comparison_answers_are_not_the_reply_to_read() {
        val reply = msg("a1", MessageRole.ASSISTANT, "Main answer")
        val compare = msg("a2", MessageRole.ASSISTANT, "Other model").copy(comparison = true)
        val next = VoiceConversation.afterSend(earlier + msg("u1", MessageRole.USER, "q") + reply + compare, false, 2)
        assertEquals(VoiceConversation.Next.Speak(reply), next)
    }

    @Test
    fun failures_cancels_and_silent_replies_end_voice_chat() {
        val base = earlier + msg("u1", MessageRole.USER, "q")
        listOf(
            msg("a1", MessageRole.ASSISTANT, "", MessageStatus.FAILED),
            msg("a1", MessageRole.ASSISTANT, "partial", MessageStatus.CANCELLED),
            msg("a1", MessageRole.ASSISTANT, "   ")
        ).forEach { reply ->
            assertTrue(VoiceConversation.afterSend(base + reply, false, 2) is VoiceConversation.Next.End)
        }
        assertTrue(VoiceConversation.afterSend(base, false, 2) is VoiceConversation.Next.End)
    }
}
