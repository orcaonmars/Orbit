package com.hydrogen.ai.model

/**
 * Pure rules for in-app voice input: the dictation bar under the composer and the listening part of voice chat.
 * The phone's own speech recognizer runs inside Orbit (no system popup); these rules turn its callbacks into
 * what the screen shows and decide when listening stops, so they can be unit tested without a device.
 */
object Dictation {
    /** Bars in the dictation waveform, oldest on the left. */
    const val BAR_COUNT = 28

    /** One new bar per tick, so the waveform shows about the last 2.2 seconds. */
    const val TICK_MS = 80L

    /** Dictation stops by itself after this much quiet once you've said something. */
    const val SILENCE_STOP_MS = 3_000L

    /** Before the first word, people get a little longer to start talking. */
    const val FIRST_WORD_WAIT_MS = 6_000L

    /** Hard cap on one dictation, so a forgotten mic never stays on. */
    const val MAX_DICTATION_MS = 180_000L

    /** Hard cap on one voice chat turn. */
    const val MAX_TURN_MS = 60_000L

    /** After the check button, how long to wait for the recognizer's final words before keeping what was heard. */
    const val FINISH_GRACE_MS = 1_500L

    /** Quietest bar, so silence still shows a calm row of dots instead of nothing. */
    const val MIN_LEVEL = 0.08f

    // SpeechRecognizer.onRmsChanged reports roughly -2 dB for silence up to about 10 dB for loud speech.
    private const val RMS_FLOOR_DB = -2f
    private const val RMS_CEIL_DB = 10f

    /** Maps the recognizer's RMS dB to a bar height between [MIN_LEVEL] and 1. */
    fun normalize(rmsDb: Float): Float {
        if (rmsDb.isNaN()) return MIN_LEVEL
        val t = ((rmsDb - RMS_FLOOR_DB) / (RMS_CEIL_DB - RMS_FLOOR_DB)).coerceIn(0f, 1f)
        return MIN_LEVEL + (1f - MIN_LEVEL) * t
    }

    /** Adds the newest level on the right and keeps the last [size]; the oldest drops off the left. */
    fun pushLevel(history: List<Float>, level: Float, size: Int = BAR_COUNT): List<Float> {
        val next = history + level.coerceIn(0f, 1f)
        return if (next.size > size) next.takeLast(size) else next
    }

    /** Exactly [size] bars to draw: quiet bars on the left until the history fills up. */
    fun bars(history: List<Float>, size: Int = BAR_COUNT): List<Float> {
        val recent = history.takeLast(size)
        return List(size - recent.size) { MIN_LEVEL } + recent
    }

    /** Eases the orb toward the newest level: quick to rise, slower to fall, so it doesn't flicker. */
    fun smooth(previous: Float, target: Float): Float {
        val weight = if (target > previous) 0.55f else 0.2f
        return (previous + (target - previous) * weight).coerceIn(0f, 1f)
    }

    /** Timer text: "0:07", "1:05", "12:00". */
    fun formatElapsed(ms: Long): String {
        val seconds = ms.coerceAtLeast(0L) / 1_000L
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }

    /** What a screen reader hears for the waveform and timer: "Recording, 7 seconds". */
    fun recordingLabel(ms: Long): String {
        val seconds = (ms.coerceAtLeast(0L) / 1_000L).toInt()
        val minutes = seconds / 60
        val rest = seconds % 60
        fun unit(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
        return when {
            minutes == 0 -> "Recording, ${unit(rest, "second")}"
            rest == 0 -> "Recording, ${unit(minutes, "minute")}"
            else -> "Recording, ${unit(minutes, "minute")} ${unit(rest, "second")}"
        }
    }

    /**
     * True when dictation should stop by itself: [SILENCE_STOP_MS] after the last word heard, [FIRST_WORD_WAIT_MS]
     * when nothing has been heard yet, and always at [MAX_DICTATION_MS].
     */
    fun shouldAutoStop(nowMs: Long, startedMs: Long, lastSpeechMs: Long?): Boolean {
        if (nowMs - startedMs >= MAX_DICTATION_MS) return true
        return if (lastSpeechMs == null) {
            nowMs - startedMs >= FIRST_WORD_WAIT_MS
        } else {
            nowMs - lastSpeechMs >= SILENCE_STOP_MS
        }
    }

    /** Joins recognized pieces with single spaces, skipping blanks. */
    fun join(vararg parts: String?): String =
        parts.mapNotNull { part -> part?.trim()?.takeIf { it.isNotEmpty() } }.joinToString(" ")

    /** The text to keep when listening ends: what was heard so far, capped, or null if nothing. */
    fun transcript(committed: String, partial: String): String? =
        join(committed, partial).takeIf { it.isNotEmpty() }?.take(VoiceInputPolicy.MAX_TRANSCRIPT_CHARS)

    /**
     * SpeechRecognizer error codes (same values as android.speech.SpeechRecognizer.ERROR_*), kept here so the
     * rules stay plain Kotlin.
     */
    object Error {
        const val NETWORK_TIMEOUT = 1
        const val NETWORK = 2
        const val AUDIO = 3
        const val SERVER = 4
        const val CLIENT = 5
        const val SPEECH_TIMEOUT = 6
        const val NO_MATCH = 7
        const val RECOGNIZER_BUSY = 8
        const val INSUFFICIENT_PERMISSIONS = 9
        const val TOO_MANY_REQUESTS = 10
        const val SERVER_DISCONNECTED = 11
        const val LANGUAGE_NOT_SUPPORTED = 12
        const val LANGUAGE_UNAVAILABLE = 13
        const val CANNOT_CHECK_SUPPORT = 14
        const val CANNOT_LISTEN_TO_DOWNLOAD_EVENTS = 15
    }

    /** "Nothing was said" rather than a fault: dictation keeps listening, voice chat waits for you. */
    fun isNoSpeech(code: Int): Boolean = code == Error.NO_MATCH || code == Error.SPEECH_TIMEOUT

    /**
     * True when the on-device recognizer failed in a way the phone's regular speech service may not, for example
     * because the language pack isn't downloaded. Orbit then retries once with the regular service.
     */
    fun shouldFallBackToService(code: Int, onDevice: Boolean): Boolean = onDevice && code in setOf(
        Error.CLIENT, Error.SERVER, Error.SERVER_DISCONNECTED, Error.LANGUAGE_NOT_SUPPORTED,
        Error.LANGUAGE_UNAVAILABLE, Error.CANNOT_CHECK_SUPPORT, Error.CANNOT_LISTEN_TO_DOWNLOAD_EVENTS
    )

    const val NO_SPEECH_MESSAGE = "Didn't catch that. Try again."
    const val MIC_PERMISSION_MESSAGE = "Orbit needs the microphone to hear you."
    const val ON_DEVICE_NOTE = "Speech is turned into text on this phone."
    const val SERVICE_NOTE = "Your phone's speech service turns speech into text. Orbit doesn't keep the audio."
    const val UNAVAILABLE_MESSAGE = "This phone has no speech service Orbit can use."

    /** What happened plus the fix, in Orbit's voice. */
    fun errorMessage(code: Int): String = when (code) {
        Error.NO_MATCH, Error.SPEECH_TIMEOUT -> NO_SPEECH_MESSAGE
        Error.NETWORK, Error.NETWORK_TIMEOUT ->
            "Your phone's speech service needs a connection. Check it, then try again."
        Error.AUDIO -> "Couldn't use the microphone. Close other apps that are using it, then try again."
        Error.INSUFFICIENT_PERMISSIONS -> MIC_PERMISSION_MESSAGE
        Error.RECOGNIZER_BUSY, Error.TOO_MANY_REQUESTS ->
            "Your phone's speech service is busy. Wait a moment, then try again."
        Error.LANGUAGE_NOT_SUPPORTED, Error.LANGUAGE_UNAVAILABLE ->
            "Your phone's speech service doesn't have your language yet. Add it in the phone's settings, then try again."
        else -> "Speech input stopped. Try again."
    }
}
