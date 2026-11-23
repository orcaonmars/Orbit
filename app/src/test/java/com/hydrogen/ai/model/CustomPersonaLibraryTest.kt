package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomPersonaLibraryTest {
    private fun shared(name: String, instructions: String = "Be $name.") = SharedPersona(name = name, instructions = instructions)

    @Test
    fun encodeDecodeRoundTrip() {
        val list = CustomPersonaLibrary.add(emptyList(), shared("Chef").copy(greeting = "Hungry?"), "custom-1")
        assertEquals(list, CustomPersonaLibrary.decode(CustomPersonaLibrary.encode(list)))
    }

    @Test
    fun brokenJsonIsNull() {
        assertNull(CustomPersonaLibrary.decode("{oops"))
    }

    @Test
    fun badEntriesAreDroppedAndGoodOnesKept() {
        val text = """[
            {"id":"tutor","persona":{"name":"Fake","instructions":"x"}},
            {"id":"custom-a","persona":{"name":"Good","instructions":"ok"}},
            {"id":"custom-a","persona":{"name":"Dupe","instructions":"ok"}},
            {"id":"custom-b","persona":{"name":"","instructions":"no name"}}
        ]"""
        assertEquals(listOf("Good"), CustomPersonaLibrary.decode(text)!!.map { it.name })
    }

    @Test
    fun addPutsNewestFirstAndIgnoresExactDuplicates() {
        var list = CustomPersonaLibrary.add(emptyList(), shared("Chef"), "custom-1")
        list = CustomPersonaLibrary.add(list, shared("Pirate"), "custom-2")
        assertEquals(listOf("Pirate", "Chef"), list.map { it.name })
        assertSame(list, CustomPersonaLibrary.add(list, shared("chef", "Be Chef."), "custom-3"))
    }

    @Test
    fun nameClashesGetANumber() {
        var list = CustomPersonaLibrary.add(emptyList(), shared("Tutor", "Other rules."), "custom-1")
        assertEquals("Tutor (2)", list.first().name)
        list = CustomPersonaLibrary.add(list, shared("Tutor", "Third rules."), "custom-2")
        assertEquals("Tutor (3)", list.first().name)
    }

    @Test
    fun capDropsTheOldest() {
        var list = emptyList<Persona>()
        (1..CustomPersonaLibrary.MAX_PERSONAS + 1).forEach { list = CustomPersonaLibrary.add(list, shared("P$it"), "custom-$it") }
        assertEquals(CustomPersonaLibrary.MAX_PERSONAS, list.size)
        assertEquals("P21", list.first().name)
        assertTrue(list.none { it.name == "P1" })
    }

    @Test
    fun removeById() {
        val list = CustomPersonaLibrary.add(emptyList(), shared("Chef"), "custom-1")
        assertTrue(CustomPersonaLibrary.remove(list, "custom-1").isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun addRejectsANonCustomId() {
        CustomPersonaLibrary.add(emptyList(), shared("Chef"), "tutor")
    }

    @Test
    fun matchingFindsCustomPersonas() {
        val list = CustomPersonaLibrary.add(emptyList(), shared("Chef"), "custom-1")
        assertEquals("custom-1", Personas.matching("Be Chef.", list)?.id)
        assertTrue(!Personas.hasCustomInstructions("Be Chef.", list))
        assertTrue(Personas.hasCustomInstructions("Be Chef."))
    }
}
