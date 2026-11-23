package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaTest {

    @Test
    fun personasAreUniqueCompleteAndFitTheInstructionLimit() {
        assertEquals(Personas.all.size, Personas.all.map { it.id }.toSet().size)
        assertEquals(Personas.all.size, Personas.all.map { it.name }.toSet().size)
        Personas.all.forEach { p ->
            assertTrue(p.name.isNotBlank() && p.tagline.isNotBlank() && p.greeting.isNotBlank())
            assertEquals("instructions of ${p.id} must survive normalization", p.instructions,
                SystemPromptPolicy.normalize(p.instructions))
            assertTrue(p.instructions.length <= SystemPromptPolicy.MAX_LENGTH)
            // Names must fit a chip on a 320dp phone.
            assertTrue(p.name.length <= 12)
        }
    }

    @Test
    fun everyPersonaHasFourDistinctStartersWithKnownIcons() {
        Personas.all.forEach { p ->
            assertEquals("${p.id} needs a full 2x2 grid", 4, p.starters.size)
            assertEquals(4, p.starters.map { it.title }.toSet().size)
            p.starters.forEach { s ->
                assertTrue(s.title.isNotBlank() && s.hint.isNotBlank() && s.prompt.isNotBlank())
                // Titles share a half-width card on a 320dp phone.
                assertTrue("${s.title} too long", s.title.length <= 12)
                assertTrue("unknown icon ${s.icon}", s.icon in Personas.STARTER_ICONS)
                // The prompt is a draft the user finishes, so it must leave room to type.
                assertTrue(s.prompt.endsWith(" ") || s.prompt.endsWith("\n") || s.prompt.endsWith("."))
            }
        }
    }

    @Test
    fun matchingFindsThePersonaFromAChatsInstructions() {
        Personas.all.forEach { p ->
            assertEquals(p, Personas.matching(p.instructions))
            assertEquals(p, Personas.matching("  ${p.instructions}\n"))
            assertEquals(p, Personas.byId(p.id))
        }
        assertNull(Personas.matching(""))
        assertNull(Personas.matching("   "))
        assertNull(Personas.matching("Answer like a pirate."))
        assertNull(Personas.byId("nope"))
    }

    @Test
    fun customInstructionsAreNeverMistakenForAPersona() {
        assertFalse(Personas.hasCustomInstructions(""))
        assertFalse(Personas.hasCustomInstructions(Personas.all.first().instructions))
        assertTrue(Personas.hasCustomInstructions("Answer like a pirate."))
        assertTrue(Personas.hasCustomInstructions(Personas.all.first().instructions + " Also be brief."))
    }
}
