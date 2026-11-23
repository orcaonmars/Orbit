package com.hydrogen.ai.model

/**
 * Hands-free voice chat: listen → send → wait for the reply → read it aloud → listen again.
 * The phone's own speech recognizer (running inside Orbit) and text-to-speech do the audio; these rules decide
 * each next step so they can be unit tested without a device.
 */
object VoiceConversation {

    enum class Phase { LISTENING, THINKING, SPEAKING }

    /** What happens after the reply the user was waiting for settles. */
    sealed interface Next {
        /** The reply is still on its way. */
        data object Wait : Next
        /** Read this finished reply aloud, then listen again. */
        data class Speak(val message: ChatMessage) : Next
        /** Nothing was spoken back (failed, cancelled or empty): stop the loop and say why. */
        data class End(val reason: String) : Next
    }

    private val stopPhrases = setOf(
        "stop", "stop listening", "end", "end chat", "end voice chat", "goodbye", "good bye", "bye",
        "that's all", "thats all", "that is all", "exit", "quit", "cancel"
    )

    /** Status line under the voice chat orb. */
    fun status(phase: Phase, muted: Boolean, listening: Boolean): String = when (phase) {
        Phase.SPEAKING -> "Speaking. Tap to interrupt."
        Phase.THINKING -> "Thinking"
        Phase.LISTENING -> when {
            muted -> "Mic off"
            listening -> "Listening"
            else -> "Tap to talk"
        }
    }

    /**
     * The sentence around [offset] in [text]. While a reply is read aloud, the voice chat caption shows the
     * sentence being spoken instead of the whole reply.
     */
    fun captionAt(text: String, offset: Int): String {
        if (text.isEmpty()) return ""
        val at = offset.coerceIn(0, text.length - 1)
        val enders = ".!?\n"
        var start = at
        while (start > 0 && text[start - 1] !in enders) start--
        var end = at
        while (end < text.length && text[end] !in enders) end++
        if (end < text.length && text[end] != '\n') end++
        return text.substring(start, end).trim()
    }

    /** True when the user said they are done ("stop", "goodbye", "that's all"…), not a message to send. */
    fun isStopPhrase(transcript: String): Boolean {
        val words = transcript.lowercase().replace(Regex("[^a-z' ]"), " ").trim().replace(Regex("\\s+"), " ")
        return words in stopPhrases
    }

    /**
     * Decides what follows a voice turn once the conversation has updated. [sentAfterCount] is how many
     * messages the chat had before the voice message was sent, so an older reply is never read again.
     */
    fun afterSend(messages: List<ChatMessage>, isSending: Boolean, sentAfterCount: Int): Next {
        if (isSending) return Next.Wait
        val reply = messages.drop(sentAfterCount).lastOrNull { it.role == MessageRole.ASSISTANT && !it.comparison }
            ?: return if (messages.size > sentAfterCount) Next.End("No reply came back.") else Next.Wait
        return when (reply.status) {
            MessageStatus.STAGED, MessageStatus.DISPATCHED, MessageStatus.STREAMING -> Next.Wait
            MessageStatus.FAILED -> Next.End("The reply failed, so voice chat stopped.")
            MessageStatus.CANCELLED -> Next.End("Voice chat stopped.")
            MessageStatus.COMPLETED ->
                if (SpeakableText.fromMarkdown(reply.content).isBlank()) Next.End("The reply had nothing to read aloud.")
                else Next.Speak(reply)
        }
    }
}
