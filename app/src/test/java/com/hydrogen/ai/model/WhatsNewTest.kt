package com.hydrogen.ai.model

import com.hydrogen.ai.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {

    @Test fun aFreshInstallNeverSeesTheSheet() {
        assertFalse(WhatsNew.shouldShow(lastSeenVersion = 0, currentVersion = 2, hadSavedData = false))
    }

    @Test fun updatingFromOrbit01WithSavedDataShowsIt() {
        // 0.1 recorded no version, so saved data is how an update is told apart from a fresh install.
        assertTrue(WhatsNew.shouldShow(lastSeenVersion = 0, currentVersion = 2, hadSavedData = true))
    }

    @Test fun updatingFromARecordedOlderVersionShowsItEvenAfterDeleteAll() {
        assertTrue(WhatsNew.shouldShow(lastSeenVersion = 1, currentVersion = 2, hadSavedData = false))
    }

    @Test fun theSameVersionOrAnOlderBuildNeverShowsItAgain() {
        assertFalse(WhatsNew.shouldShow(lastSeenVersion = 2, currentVersion = 2, hadSavedData = true))
        assertFalse(WhatsNew.shouldShow(lastSeenVersion = 3, currentVersion = 2, hadSavedData = true))
    }

    @Test fun theTitleNamesThisVersion() {
        // Fails when versionName goes up without new notes, so an update never shows the last release's list.
        val majorMinor = BuildConfig.VERSION_NAME.substringBefore('-').split('.').take(2).joinToString(".")
        assertTrue(WhatsNew.TITLE, WhatsNew.TITLE.endsWith("Orbit $majorMinor"))
    }

    @Test fun copyFollowsTheHouseRules() {
        val all = listOf(WhatsNew.TITLE, WhatsNew.SUBTITLE, WhatsNew.DONE) +
            WhatsNew.ITEMS.flatMap { listOf(it.title, it.body) }
        all.forEach { line ->
            assertFalse(line, line.contains('!'))
            assertFalse(line, line.contains('—'))
            assertFalse(line, line.contains('–'))
            assertFalse(line, line.contains("ChatGPT", ignoreCase = true))
            assertFalse(line, line.contains("Claude", ignoreCase = true))
            assertEquals(line, line.trim(), line)
        }
        assertTrue(WhatsNew.ITEMS.size in 3..10)
        assertEquals(WhatsNew.ITEMS.size, WhatsNew.ITEMS.map { it.title }.toSet().size)
        WhatsNew.ITEMS.forEach { item ->
            assertTrue(item.title, item.title.length <= 24)
            assertTrue(item.body, item.body.length <= 110)
            assertTrue(item.body, item.body.endsWith("."))
        }
    }
}
