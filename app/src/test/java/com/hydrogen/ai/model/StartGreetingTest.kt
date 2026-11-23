package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartGreetingTest {

    @Test
    fun greetingFollowsTheLocalHour() {
        assertEquals("Still up?", StartGreeting.greeting(4))
        assertEquals("Good morning", StartGreeting.greeting(5))
        assertEquals("Good morning", StartGreeting.greeting(11))
        assertEquals("Good afternoon", StartGreeting.greeting(12))
        assertEquals("Good afternoon", StartGreeting.greeting(17))
        assertEquals("Good evening", StartGreeting.greeting(18))
        assertEquals("Good evening", StartGreeting.greeting(22))
        assertEquals("Still up?", StartGreeting.greeting(23))
        assertEquals("Still up?", StartGreeting.greeting(0))
    }

    @Test
    fun outOfRangeHoursWrapInsteadOfFailing() {
        assertEquals("Good morning", StartGreeting.greeting(24 + 9))
        assertEquals("Good evening", StartGreeting.greeting(-4))
    }

    @Test
    fun aFirstNameIsAddedExceptLateAtNight() {
        assertEquals("Good morning, Sam", StartGreeting.greeting(9, "Sam"))
        assertEquals("Good evening, Sam", StartGreeting.greeting(20, "  Sam  "))
        assertEquals("Still up?", StartGreeting.greeting(2, "Sam"))
        assertEquals("Good afternoon", StartGreeting.greeting(14, "   "))
        assertEquals("Good afternoon", StartGreeting.greeting(14, null))
        val long = "A".repeat(60)
        assertEquals("Good afternoon, " + "A".repeat(24), StartGreeting.greeting(14, long))
    }

    @Test
    fun sublinesAreShortPlainAndNotShouty() {
        assertTrue(StartGreeting.SUBLINES.isNotEmpty())
        StartGreeting.SUBLINES.forEach { line ->
            assertTrue(line, line.length <= 40)
            assertFalse(line, line.contains('!'))
            assertFalse(line, line.contains('—'))
        }
    }

    @Test
    fun theSunCrossesTheSkyFromSixToSix() {
        assertEquals(StartGreeting.SkyPosition(0f, day = true), StartGreeting.skyPosition(6 * 60))
        assertEquals(StartGreeting.SkyPosition(0.5f, day = true), StartGreeting.skyPosition(12 * 60))
        val dusk = StartGreeting.skyPosition(18 * 60 - 1)
        assertTrue(dusk.day)
        assertEquals(1f, dusk.fraction, 0.01f)
    }

    @Test
    fun theMoonTakesTheSamePathAtNight() {
        assertEquals(StartGreeting.SkyPosition(0f, day = false), StartGreeting.skyPosition(18 * 60))
        assertEquals(StartGreeting.SkyPosition(0.5f, day = false), StartGreeting.skyPosition(0))
        val dawn = StartGreeting.skyPosition(6 * 60 - 1)
        assertFalse(dawn.day)
        assertEquals(1f, dawn.fraction, 0.01f)
    }

    @Test
    fun skyPositionWrapsMinutesOutsideTheDay() {
        assertEquals(StartGreeting.skyPosition(12 * 60), StartGreeting.skyPosition(12 * 60 + 24 * 60))
        assertEquals(StartGreeting.skyPosition(23 * 60), StartGreeting.skyPosition(-60))
        for (m in -1440..2880 step 7) {
            val p = StartGreeting.skyPosition(m)
            assertTrue("minute $m -> ${p.fraction}", p.fraction in 0f..1f)
        }
    }
}
