package com.hydrogen.ai.model

/**
 * Pure rules for voice input. Orbit listens through the phone's speech recognizer (on-device when the phone
 * has one) and gets text back; audio is never saved. Dictated text only lands in the composer for the user
 * to review.
 */
object VoiceInputPolicy {
    const val MAX_TRANSCRIPT_CHARS = 5_000

    /** The recognizer's best (first non-blank) result, trimmed and capped; null if nothing usable. */
    fun bestTranscript(results: List<String?>?): String? =
        results.orEmpty()
            .firstNotNullOfOrNull { candidate -> candidate?.trim()?.takeIf { it.isNotEmpty() } }
            ?.take(MAX_TRANSCRIPT_CHARS)

    /** Appends dictated text to the draft with exactly one separating space. */
    fun mergeIntoDraft(draft: String, transcript: String): String {
        val spoken = transcript.trim()
        if (spoken.isEmpty()) return draft
        if (draft.isBlank()) return spoken
        val separator = if (draft.last().isWhitespace()) "" else " "
        return draft + separator + spoken
    }

    /** The mic takes the Send button's place while there is nothing to send. */
    fun showMicInsteadOfSend(
        voiceAvailable: Boolean,
        isSending: Boolean,
        draftText: String,
        hasAttachments: Boolean
    ): Boolean = voiceAvailable && !isSending && draftText.isBlank() && !hasAttachments
}
