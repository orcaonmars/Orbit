package com.hydrogen.ai.model

/**
 * Pure Kotlin policy for trimming long chat histories to fit within a model's character budget.
 * Linear time, never throws, and has zero Android dependencies.
 */
object ContextWindowPolicy {

    const val DEFAULT_BUDGET_CHARS = 24_000
    const val IMAGE_COST_CHARS = 3_000

    data class Item(
        val role: String,
        val chars: Int,
        val imageCount: Int = 0
    )

    data class Plan(
        val keepFromIndex: Int,
        val droppedCount: Int,
        val usedChars: Int,
        val overBudget: Boolean
    )

    fun plan(
        items: List<Item>,
        budgetChars: Int = DEFAULT_BUDGET_CHARS,
        systemPromptChars: Int = 0
    ): Plan {
        val safeSystemChars = systemPromptChars.coerceAtLeast(0)
        val effectiveBudget = if (budgetChars <= 0) DEFAULT_BUDGET_CHARS else budgetChars
        if (items.isEmpty()) {
            return Plan(
                keepFromIndex = 0,
                droppedCount = 0,
                usedChars = safeSystemChars,
                overBudget = safeSystemChars > effectiveBudget
            )
        }

        val budgetLong = effectiveBudget.toLong()
        val lastIndex = items.lastIndex

        var usedLong = safeSystemChars.toLong() + itemCostLong(items[lastIndex])
        var keepFrom = lastIndex

        if (usedLong <= budgetLong) {
            var i = lastIndex - 1
            while (i >= 0) {
                val cost = itemCostLong(items[i])
                if (usedLong + cost <= budgetLong) {
                    usedLong += cost
                    keepFrom = i
                    i--
                } else {
                    break
                }
            }
        }

        while (keepFrom < lastIndex && isAssistantRole(items[keepFrom].role)) {
            usedLong -= itemCostLong(items[keepFrom])
            keepFrom++
        }

        val overBudget = usedLong > budgetLong
        val saturatedUsed = usedLong.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

        return Plan(
            keepFromIndex = keepFrom,
            droppedCount = keepFrom,
            usedChars = saturatedUsed,
            overBudget = overBudget
        )
    }

    private fun itemCostLong(item: Item): Long {
        val safeChars = item.chars.coerceAtLeast(0).toLong()
        val safeImages = item.imageCount.coerceAtLeast(0).toLong()
        val rawCost = safeChars + safeImages * IMAGE_COST_CHARS.toLong()
        return rawCost.coerceAtMost(Int.MAX_VALUE.toLong())
    }

    private fun isAssistantRole(role: String): Boolean =
        role.trim().equals("assistant", ignoreCase = true)
}
