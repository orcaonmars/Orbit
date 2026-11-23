package com.hydrogen.ai.model

/**
 * Decides whether a reply may be re-run. RETRY re-sends a failed/cancelled turn with the same
 * idempotency key (so a relay that already finished it can return the stored result).
 * REGENERATE asks for a fresh answer to the latest completed reply and must use a NEW key.
 */
object RegeneratePolicy {
    enum class Mode { RETRY, REGENERATE }

    fun modeFor(messages: List<ChatMessage>, messageId: String): Mode? {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return null
        val message = messages[index]
        if (message.hasSideEffectsInProgress) return null
        return when (message.status) {
            MessageStatus.FAILED, MessageStatus.CANCELLED -> Mode.RETRY
            MessageStatus.COMPLETED -> Mode.REGENERATE.takeIf {
                message.role == MessageRole.ASSISTANT &&
                    index == messages.lastIndex &&
                    // Never silently re-request tool calls (they may lead to PC actions).
                    message.turnMetadata?.toolCalls.isNullOrEmpty() &&
                    messages.subList(0, index).any { it.role == MessageRole.USER }
            }
            else -> null
        }
    }
}
