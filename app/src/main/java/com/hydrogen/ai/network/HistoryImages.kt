package com.hydrogen.ai.network

import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.MessageRole

/**
 * A photo in an earlier message that can no longer be read (its file was removed, or it is a kind this model can't
 * take) must not block every later message in the chat: a sent message's photos can't be removed. Only the message
 * being sent now fails on such a photo; earlier ones go without it and carry [LEFT_OUT_NOTE] instead.
 */
internal object HistoryImages {
    const val LEFT_OUT_NOTE = "[Orbit note: a photo from this earlier message could not be read, so it was left out.]"

    /** Index of the message being sent now, the newest one from the person, or -1. */
    fun newestUserIndex(messages: List<ChatMessage>): Int = messages.indexOfLast { it.role == MessageRole.USER }
}
