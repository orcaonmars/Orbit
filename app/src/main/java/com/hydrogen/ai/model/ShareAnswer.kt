package com.hydrogen.ai.model

/** Plain text for the share sheet: the question that was asked, then the finished answer. */
object ShareAnswer {
    const val MAX_QUESTION = 500
    const val MAX_TOTAL = 50_000

    /**
     * Text to share for the assistant message at [index], or null when it is not a finished, non-empty answer.
     * The question is the closest earlier user message with text (shortened past [MAX_QUESTION]); the whole text
     * is capped at [MAX_TOTAL] characters so it always fits through the share intent.
     */
    fun text(messages: List<ChatMessage>, index: Int): String? {
        val answer = messages.getOrNull(index) ?: return null
        if (answer.role != MessageRole.ASSISTANT || answer.status != MessageStatus.COMPLETED) return null
        val body = PdfMarkdown.plainText(answer.content.trim())
        if (body.isEmpty()) return null
        val question = (index - 1 downTo 0)
            .map { messages[it] }
            .firstOrNull { it.role == MessageRole.USER && it.content.isNotBlank() }
            ?.content?.trim()
            ?.let { if (it.length > MAX_QUESTION) it.take(MAX_QUESTION - 1).trimEnd() + "…" else it }
        val full = if (question == null) body else "Q: $question\n\nA: $body"
        return if (full.length > MAX_TOTAL) full.take(MAX_TOTAL - 1).trimEnd() + "…" else full
    }
}
