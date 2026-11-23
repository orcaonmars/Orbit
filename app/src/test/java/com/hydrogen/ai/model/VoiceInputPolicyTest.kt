package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceInputPolicyTest {

    @Test
    fun picksFirstNonBlankResultTrimmedAndCapped() {
        assertEquals("hello world", VoiceInputPolicy.bestTranscript(listOf("  ", null, " hello world ", "other")))
        assertNull(VoiceInputPolicy.bestTranscript(null))
        assertNull(VoiceInputPolicy.bestTranscript(listOf("", "   ")))
        val long = "a".repeat(VoiceInputPolicy.MAX_TRANSCRIPT_CHARS + 10)
        assertEquals(VoiceInputPolicy.MAX_TRANSCRIPT_CHARS, VoiceInputPolicy.bestTranscript(listOf(long))!!.length)
    }

    @Test
    fun appendsToDraftWithOneSpace() {
        assertEquals("hi there", VoiceInputPolicy.mergeIntoDraft("", " hi there "))
        assertEquals("hi there", VoiceInputPolicy.mergeIntoDraft("   ", "hi there"))
        assertEquals("Summarize this and more", VoiceInputPolicy.mergeIntoDraft("Summarize this", "and more"))
        assertEquals("line one\nmore", VoiceInputPolicy.mergeIntoDraft("line one\n", "more"))
    }

    @Test
    fun emptyTranscriptLeavesDraftUntouched() {
        assertEquals("keep me ", VoiceInputPolicy.mergeIntoDraft("keep me ", "   "))
    }

    @Test
    fun micOnlyReplacesSendWhenThereIsNothingToSend() {
        assertTrue(VoiceInputPolicy.showMicInsteadOfSend(true, false, "", false))
        assertFalse(VoiceInputPolicy.showMicInsteadOfSend(false, false, "", false))
        assertFalse(VoiceInputPolicy.showMicInsteadOfSend(true, true, "", false))
        assertFalse(VoiceInputPolicy.showMicInsteadOfSend(true, false, "draft", false))
        assertFalse(VoiceInputPolicy.showMicInsteadOfSend(true, false, "", true))
    }
}
