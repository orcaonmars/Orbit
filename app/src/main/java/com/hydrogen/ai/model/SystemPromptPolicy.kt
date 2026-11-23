package com.hydrogen.ai.model

/** Per-chat system prompt ("chat instructions"): normalization and how it is placed in a dispatch. */
object SystemPromptPolicy {
    const val MAX_LENGTH = 4_000
    const val MESSAGE_ID = "chat-system-prompt"

    /** Trimmed and capped; blank input clears the prompt. */
    fun normalize(text: String): String = text.trim().take(MAX_LENGTH).trim()

    /**
     * Prepends the chat's system prompt as one SYSTEM message. It is never stored in the conversation
     * history, so editing it applies to the next send and retry without rewriting past messages.
     */
    fun applyTo(messages: List<ChatMessage>, systemPrompt: String, createdAtEpochMs: Long): List<ChatMessage> {
        val prompt = normalize(systemPrompt)
        if (prompt.isEmpty()) return messages
        val systemMessage = ChatMessage(
            id = MESSAGE_ID,
            correlationId = MESSAGE_ID,
            role = MessageRole.SYSTEM,
            content = prompt,
            createdAtEpochMs = createdAtEpochMs
        )
        return listOf(systemMessage) + messages.filterNot { it.id == MESSAGE_ID }
    }
}
