package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearancePrefsTest {

    @Test
    fun defaultsFollowThePhoneWithTheSunAccent() {
        val prefs = AppearancePrefs()
        assertEquals(ThemeMode.SYSTEM, prefs.themeMode)
        assertEquals(AccentId.SUN, prefs.accent)
        assertFalse(prefs.pureBlack)
        assertTrue(prefs.isDark(systemDark = true))
        assertFalse(prefs.isDark(systemDark = false))
    }

    @Test
    fun forcedThemesIgnoreThePhone() {
        assertFalse(AppearancePrefs(themeMode = ThemeMode.LIGHT).isDark(systemDark = true))
        assertTrue(AppearancePrefs(themeMode = ThemeMode.DARK).isDark(systemDark = false))
    }

    @Test
    fun parseRoundTripsEveryCombinationByName() {
        for (mode in ThemeMode.values()) for (accent in AccentId.values()) for (black in listOf(true, false)) {
            val prefs = AppearancePrefs(mode, accent, black)
            assertEquals(prefs, AppearancePrefs.parse(mode.name, accent.name, black))
        }
    }

    @Test
    fun unknownOrMissingValuesFallBackToDefaults() {
        assertEquals(AppearancePrefs(), AppearancePrefs.parse(null, null, false))
        assertEquals(AppearancePrefs(), AppearancePrefs.parse("", "", false))
        assertEquals(AppearancePrefs(), AppearancePrefs.parse("dark", "ocean", false)) // stored names are exact
        assertEquals(AppearancePrefs(), AppearancePrefs.parse("NEON", "RAINBOW", false))
        assertEquals(
            AppearancePrefs(themeMode = ThemeMode.DARK, pureBlack = true),
            AppearancePrefs.parse("DARK", "RAINBOW", true)
        )
    }

    @Test
    fun accentsSavedBeforeTheRedesignMapToTheClosestNewOne() {
        val expected = mapOf(
            "HYDROGEN" to AccentId.SUN,
            "OCEAN" to AccentId.TIDE,
            "AURORA" to AccentId.TIDE,
            "SUNSET" to AccentId.EMBER,
            "ORCHID" to AccentId.NEBULA,
            "GRAPHITE" to AccentId.GLACIER
        )
        for ((old, new) in expected) {
            assertEquals(old, new, AccentId.fromStored(old))
            assertEquals(old, AppearancePrefs(ThemeMode.DARK, new, true), AppearancePrefs.parse("DARK", old, true))
        }
        // New names win, and nothing else is guessed.
        AccentId.values().forEach { assertEquals(it, AccentId.fromStored(it.name)) }
        assertEquals(null, AccentId.fromStored("hydrogen"))
        assertEquals(null, AccentId.fromStored(null))
    }
}
