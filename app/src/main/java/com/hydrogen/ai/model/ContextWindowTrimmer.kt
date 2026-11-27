package com.hydrogen.ai.model

/**
 * Applies [ContextWindowPolicy] to the chat history that is about to be sent, so very long chats
 * keep their newest turns instead of overflowing a small local model's context window.
 * System messages are always kept; only the oldest user/assistant turns are dropped.
 */
object ContextWindowTrimmer {

    /** Hosted Gemini models accept far more context than a typical local model. */
    const val LARGE_CONTEXT_BUDGET_CHARS = 400_000

    /**
     * Well-known hosted services (OpenAI, Anthropic, Google, OpenRouter, Mistral, Groq) run models with at least
     * 32k tokens of context, so they get room for a real conversation plus a message full of photos. About 25k tokens.
     */
    const val HOSTED_CONTEXT_BUDGET_CHARS = 100_000

    fun budgetFor(route: ProviderRoute): Int =
        if (route == ProviderRoute.GOOGLE_AI_STUDIO_DIRECT) LARGE_CONTEXT_BUDGET_CHARS
        else ContextWindowPolicy.DEFAULT_BUDGET_CHARS

    /**
     * The budget for this connection. Unknown and local servers keep the small default, since a local model's
     * context window may be only a few thousand tokens.
     */
    fun budgetFor(config: RelayConfig): Int {
        if (config.route == ProviderRoute.GOOGLE_AI_STUDIO_DIRECT) return LARGE_CONTEXT_BUDGET_CHARS
        if (config.route == ProviderRoute.LOCAL_OFFLINE) return ContextWindowPolicy.DEFAULT_BUDGET_CHARS
        val host = runCatching { java.net.URI(config.effectiveBaseUrl.trim()).host }.getOrNull().orEmpty().lowercase()
        if (config.route == ProviderRoute.ANTHROPIC_MESSAGES && (host.isEmpty() || host.isOn("anthropic.com"))) {
            return LARGE_CONTEXT_BUDGET_CHARS
        }
        return if (HOSTED_DOMAINS.any { host.isOn(it) }) HOSTED_CONTEXT_BUDGET_CHARS
        else ContextWindowPolicy.DEFAULT_BUDGET_CHARS
    }

    private val HOSTED_DOMAINS = listOf(
        "openai.com", "anthropic.com", "googleapis.com", "openrouter.ai", "mistral.ai", "groq.com"
    )

    private fun String.isOn(domain: String): Boolean = this == domain || endsWith(".$domain")

    fun trim(
        messages: List<ChatMessage>,
        systemPrompt: String,
        omittedAttachmentIds: Collection<String>,
        budgetChars: Int = ContextWindowPolicy.DEFAULT_BUDGET_CHARS
    ): List<ChatMessage> {
        val (systemMessages, turns) = messages.partition { it.role == MessageRole.SYSTEM }
        if (turns.isEmpty()) return messages
        val reserved = systemPrompt.length.toLong() + systemMessages.sumOf { it.content.length.toLong() }
        val omitted = omittedAttachmentIds.toHashSet()
        val plan = ContextWindowPolicy.plan(
            items = turns.map { msg ->
                ContextWindowPolicy.Item(
                    role = msg.role.name.lowercase(),
                    chars = msg.content.length,
                    imageCount = msg.attachments.count { it.id !in omitted }
                )
            },
            budgetChars = budgetChars,
            systemPromptChars = reserved.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        )
        if (plan.droppedCount == 0) return messages
        var remainingToDrop = plan.droppedCount
        return messages.filter { message ->
            if (message.role == MessageRole.SYSTEM || remainingToDrop == 0) true else {
                remainingToDrop--
                false
            }
        }
    }
}
