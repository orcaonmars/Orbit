package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectImportBudgetTest {
    @Test fun failedFilesDoNotConsumeRemainingSlots() {
        val budget = ProjectImportBudget(19, 20)
        assertTrue(budget.beginRead())
        assertTrue(budget.beginRead())
        budget.recordAdded()
        assertEquals(2, budget.attempted)
        assertEquals(1, budget.added)
        assertTrue(budget.full)
        assertFalse(budget.beginRead())
    }

    @Test fun fullProjectDoesNotReadAnyFiles() {
        val budget = ProjectImportBudget(20, 20)
        assertFalse(budget.beginRead())
        assertEquals(0, budget.attempted)
    }

    @Test fun failedBatchIsBoundedWithoutPretendingTheProjectIsFull() {
        val budget = ProjectImportBudget(0, 20, maxAttempts = 3)
        repeat(3) { assertTrue(budget.beginRead()) }
        assertFalse(budget.beginRead())
        assertFalse(budget.full)
        assertTrue(budget.attemptLimitReached)
        assertEquals(0, budget.added)
    }

    @Test fun onlySuccessfulAddsFillCapacity() {
        val budget = ProjectImportBudget(17, 20)
        repeat(3) {
            assertTrue(budget.beginRead())
            budget.recordAdded()
        }
        assertFalse(budget.canRead)
        assertEquals(3, budget.added)
    }
}
