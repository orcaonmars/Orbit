package com.hydrogen.ai.model

import java.util.Locale

/**
 * How full a chat's context window is for the next send. Measured with the same character budget and
 * trimming rule as [ContextWindowTrimmer], so the meter tells the truth about what the model will see.
 */
data class ContextUsage(
    val usedChars: Long,
    val budgetChars: Int,
    val droppedMessages: Int
) {
    enum class Level { HIDDEN, NOTICE, NEAR, TRIMMED }

    /** Share of the budget in use; may exceed 1 when older messages are being dropped. */
    val fraction: Float
        get() = if (budgetChars <= 0) 0f else (usedChars.toDouble() / budgetChars).toFloat().coerceAtLeast(0f)

    val approxTokens: Long get() = (usedChars + ContextMeter.CHARS_PER_TOKEN - 1) / ContextMeter.CHARS_PER_TOKEN
    val budgetTokens: Long get() = budgetChars.toLong() / ContextMeter.CHARS_PER_TOKEN

    val level: Level
        get() = when {
            droppedMessages > 0 -> Level.TRIMMED
            fraction >= NEAR_FRACTION -> Level.NEAR
            fraction >= SHOW_FRACTION -> Level.NOTICE
            else -> Level.HIDDEN
        }

    /** Whole percent for the label, never above 100. */
    val percent: Int get() = (fraction * 100).toInt().coerceIn(0, 100)

    /**
     * One short line next to the context ring, in plain words (docs/ORBIT_DESIGN.md, "Context ring tooltip");
     * empty when the ring is hidden.
     */
    val label: String
        get() = when (level) {
            Level.HIDDEN -> ""
            Level.TRIMMED ->
                if (droppedMessages == 1) "Long chat: the oldest message is no longer sent"
                else "Long chat: the oldest $droppedMessages messages are no longer sent"
            Level.NEAR -> "This chat is $percent% full. Older messages may be left out"
            Level.NOTICE -> "This chat is $percent% full"
        }

    companion object {
        const val SHOW_FRACTION = 0.5f
        const val NEAR_FRACTION = 0.85f
    }
}

object ContextMeter {
    /** Rough average for English text; the meter always shows "~". */
    const val CHARS_PER_TOKEN = 4

    /** Same filter the send path applies before trimming: failed or empty replies are never sent. */
    fun isSent(message: ChatMessage): Boolean = when (message.role) {
        MessageRole.SYSTEM, MessageRole.USER -> true
        MessageRole.ASSISTANT -> message.status == MessageStatus.COMPLETED &&
            (message.content.isNotBlank() || message.turnMetadata?.toolCalls?.isNotEmpty() == true)
    }

    /**
     * Usage if the user sent now: the chat's sendable history plus the current draft ([draftChars] text and
     * [draftImages] attached images).
     */
    fun measure(
        messages: List<ChatMessage>,
        systemPrompt: String,
        route: ProviderRoute,
        draftChars: Int = 0,
        draftImages: Int = 0
    ): ContextUsage {
        val budget = ContextWindowTrimmer.budgetFor(route)
        val (system, turns) = messages.filter(::isSent).partition { it.role == MessageRole.SYSTEM }
        val reserved = systemPrompt.length.toLong() + system.sumOf { it.content.length.toLong() }
        val items = turns.map {
            ContextWindowPolicy.Item(
                it.role.name.lowercase(), it.content.length, it.attachments.count { a -> !a.omittedFromRemote }
            )
        } + if (draftChars > 0 || draftImages > 0) {
            listOf(ContextWindowPolicy.Item("user", draftChars.coerceAtLeast(0), draftImages.coerceAtLeast(0)))
        } else {
            emptyList()
        }
        if (items.isEmpty()) return ContextUsage(reserved, budget, 0)
        val total = reserved + items.sumOf {
            it.chars.toLong() + it.imageCount.toLong() * ContextWindowPolicy.IMAGE_COST_CHARS
        }
        val plan = ContextWindowPolicy.plan(
            items = items,
            budgetChars = budget,
            systemPromptChars = reserved.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        )
        return ContextUsage(total, budget, plan.droppedCount)
    }

    /** 950 -> "950", 6000 -> "6k", 4200 -> "4.2k", 100000 -> "100k". */
    fun compact(value: Long): String = when {
        value < 1_000 -> value.coerceAtLeast(0).toString()
        value < 9_950 -> String.format(Locale.US, "%.1f", value / 1_000.0).removeSuffix(".0") + "k"
        else -> "${(value + 500) / 1_000}k"
    }
}
