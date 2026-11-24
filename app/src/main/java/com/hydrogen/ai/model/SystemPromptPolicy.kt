package com.hydrogen.ai.model

/** Per-chat system prompt ("chat instructions"): normalization and how it is placed in a dispatch. */
object SystemPromptPolicy {
    const val MAX_LENGTH = 4_000
    const val MESSAGE_ID = "chat-system-prompt"

    /** Trimmed and capped; blank input clears the prompt. */
    fun normalize(text: String): String = text.trim().take(MAX_LENGTH).trim()

    /**
     * Places the chat's system prompt as one SYSTEM message at the front of the dispatch list.
     * It is never stored in the conversation history, so editing it applies to the next send and
     * retry without rewriting past messages.
     *
     * Backwards compatibility: a blank [systemPrompt] returns [messages] unchanged (same instance),
     * matching pre-M4_1 behaviour for chats that have no instructions set. If a stale SYSTEM message
     * from an earlier dispatch is already present it is replaced in place so the chat's own ordering
     * stays stable; any other SYSTEM messages are left untouched. Otherwise the prompt is prepended,
     * which is also what callers observed before this change when no stale entry existed.
     */
    fun applyTo(messages: List<ChatMessage>, systemPrompt: String, createdAtEpochMs: Long): List<ChatMessage> {
        val prompt = normalize(systemPrompt)
        if (prompt.isEmpty()) return messages
        // A stale SYSTEM message from an earlier dispatch is replaced in place so the chat's own
        // ordering stays stable; any other SYSTEM messages are left untouched.
        val existingIndex = messages.indexOfFirst { it.id == MESSAGE_ID }
        val systemMessage = ChatMessage(
            id = MESSAGE_ID,
            correlationId = MESSAGE_ID,
            role = MessageRole.SYSTEM,
            content = prompt,
            createdAtEpochMs = createdAtEpochMs
        )
        return if (existingIndex >= 0) {
            messages.toMutableList().also { it[existingIndex] = systemMessage }
        } else {
            listOf(systemMessage) + messages
        }
    }
}
