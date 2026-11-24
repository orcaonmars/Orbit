package com.hydrogen.ai.model

/** Plain text for the share sheet: the question that was asked, then the finished answer. */
object ShareAnswer {
    const val MAX_QUESTION = 500
    const val MAX_TOTAL = 50_000

    /**
     * Text to share for the assistant message at [index], or null when it is not a finished, non-empty answer.
     * The question is the closest earlier sent user message, if it has text (shortened past [MAX_QUESTION]); the whole text
     * is capped at [MAX_TOTAL] characters so it always fits through the share intent.
     */
    fun text(messages: List<ChatMessage>, index: Int): String? {
        val answer = messages.getOrNull(index) ?: return null
        if (answer.role != MessageRole.ASSISTANT || answer.status != MessageStatus.COMPLETED) return null
        val body = ConversationMarkdownExporter.messageText(answer)
        if (body.isEmpty()) return null
        val question = (index - 1 downTo 0)
            .map { messages[it] }
            .firstOrNull { it.role == MessageRole.USER && it.status != MessageStatus.STAGED }
            ?.content?.trim()?.takeIf { it.isNotBlank() }
            ?.let { if (it.length > MAX_QUESTION) safePrefix(it, MAX_QUESTION - 1).trimEnd() + "…" else it }
        val full = if (question == null) body else "Q: $question\n\nA: $body"
        return if (full.length > MAX_TOTAL) safePrefix(full, MAX_TOTAL - 1).trimEnd() + "…" else full
    }

    private fun safePrefix(text: String, limit: Int): String {
        val end = if (limit < text.length && limit > 0 && Character.isHighSurrogate(text[limit - 1])) limit - 1 else limit
        return text.take(end)
    }
}
