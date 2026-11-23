package com.hydrogen.ai.model

import com.hydrogen.ai.model.ContextWindowPolicy.DEFAULT_BUDGET_CHARS
import com.hydrogen.ai.model.ContextWindowPolicy.IMAGE_COST_CHARS
import com.hydrogen.ai.model.ContextWindowPolicy.Item
import com.hydrogen.ai.model.ContextWindowPolicy.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWindowPolicyTest {

    @Test
    fun allFitKeepsEverythingWithExactUsedChars() {
        val items = listOf(
            Item(role = "user", chars = 400),
            Item(role = "assistant", chars = 600),
            Item(role = "user", chars = 300)
        )

        val plan = ContextWindowPolicy.plan(
            items = items,
            budgetChars = 2_000,
            systemPromptChars = 200
        )

        assertEquals(
            Plan(keepFromIndex = 0, droppedCount = 0, usedChars = 1_500, overBudget = false),
            plan
        )
    }

    @Test
    fun oldestDroppedFirstAndPreservesContiguousSuffix() {
        // Index 0 is small enough that 0 + 2 + 3 would fit,
        // but index 1 breaks contiguity so 0 and 1 must both be dropped.
        val items = listOf(
            Item(role = "user", chars = 10),
            Item(role = "assistant", chars = 500),
            Item(role = "user", chars = 200),
            Item(role = "assistant", chars = 250)
        )

        val plan = ContextWindowPolicy.plan(
            items = items,
            budgetChars = 600,
            systemPromptChars = 0
        )

        assertEquals(
            Plan(keepFromIndex = 2, droppedCount = 2, usedChars = 450, overBudget = false),
            plan
        )
    }

    @Test
    fun newestAloneOverBudgetIsAlwaysKeptAndFlagged() {
        val items = listOf(
            Item(role = "user", chars = 100),
            Item(role = "assistant", chars = 100),
            Item(role = "user", chars = 1_500)
        )

        val plan = ContextWindowPolicy.plan(
            items = items,
            budgetChars = 1_000,
            systemPromptChars = 200
        )

        assertEquals(
            Plan(keepFromIndex = 2, droppedCount = 2, usedChars = 1_700, overBudget = true),
            plan
        )
    }

    @Test
    fun imagesCostIsIncludedAndNegativeInputsAreClampedToZero() {
        val items = listOf(
            Item(role = "user", chars = 500, imageCount = 1),
            Item(role = "assistant", chars = -50, imageCount = -3),
            Item(role = "user", chars = 200, imageCount = 2)
        )

        // Item 2 costs 6200; Item 1 costs 0; Item 0 costs 3500.
        // Budget 7000 fits items 1 and 2 (6200) but not item 0 (9700).
        // Item 1 is leading "assistant", so keepFromIndex advances to 2.
        val plan = ContextWindowPolicy.plan(
            items = items,
            budgetChars = 7_000,
            systemPromptChars = -100
        )

        assertEquals(
            Plan(keepFromIndex = 2, droppedCount = 2, usedChars = 200 + 2 * IMAGE_COST_CHARS, overBudget = false),
            plan
        )
    }

    @Test
    fun leadingAssistantIsSkippedUnlessItWouldDropNewestItem() {
        val items = listOf(
            Item(role = "user", chars = 2_000),
            Item(role = "assistant", chars = 300),
            Item(role = "assistant", chars = 200),
            Item(role = "user", chars = 400),
            Item(role = "assistant", chars = 150)
        )

        val trimmedPlan = ContextWindowPolicy.plan(items = items, budgetChars = 1_200)
        assertEquals(
            Plan(keepFromIndex = 3, droppedCount = 3, usedChars = 550, overBudget = false),
            trimmedPlan
        )

        val allAssistants = listOf(
            Item(role = "assistant", chars = 100),
            Item(role = "assistant", chars = 200)
        )
        val newestAssistantKept = ContextWindowPolicy.plan(items = allAssistants, budgetChars = 1_000)
        assertEquals(
            Plan(keepFromIndex = 1, droppedCount = 1, usedChars = 200, overBudget = false),
            newestAssistantKept
        )
    }

    @Test
    fun systemPromptIsAlwaysReserved() {
        val items = listOf(
            Item(role = "user", chars = 400),
            Item(role = "assistant", chars = 300),
            Item(role = "user", chars = 350)
        )

        val plan = ContextWindowPolicy.plan(
            items = items,
            budgetChars = 1_000,
            systemPromptChars = 400
        )
        assertEquals(
            Plan(keepFromIndex = 2, droppedCount = 2, usedChars = 750, overBudget = false),
            plan
        )

        val overPlan = ContextWindowPolicy.plan(
            items = items,
            budgetChars = 500,
            systemPromptChars = 600
        )
        assertEquals(
            Plan(keepFromIndex = 2, droppedCount = 2, usedChars = 950, overBudget = true),
            overPlan
        )
    }

    @Test
    fun emptyListAndInvalidBudgetUseDefaultsSafely() {
        val emptyPlan = ContextWindowPolicy.plan(
            items = emptyList(),
            budgetChars = 500,
            systemPromptChars = 250
        )
        assertEquals(Plan(keepFromIndex = 0, droppedCount = 0, usedChars = 250, overBudget = false), emptyPlan)

        val emptyOverSystem = ContextWindowPolicy.plan(
            items = emptyList(),
            budgetChars = 100,
            systemPromptChars = 500
        )
        assertEquals(Plan(keepFromIndex = 0, droppedCount = 0, usedChars = 500, overBudget = false), emptyOverSystem)

        val exactDefaultItems = listOf(
            Item(role = "user", chars = 1),
            Item(role = "user", chars = DEFAULT_BUDGET_CHARS - 1)
        )
        val zeroBudgetPlan = ContextWindowPolicy.plan(items = exactDefaultItems, budgetChars = 0)
        assertEquals(
            Plan(keepFromIndex = 0, droppedCount = 0, usedChars = DEFAULT_BUDGET_CHARS, overBudget = false),
            zeroBudgetPlan
        )

        val overDefaultItems = listOf(
            Item(role = "user", chars = 2),
            Item(role = "user", chars = DEFAULT_BUDGET_CHARS - 1)
        )
        val negativeBudgetPlan = ContextWindowPolicy.plan(items = overDefaultItems, budgetChars = -42)
        assertEquals(
            Plan(keepFromIndex = 1, droppedCount = 1, usedChars = DEFAULT_BUDGET_CHARS - 1, overBudget = false),
            negativeBudgetPlan
        )
    }

    @Test
    fun intSaturationPreventsOverflowAndWrapAround() {
        val hugeImageItems = listOf(
            Item(role = "user", chars = 10),
            Item(role = "user", chars = 500, imageCount = Int.MAX_VALUE)
        )

        val imageOverflowPlan = ContextWindowPolicy.plan(
            items = hugeImageItems,
            budgetChars = 10_000,
            systemPromptChars = 0
        )
        assertEquals(1, imageOverflowPlan.keepFromIndex)
        assertEquals(1, imageOverflowPlan.droppedCount)
        assertEquals(Int.MAX_VALUE, imageOverflowPlan.usedChars)
        assertTrue(imageOverflowPlan.overBudget)

        val maxPlusSystemPlan = ContextWindowPolicy.plan(
            items = listOf(Item(role = "user", chars = Int.MAX_VALUE)),
            budgetChars = Int.MAX_VALUE,
            systemPromptChars = 100
        )
        assertEquals(0, maxPlusSystemPlan.keepFromIndex)
        assertEquals(0, maxPlusSystemPlan.droppedCount)
        assertEquals(Int.MAX_VALUE, maxPlusSystemPlan.usedChars)
        assertTrue(maxPlusSystemPlan.overBudget)

        val maxExactFitPlan = ContextWindowPolicy.plan(
            items = listOf(Item(role = "user", chars = Int.MAX_VALUE)),
            budgetChars = Int.MAX_VALUE,
            systemPromptChars = 0
        )
        assertEquals(Int.MAX_VALUE, maxExactFitPlan.usedChars)
        assertFalse(maxExactFitPlan.overBudget)
    }
}
