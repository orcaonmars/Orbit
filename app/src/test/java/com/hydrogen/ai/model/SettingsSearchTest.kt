package com.hydrogen.ai.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    @Test fun blankQueryShowsEverything() {
        assertTrue(SettingsSearch.matches("  ", "Appearance", "Theme and text size"))
    }

    @Test fun matchesAcrossSectionTitleAndDescription() {
        assertTrue(SettingsSearch.matches(" PREFERENCES  text ", "Preferences", "Appearance", "Theme and text size"))
        assertTrue(SettingsSearch.matches("api model", "Connections", "Models and API keys"))
        assertFalse(SettingsSearch.matches("api privacy", "Connections", "Models and API keys"))
    }
}
