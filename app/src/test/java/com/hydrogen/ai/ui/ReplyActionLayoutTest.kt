package com.hydrogen.ai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyActionLayoutTest {
    private val all = ReplyAction.entries.toList()

    @Test fun familiarActionsRemainInlineOnNormalPhone() {
        val plan = ReplyActionLayout.plan(all, 320f)
        assertEquals(listOf(ReplyAction.COPY, ReplyAction.READ, ReplyAction.RETRY, ReplyAction.SHARE), plan.inline)
        assertEquals(listOf(ReplyAction.COMPARE, ReplyAction.BRANCH), plan.overflow)
    }

    @Test fun narrowWidthMovesActionsIntoOverflowWithoutLosingThem() {
        val plan = ReplyActionLayout.plan(all, 150f)
        assertEquals(2, plan.inline.size)
        assertEquals(all, plan.inline + plan.overflow)
        assertTrue((plan.inline.size + 1) * 48 <= 150)
    }

    @Test fun veryNarrowWidthStillOffersEveryActionInOneMenu() {
        val plan = ReplyActionLayout.plan(all, 80f)
        assertTrue(plan.inline.isEmpty())
        assertEquals(all, plan.overflow)
    }

    @Test fun noExtraMenuWhenOnlyCommonActionsFit() {
        val actions = listOf(ReplyAction.COPY, ReplyAction.READ)
        val plan = ReplyActionLayout.plan(actions, 96f)
        assertEquals(actions, plan.inline)
        assertTrue(plan.overflow.isEmpty())
    }

    @Test fun emptyAndDuplicateActionsCannotCreateRedundantControls() {
        assertEquals(ReplyActionPlan(emptyList(), emptyList()), ReplyActionLayout.plan(emptyList(), 320f))
        val actions = listOf(ReplyAction.COPY, ReplyAction.COPY, ReplyAction.BRANCH)
        assertEquals(ReplyActionPlan(listOf(ReplyAction.COPY), listOf(ReplyAction.BRANCH)), ReplyActionLayout.plan(actions, 320f))
    }
}
