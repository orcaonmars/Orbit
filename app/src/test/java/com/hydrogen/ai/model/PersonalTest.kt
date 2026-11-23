package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalTest {

    private fun chip(label: String) = Personal.STYLE_CHIPS.first { it.label == label }

    @Test
    fun cleanKeepsNameOnOneLineAndWithinLimits() {
        val cleaned = Personal.clean(
            PersonalProfile(
                name = "  Sam \n  Lee  ",
                about = "  I teach math.  ",
                replyStyle = "x".repeat(Personal.MAX_FIELD + 50)
            )
        )
        assertEquals("Sam Lee", cleaned.name)
        assertEquals("I teach math.", cleaned.about)
        assertEquals(Personal.MAX_FIELD, cleaned.replyStyle.length)
        assertEquals(Personal.MAX_NAME, Personal.clean(PersonalProfile(name = "n".repeat(90))).name.length)
    }

    @Test
    fun systemBlockLeavesOutBlankFields() {
        assertNull(Personal.systemBlock(PersonalProfile()))
        assertNull(Personal.systemBlock(PersonalProfile(name = "   ", about = "\n", replyStyle = " ")))

        val nameOnly = Personal.systemBlock(PersonalProfile(name = "Sam"))!!
        assertEquals("About the user:\nTheir name is Sam.", nameOnly)

        val styleOnly = Personal.systemBlock(PersonalProfile(replyStyle = "Keep it short."))!!
        assertEquals("How the user wants you to reply:\nKeep it short.", styleOnly)

        val all = Personal.systemBlock(PersonalProfile("Sam", "I teach math.", "Keep it short."))!!
        assertTrue(all.indexOf("Their name is Sam.") < all.indexOf("I teach math."))
        assertTrue(all.indexOf("I teach math.") < all.indexOf("How the user wants you to reply:"))
    }

    @Test
    fun greetingUsesFirstNameOnly() {
        assertEquals("Sam", Personal.greetingName(PersonalProfile(name = " Sam  Lee ")))
        assertNull(Personal.greetingName(PersonalProfile(name = "  ")))
    }

    @Test
    fun decodeSurvivesGarbageAndRoundTrips() {
        assertEquals(PersonalProfile(), Personal.decode("not json"))
        assertEquals(PersonalProfile(), Personal.decode(""))
        val profile = PersonalProfile("Sam", "I teach math.", "Keep it short.")
        assertEquals(profile, Personal.decode(Personal.encode(profile)))
        // Unknown keys from a newer version are ignored.
        assertEquals("Sam", Personal.decode("{\"name\":\"Sam\",\"pronouns\":\"they\"}").name)
    }

    @Test
    fun chipAddsItsSentenceAndTakesItOutAgain() {
        val short = chip("Short")
        val on = Personal.toggleChip("", short)
        assertEquals(short.sentence, on)
        assertTrue(Personal.hasChip(on, short))
        assertEquals("", Personal.toggleChip(on, short))
    }

    @Test
    fun chipKeepsTypedTextAndReplacesItsOpposite() {
        val typed = "I like lists."
        val withDetail = Personal.toggleChip(typed, chip("Detailed"))
        assertEquals("I like lists. ${chip("Detailed").sentence}", withDetail)

        val withShort = Personal.toggleChip(withDetail, chip("Short"))
        assertFalse(Personal.hasChip(withShort, chip("Detailed")))
        assertTrue(Personal.hasChip(withShort, chip("Short")))
        assertTrue(withShort.startsWith(typed))

        val back = Personal.toggleChip(withShort, chip("Short"))
        assertEquals(typed, back)
    }

    @Test
    fun chipThatWouldOverflowLeavesTheStyleAlone() {
        val full = "x".repeat(Personal.MAX_FIELD - 3)
        assertEquals(full, Personal.toggleChip(full, chip("Examples")))
    }

    @Test
    fun opposingChipsComeInPairs() {
        Personal.STYLE_CHIPS.filter { it.opposite != null }.forEach { c ->
            val other = chip(c.opposite!!)
            assertEquals(c.label, other.opposite)
        }
    }
}
