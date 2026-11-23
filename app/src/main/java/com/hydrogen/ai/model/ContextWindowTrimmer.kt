package com.hydrogen.ai.model

/**
 * Applies [ContextWindowPolicy] to the chat history that is about to be sent, so very long chats
 * keep their newest turns instead of overflowing a small local model's context window.
 * System messages are always kept; only the oldest user/assistant turns are dropped.
 */
object ContextWindowTrimmer {

    /** Hosted Gemini models accept far more context than a typical local model. */
    const val LARGE_CONTEXT_BUDGET_CHARS = 400_000

    fun budgetFor(route: ProviderRoute): Int =
        if (route == ProviderRoute.GOOGLE_AI_STUDIO_DIRECT) LARGE_CONTEXT_BUDGET_CHARS
        else ContextWindowPolicy.DEFAULT_BUDGET_CHARS

    fun trim(
        messages: List<ChatMessage>,
        systemPrompt: String,
        omittedAttachmentIds: Collection<String>,
        budgetChars: Int = ContextWindowPolicy.DEFAULT_BUDGET_CHARS
    ): List<ChatMessage> {
        val (systemMessages, turns) = messages.partition { it.role == MessageRole.SYSTEM }
        if (turns.isEmpty()) return messages
        val reserved = systemPrompt.length.toLong() + systemMessages.sumOf { it.content.length.toLong() }
        val plan = ContextWindowPolicy.plan(
            items = turns.map { msg ->
                ContextWindowPolicy.Item(
                    role = msg.role.name.lowercase(),
                    chars = msg.content.length,
                    imageCount = msg.attachments.count { it.id !in omittedAttachmentIds }
                )
            },
            budgetChars = budgetChars,
            systemPromptChars = reserved.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        )
        if (plan.droppedCount == 0) return messages
        return systemMessages + turns.drop(plan.keepFromIndex)
    }
}
