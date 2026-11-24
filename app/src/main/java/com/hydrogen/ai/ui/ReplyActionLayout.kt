package com.hydrogen.ai.ui

internal enum class ReplyAction { COPY, READ, RETRY, SHARE, COMPARE, BRANCH }

internal data class ReplyActionPlan(val inline: List<ReplyAction>, val overflow: List<ReplyAction>)

/** Keep common actions easy to reach and guarantee a 48 dp target without clipping secondary actions. */
internal object ReplyActionLayout {
    private const val TARGET_DP = 48f
    private val secondary = setOf(ReplyAction.COMPARE, ReplyAction.BRANCH)

    fun plan(available: List<ReplyAction>, widthDp: Float): ReplyActionPlan {
        val ordered = available.distinct()
        val primary = ordered.filterNot { it in secondary }
        val slots = (widthDp / TARGET_DP).toInt().coerceIn(1, ReplyAction.entries.size + 1)
        val needsMenu = ordered.any { it in secondary } || primary.size > slots
        val inline = primary.take(if (needsMenu) slots - 1 else slots)
        return ReplyActionPlan(inline, ordered.filterNot { it in inline })
    }
}
