package com.hydrogen.ai.model

/**
 * "Ask another model": the latest question can be answered again by a different model, and the answers are
 * shown one after another. The first answer stays the conversation's answer; compared answers
 * ([ChatMessage.comparison]) are for reading only and are never sent back as context, which keeps the
 * user/assistant turns alternating (local chat templates such as Gemma's reject two assistant turns in a row).
 */
object CompareModelsPolicy {
    /** Answers one question may have, the first one included. */
    const val MAX_ANSWERS = 4

    /**
     * True when the latest turn can get another model's answer: it ends with completed assistant replies to a
     * user message, none of them requested tools (never re-run possible PC actions), and there is room left.
     */
    fun canCompare(messages: List<ChatMessage>): Boolean {
        val question = questionIndex(messages) ?: return false
        val answers = messages.subList(question + 1, messages.size)
        if (answers.isEmpty() || answers.size >= MAX_ANSWERS) return false
        return answers.all {
            it.role == MessageRole.ASSISTANT &&
                it.status == MessageStatus.COMPLETED &&
                !it.hasSideEffectsInProgress &&
                it.turnMetadata?.toolCalls.isNullOrEmpty()
        }
    }

    /** Index of the latest user message, or null when there is none. */
    fun questionIndex(messages: List<ChatMessage>): Int? =
        messages.indexOfLast { it.role == MessageRole.USER }.takeIf { it >= 0 }

    /** What another model is asked: the conversation up to and including the latest question, without comparisons. */
    fun promptHistory(messages: List<ChatMessage>): List<ChatMessage> {
        val question = questionIndex(messages) ?: return emptyList()
        return context(messages.take(question + 1))
    }

    /** Messages that may be sent to a model as context: compared answers are left out. */
    fun context(messages: List<ChatMessage>): List<ChatMessage> = messages.filterNot { it.comparison }

    /** Models that already answered the latest question. */
    fun answeredModels(messages: List<ChatMessage>): Set<String> {
        val question = questionIndex(messages) ?: return emptySet()
        return messages.drop(question + 1).map { it.modelId.trim() }.toSet()
    }

    /**
     * Models worth offering: listed by the server, not answered yet, in the server's order, without blanks,
     * repeats or embedding models (servers such as LM Studio list those too, but they cannot chat).
     */
    fun choices(listed: List<String>, answered: Set<String>): List<String> =
        listed.map { it.trim() }
            .filter { it.isNotEmpty() && it !in answered && !it.contains("embed", ignoreCase = true) }
            .distinct()

    /** Whether the label with the model's name is shown above [index]: on every answer of a compared question. */
    fun showsModelLabel(messages: List<ChatMessage>, index: Int): Boolean {
        val message = messages.getOrNull(index) ?: return false
        if (message.role != MessageRole.ASSISTANT) return false
        if (message.comparison) return true
        return messages.getOrNull(index + 1)?.comparison == true
    }
}
