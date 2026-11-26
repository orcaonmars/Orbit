package com.hydrogen.ai.model

/**
 * Rules for "Edit and resend": a sent message of yours can be changed and sent again. The model answers the edited
 * version from that point on, and what was there before is kept as an earlier version ([MessageVersions]).
 */
object EditResendPolicy {

    fun canEdit(messages: List<ChatMessage>, messageId: String, isSending: Boolean): Boolean {
        if (isSending) return false
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return false
        val message = messages[index]
        if (message.role != MessageRole.USER) return false
        // Never rewrite history around a reply that is still doing work on the PC.
        return (index until messages.size).none { messages[it].hasSideEffectsInProgress }
    }

    /** Messages kept when [messageId] is edited and resent, or null when it is not in the chat. */
    fun historyBefore(messages: List<ChatMessage>, messageId: String): List<ChatMessage>? {
        val index = messages.indexOfFirst { it.id == messageId }
        return if (index < 0) null else messages.subList(0, index).toList()
    }

    /** How many messages (the edited one included) the resend replaces; 0 when it is not in the chat. */
    fun replacedCount(messages: List<ChatMessage>, messageId: String): Int {
        val index = messages.indexOfFirst { it.id == messageId }
        return if (index < 0) 0 else messages.size - index
    }
}
