package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PersonaShareCodecTest {
    private val chef = SharedPersona(
        name = "Chef 👩‍🍳 料理",
        tagline = "Dinner from what you have",
        greeting = "What's in the fridge?",
        instructions = "You are a friendly home cook.\nSuggest quick recipes. Ünïcode ok.",
        starters = listOf(SharedStarter("Tonight", "15 minutes", "Quick dinner with: ", "idea"))
    )

    private fun rawCode(json: String): String =
        PersonaShareCodec.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

    private fun message(result: Result<SharedPersona>): String = result.exceptionOrNull()?.message.orEmpty()

    @Test
    fun roundTripKeepsEmojiAndNonLatinText() {
        val code = PersonaShareCodec.encode(chef)
        assertTrue(code.startsWith(PersonaShareCodec.PREFIX))
        assertTrue(code.none { it.isWhitespace() })
        assertEquals(chef, PersonaShareCodec.decode(code).getOrThrow())
    }

    @Test
    fun everyBuiltInPersonaRoundTrips() {
        Personas.all.forEach { persona ->
            val shared = PersonaShareCodec.fromPersona(persona)
            val back = PersonaShareCodec.decode(PersonaShareCodec.encode(shared)).getOrThrow()
            assertEquals(persona.copy(id = "imported"), PersonaShareCodec.toPersona(back, "imported"))
        }
    }

    @Test
    fun codeIsFoundInsideAPastedMessage() {
        val pasted = "Try my persona!\n\n  ${PersonaShareCodec.encode(chef)}  \nEnjoy."
        assertEquals(chef.name, PersonaShareCodec.decode(pasted).getOrThrow().name)
    }

    @Test
    fun codesSharedBeforeTheRenameStillImport() {
        val legacy = PersonaShareCodec.encode(chef)
            .replaceFirst(PersonaShareCodec.PREFIX, PersonaShareCodec.LEGACY_PREFIX)
        assertTrue(legacy.startsWith("hydrogen-persona:v1:"))
        assertEquals(chef, PersonaShareCodec.decode("Try this: $legacy").getOrThrow())
        val oldV2 = legacy.replace(":v1:", ":v2:")
        assertTrue(message(PersonaShareCodec.decode(oldV2)).contains("newer"))
    }

    @Test
    fun missingOrNewerPrefixFails() {
        assertEquals("No Orbit persona code found.", message(PersonaShareCodec.decode("hello")))
        val v2 = PersonaShareCodec.encode(chef).replace(":v1:", ":v2:")
        assertTrue(message(PersonaShareCodec.decode(v2)).contains("newer"))
    }

    @Test
    fun corruptCodeFailsWithoutThrowing() {
        assertTrue(PersonaShareCodec.decode(PersonaShareCodec.PREFIX + "%%%not-base64").isFailure)
        assertTrue(PersonaShareCodec.decode(rawCode("{not json")).isFailure)
        assertTrue(PersonaShareCodec.decode(PersonaShareCodec.PREFIX).isFailure)
    }

    @Test
    fun oversizedTokenIsRejectedBeforeDecoding() {
        val huge = PersonaShareCodec.PREFIX + "A".repeat(PersonaShareCodec.MAX_TOKEN + 1)
        assertEquals("The persona code is too long.", message(PersonaShareCodec.decode(huge)))
    }

    @Test
    fun blankNameOrInstructionsFail() {
        assertTrue(PersonaShareCodec.validate(chef.copy(name = "  ")).isFailure)
        assertTrue(PersonaShareCodec.validate(chef.copy(instructions = "\n")).isFailure)
        assertTrue(PersonaShareCodec.decode(rawCode("""{"name":"","instructions":"x"}""")).isFailure)
    }

    @Test(expected = IllegalArgumentException::class)
    fun encodeRejectsAnInvalidPersona() {
        PersonaShareCodec.encode(chef.copy(name = "x".repeat(PersonaShareCodec.MAX_NAME + 1)))
    }

    @Test
    fun tooManyOrDuplicateStartersFail() {
        val five = (1..5).map { SharedStarter("S$it", prompt = "p$it") }
        assertTrue(PersonaShareCodec.validate(chef.copy(starters = five)).isFailure)
        val dupes = listOf(SharedStarter("Plan", prompt = "a"), SharedStarter("plan", prompt = "b"))
        assertEquals("Two starters have the same title.", message(PersonaShareCodec.validate(chef.copy(starters = dupes))))
    }

    @Test
    fun unknownIconBecomesDefault() {
        val odd = chef.copy(starters = listOf(SharedStarter("Go", prompt = "p", icon = "javascript:alert")))
        assertEquals("idea", PersonaShareCodec.validate(odd).getOrThrow().starters.single().icon)
    }

    @Test
    fun controlCharactersAreStrippedButNewlinesKept() {
        val dirty = chef.copy(name = "Ch\u0000ef\u001B", instructions = "Line one\r\nLine\ttwo\u0007")
        val clean = PersonaShareCodec.validate(dirty).getOrThrow()
        assertEquals("Chef", clean.name)
        assertEquals("Line one\nLine\ttwo", clean.instructions)
    }

    @Test
    fun unknownJsonKeysAreIgnoredAndIdIsNeverImported() {
        val shared = PersonaShareCodec.decode(
            rawCode("""{"id":"tutor","name":"Pirate","instructions":"Talk like a pirate.","future":42}""")
        ).getOrThrow()
        assertEquals("Pirate", shared.name)
        assertEquals("local-1", PersonaShareCodec.toPersona(shared, "local-1").id)
    }
}
