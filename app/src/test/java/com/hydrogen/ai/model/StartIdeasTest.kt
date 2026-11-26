package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartIdeasTest {
    @Test fun pickIsStableForASeedAndDistinct() {
        val a = StartIdeas.pick(42L)
        assertEquals(a, StartIdeas.pick(42L))
        assertEquals(3, a.size)
        assertEquals(3, a.map { it.id }.toSet().size)
    }

    @Test fun pickClampsTheCount() {
        assertEquals(StartIdeas.ALL.size, StartIdeas.pick(1L, 99).size)
        assertTrue(StartIdeas.pick(1L, -1).isEmpty())
    }

    @Test fun copyFollowsTheHouseStyle() {
        assertEquals(StartIdeas.ALL.size, StartIdeas.ALL.map { it.id }.toSet().size)
        for (idea in StartIdeas.ALL) {
            for (text in listOf(idea.title, idea.detail, idea.draft)) {
                assertFalse(text, text.contains('—') || text.contains('–') || text.contains('!') || text.contains('&'))
            }
            assertTrue(idea.title, idea.title.first().isUpperCase() && idea.title.drop(1) == idea.title.drop(1).lowercase())
            assertTrue(idea.draft, idea.draft.isNotBlank() && idea.draft.length < 140)
        }
    }
}
