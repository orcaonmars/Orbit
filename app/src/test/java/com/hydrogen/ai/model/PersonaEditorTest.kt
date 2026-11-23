package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaEditorTest {
    private val chef = PersonaDraft(name = "  Chef ", instructions = "You are a friendly chef.")

    @Test
    fun nameAndInstructionsAreEnoughAndGetFriendlyDefaults() {
        val built = PersonaEditor.build(chef).getOrThrow()
        assertEquals("Chef", built.name)
        assertEquals(PersonaEditor.DEFAULT_TAGLINE, built.tagline)
        assertEquals("Hi, I'm Chef. How can I help?", built.greeting)
        assertTrue(built.starters.isEmpty())
    }

    @Test
    fun missingNameOrInstructionsIsExplained() {
        assertEquals("The persona needs a name.", PersonaEditor.build(chef.copy(name = " ")).exceptionOrNull()?.message)
        assertEquals("The persona needs instructions.", PersonaEditor.build(chef.copy(instructions = "\n")).exceptionOrNull()?.message)
    }

    @Test
    fun starterTitleAndHintComeFromThePrompt() {
        assertEquals("Write a haiku" to "about the sea", PersonaEditor.splitStarter("Write a haiku about the sea"))
        assertEquals("Plan dinner" to "", PersonaEditor.splitStarter("  Plan   dinner:  "))
        val (title, hint) = PersonaEditor.splitStarter("Supercalifragilisticexpialidocious")
        assertTrue(title.length <= PersonaShareCodec.MAX_STARTER_TITLE && title.endsWith("…"))
        assertEquals("", hint)
        val long = PersonaEditor.splitStarter("Give me " + "very ".repeat(20) + "long ideas").second
        assertTrue(long.length <= PersonaShareCodec.MAX_STARTER_HINT && long.endsWith("…"))
    }

    @Test
    fun blankStartersAreSkippedAndDuplicateTitlesRenamed() {
        val built = PersonaEditor.build(
            chef.copy(starterPrompts = listOf("", "Make a meal plan for the week", "  ", "Make a meal plan for a party"))
        ).getOrThrow()
        assertEquals(listOf("Make a meal plan", "Idea 2"), built.starters.map { it.title })
        assertEquals("Make a meal plan for a party", built.starters[1].prompt)
    }

    @Test
    fun tooManyStartersIsRefused() {
        val result = PersonaEditor.build(chef.copy(starterPrompts = (1..5).map { "Idea number $it" }))
        assertTrue(result.isFailure)
    }

    @Test
    fun editingRoundTripsThroughTheDraft() {
        val built = PersonaEditor.build(chef.copy(starterPrompts = listOf("Suggest a recipe with "))).getOrThrow()
        val persona = PersonaShareCodec.toPersona(built, "custom-1")
        assertEquals(built, PersonaEditor.build(PersonaEditor.fromPersona(persona)).getOrThrow())
    }

    @Test
    fun createRefusesTakenNamesAndAFullLibrary() {
        val built = PersonaEditor.build(chef).getOrThrow()
        val one = CustomPersonaLibrary.create(emptyList(), built, "custom-1").getOrThrow()
        assertEquals(listOf("custom-1"), one.map { it.id })
        assertTrue(CustomPersonaLibrary.create(one, built.copy(name = "chef"), "custom-2").isFailure)
        assertTrue(CustomPersonaLibrary.create(one, built.copy(name = "Tutor"), "custom-2").isFailure)
        val full = (1..CustomPersonaLibrary.MAX_PERSONAS).map { PersonaShareCodec.toPersona(built.copy(name = "P$it"), "custom-$it") }
        assertTrue(CustomPersonaLibrary.create(full, built, "custom-new").isFailure)
    }

    @Test
    fun updateKeepsIdAndPositionAndChecksNames() {
        val a = PersonaShareCodec.toPersona(PersonaEditor.build(chef).getOrThrow(), "custom-a")
        val b = PersonaShareCodec.toPersona(PersonaEditor.build(chef.copy(name = "Baker")).getOrThrow(), "custom-b")
        val edited = PersonaEditor.build(chef.copy(name = "Head chef", instructions = "Be bold.")).getOrThrow()
        val updated = CustomPersonaLibrary.update(listOf(a, b), "custom-a", edited).getOrThrow()
        assertEquals(listOf("custom-a", "custom-b"), updated.map { it.id })
        assertEquals("Be bold.", updated[0].instructions)
        // Keeping its own name is fine; taking another's is not.
        assertTrue(CustomPersonaLibrary.update(listOf(a, b), "custom-a", edited.copy(name = "CHEF")).isSuccess)
        assertTrue(CustomPersonaLibrary.update(listOf(a, b), "custom-a", edited.copy(name = "baker")).isFailure)
        assertTrue(CustomPersonaLibrary.update(listOf(a, b), "custom-zzz", edited).isFailure)
    }
}
