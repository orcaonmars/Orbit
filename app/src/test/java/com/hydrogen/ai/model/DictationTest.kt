package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationTest {

    @Test
    fun levelsStayBetweenTheQuietBarAndFull() {
        assertEquals(Dictation.MIN_LEVEL, Dictation.normalize(-10f), 0.0001f)
        assertEquals(Dictation.MIN_LEVEL, Dictation.normalize(-2f), 0.0001f)
        assertEquals(1f, Dictation.normalize(10f), 0.0001f)
        assertEquals(1f, Dictation.normalize(40f), 0.0001f)
        assertEquals(Dictation.MIN_LEVEL, Dictation.normalize(Float.NaN), 0.0001f)
        val mid = Dictation.normalize(4f)
        assertTrue(mid > Dictation.MIN_LEVEL && mid < 1f)
    }

    @Test
    fun waveformKeepsTheNewestBarsOnTheRight() {
        var history = emptyList<Float>()
        repeat(Dictation.BAR_COUNT + 5) { i -> history = Dictation.pushLevel(history, i / 100f) }
        assertEquals(Dictation.BAR_COUNT, history.size)
        assertEquals((Dictation.BAR_COUNT + 4) / 100f, history.last(), 0.0001f)
        assertEquals(5 / 100f, history.first(), 0.0001f)
        assertEquals(1f, Dictation.pushLevel(emptyList(), 3f).single(), 0.0001f)
    }

    @Test
    fun barsArePaddedWithQuietBarsOnTheLeft() {
        val bars = Dictation.bars(listOf(0.5f, 0.9f))
        assertEquals(Dictation.BAR_COUNT, bars.size)
        assertEquals(Dictation.MIN_LEVEL, bars.first(), 0.0001f)
        assertEquals(listOf(0.5f, 0.9f), bars.takeLast(2))
        assertEquals(Dictation.BAR_COUNT, Dictation.bars(List(40) { 0.3f }).size)
    }

    @Test
    fun orbRisesQuicklyAndFallsSlowly() {
        val up = Dictation.smooth(0f, 1f)
        val down = Dictation.smooth(1f, 0f)
        assertTrue(up > 0.5f)
        assertTrue(down > 0.7f)
        assertEquals(0.5f, Dictation.smooth(0.5f, 0.5f), 0.0001f)
    }

    @Test
    fun timerReadsLikeAClock() {
        assertEquals("0:00", Dictation.formatElapsed(0))
        assertEquals("0:07", Dictation.formatElapsed(7_900))
        assertEquals("1:05", Dictation.formatElapsed(65_000))
        assertEquals("12:00", Dictation.formatElapsed(720_000))
        assertEquals("0:00", Dictation.formatElapsed(-50))
    }

    @Test
    fun screenReadersHearPlainDurations() {
        assertEquals("Recording, 7 seconds", Dictation.recordingLabel(7_200))
        assertEquals("Recording, 1 second", Dictation.recordingLabel(1_000))
        assertEquals("Recording, 1 minute", Dictation.recordingLabel(60_000))
        assertEquals("Recording, 2 minutes 1 second", Dictation.recordingLabel(121_000))
    }

    @Test
    fun stopsAfterThreeQuietSecondsOnceYouHaveSpoken() {
        assertFalse(Dictation.shouldAutoStop(nowMs = 5_000, startedMs = 0, lastSpeechMs = 2_500))
        assertTrue(Dictation.shouldAutoStop(nowMs = 5_500, startedMs = 0, lastSpeechMs = 2_500))
    }

    @Test
    fun givesMoreTimeBeforeTheFirstWordAndHasAHardCap() {
        assertFalse(Dictation.shouldAutoStop(nowMs = 5_000, startedMs = 0, lastSpeechMs = null))
        assertTrue(Dictation.shouldAutoStop(nowMs = 6_000, startedMs = 0, lastSpeechMs = null))
        assertTrue(
            Dictation.shouldAutoStop(
                nowMs = Dictation.MAX_DICTATION_MS, startedMs = 0, lastSpeechMs = Dictation.MAX_DICTATION_MS - 10
            )
        )
    }

    @Test
    fun transcriptJoinsPiecesAndIsNullWhenNothingWasHeard() {
        assertEquals("hello there friend", Dictation.transcript(" hello there ", "friend "))
        assertEquals("hello", Dictation.transcript("hello", "  "))
        assertNull(Dictation.transcript("", " "))
        val long = "a".repeat(VoiceInputPolicy.MAX_TRANSCRIPT_CHARS + 50)
        assertEquals(VoiceInputPolicy.MAX_TRANSCRIPT_CHARS, Dictation.transcript(long, "")!!.length)
        assertEquals("a b", Dictation.join(null, " a", "", "b "))
    }

    @Test
    fun errorCodesMatchAndroid() {
        // Same numbers as android.speech.SpeechRecognizer.ERROR_*.
        assertEquals(1, Dictation.Error.NETWORK_TIMEOUT)
        assertEquals(6, Dictation.Error.SPEECH_TIMEOUT)
        assertEquals(7, Dictation.Error.NO_MATCH)
        assertEquals(9, Dictation.Error.INSUFFICIENT_PERMISSIONS)
        assertEquals(13, Dictation.Error.LANGUAGE_UNAVAILABLE)
    }

    @Test
    fun silenceIsNotAFault() {
        assertTrue(Dictation.isNoSpeech(Dictation.Error.NO_MATCH))
        assertTrue(Dictation.isNoSpeech(Dictation.Error.SPEECH_TIMEOUT))
        assertFalse(Dictation.isNoSpeech(Dictation.Error.AUDIO))
    }

    @Test
    fun onDeviceFailuresFallBackToTheSpeechService() {
        assertTrue(Dictation.shouldFallBackToService(Dictation.Error.LANGUAGE_UNAVAILABLE, onDevice = true))
        assertTrue(Dictation.shouldFallBackToService(Dictation.Error.CLIENT, onDevice = true))
        assertFalse(Dictation.shouldFallBackToService(Dictation.Error.LANGUAGE_UNAVAILABLE, onDevice = false))
        assertFalse(Dictation.shouldFallBackToService(Dictation.Error.NO_MATCH, onDevice = true))
        assertFalse(Dictation.shouldFallBackToService(Dictation.Error.INSUFFICIENT_PERMISSIONS, onDevice = true))
    }

    @Test
    fun everyErrorSaysWhatHappenedInOrbitsVoice() {
        val banned = listOf("!", "—", "please note", "oops", "sorry")
        (0..20).forEach { code ->
            val message = Dictation.errorMessage(code)
            assertTrue(message.isNotBlank())
            assertTrue(message.endsWith("."))
            banned.forEach { word -> assertFalse("$code: $message", message.lowercase().contains(word)) }
        }
        assertEquals(Dictation.NO_SPEECH_MESSAGE, Dictation.errorMessage(Dictation.Error.NO_MATCH))
        assertEquals(Dictation.MIC_PERMISSION_MESSAGE, Dictation.errorMessage(Dictation.Error.INSUFFICIENT_PERMISSIONS))
    }
}
