package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceChatScreenRulesTest {

    @Test
    fun captionIsTheSentenceBeingRead() {
        val text = "First one. Second sentence here! Third?"
        assertEquals("First one.", VoiceConversation.captionAt(text, 0))
        assertEquals("First one.", VoiceConversation.captionAt(text, 9))
        assertEquals("Second sentence here!", VoiceConversation.captionAt(text, 11))
        assertEquals("Third?", VoiceConversation.captionAt(text, text.length - 1))
    }

    @Test
    fun captionHandlesEdgesAndLineBreaks() {
        assertEquals("", VoiceConversation.captionAt("", 3))
        assertEquals("no stop at the end", VoiceConversation.captionAt("no stop at the end", 99))
        assertEquals("line two", VoiceConversation.captionAt("line one\nline two\nline three", 12))
        assertEquals("only", VoiceConversation.captionAt("only", -5))
    }

    @Test
    fun statusLineUsesTheDesignWords() {
        assertEquals("Listening", VoiceConversation.status(VoiceConversation.Phase.LISTENING, muted = false, listening = true))
        assertEquals("Tap to talk", VoiceConversation.status(VoiceConversation.Phase.LISTENING, muted = false, listening = false))
        assertEquals("Mic off", VoiceConversation.status(VoiceConversation.Phase.LISTENING, muted = true, listening = false))
        assertEquals("Thinking", VoiceConversation.status(VoiceConversation.Phase.THINKING, muted = true, listening = false))
        assertEquals(
            "Speaking. Tap to interrupt.",
            VoiceConversation.status(VoiceConversation.Phase.SPEAKING, muted = false, listening = false)
        )
    }
}
