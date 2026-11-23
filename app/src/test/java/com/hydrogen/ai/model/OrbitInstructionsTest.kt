package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitInstructionsTest {

    @Test
    fun partsGoFromGeneralToSpecific() {
        val text = OrbitInstructions.compose(
            PersonalProfile(name = "Sam"),
            memoryBlock = "Saved notes:\n- I live in Toronto",
            chatInstructions = "Answer in French."
        )
        val style = text.indexOf(OrbitInstructions.DEFAULT_STYLE)
        val personal = text.indexOf("Their name is Sam.")
        val memory = text.indexOf("I live in Toronto")
        val chat = text.indexOf("Answer in French.")
        assertEquals(0, style)
        assertTrue(style < personal && personal < memory && memory < chat)
    }

    @Test
    fun blankPartsAreSkipped() {
        val text = OrbitInstructions.compose(PersonalProfile(), memoryBlock = "   ", chatInstructions = "  ")
        assertEquals(OrbitInstructions.DEFAULT_STYLE, text)
        assertFalse(text.contains("\n\n\n"))
    }

    @Test
    fun defaultStyleAvoidsFillerAndDashes() {
        // The style asks the model not to use filler, so the style itself must not be written that way.
        assertFalse(OrbitInstructions.DEFAULT_STYLE.contains("—"))
        assertFalse(OrbitInstructions.DEFAULT_STYLE.contains("!"))
    }

    @Test
    fun applyToReplacesAnEarlierSystemMessage() {
        val old = ChatMessage(
            id = SystemPromptPolicy.MESSAGE_ID,
            correlationId = SystemPromptPolicy.MESSAGE_ID,
            role = MessageRole.SYSTEM,
            content = "old",
            createdAtEpochMs = 1L
        )
        val user = ChatMessage(id = "u1", correlationId = "c1", role = MessageRole.USER, content = "Hi", createdAtEpochMs = 2L)

        val applied = OrbitInstructions.applyTo(listOf(old, user), "new", 5L)
        assertEquals(listOf(SystemPromptPolicy.MESSAGE_ID, "u1"), applied.map { it.id })
        assertEquals("new", applied.first().content)
        assertEquals(MessageRole.SYSTEM, applied.first().role)

        val removed = OrbitInstructions.applyTo(listOf(old, user), "  ", 5L)
        assertEquals(listOf("u1"), removed.map { it.id })
    }
}
