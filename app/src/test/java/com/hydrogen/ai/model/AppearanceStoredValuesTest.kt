package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceStoredValuesTest {
    @Test fun wrongAccentTypePreservesThemeAndOledMode() {
        assertEquals(AppearancePrefs(ThemeMode.DARK, AccentId.SUN, true),
            AppearancePrefs.parseStoredValues("DARK", 42, true))
    }

    @Test fun wrongThemeAndOledTypesPreserveLegacyAccent() {
        assertEquals(AppearancePrefs(ThemeMode.SYSTEM, AccentId.TIDE, false),
            AppearancePrefs.parseStoredValues(false, "OCEAN", "true"))
    }

    @Test fun missingValuesUseDefaults() {
        assertEquals(AppearancePrefs(), AppearancePrefs.parseStoredValues(null, null, null))
    }

    @Test fun validValuesAreUnchanged() {
        assertEquals(AppearancePrefs(ThemeMode.LIGHT, AccentId.GLACIER, true),
            AppearancePrefs.parseStoredValues("LIGHT", "GLACIER", true))
    }
}
