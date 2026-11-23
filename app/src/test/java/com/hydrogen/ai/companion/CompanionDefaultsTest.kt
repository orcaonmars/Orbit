package com.hydrogen.ai.companion

import com.hydrogen.ai.model.LocalServerPresets
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CompanionDefaultsTest {
    @After
    fun restoreHost() {
        LocalServerPresets.pcHost = LocalServerPresets.EMULATOR_HOST
    }

    @Test
    fun defaultUrlFollowsTheHostThatReachesThePc() {
        LocalServerPresets.pcHost = LocalServerPresets.EMULATOR_HOST
        assertEquals("http://10.0.2.2:18812", CompanionDefaults.defaultUrl())
        assertEquals(CompanionDefaults.DEFAULT_COMPANION_URL, CompanionDefaults.defaultUrl())

        LocalServerPresets.pcHost = LocalServerPresets.DEVICE_HOST
        assertEquals("http://127.0.0.1:18812", CompanionDefaults.defaultUrl())
        assertEquals("http://127.0.0.1:18812", CompanionConfig().baseUrl)
    }

    @Test
    fun defaultDeviceNameIsNotPersonal() {
        assertFalse(CompanionDefaults.DEFAULT_DEVICE_NAME.contains("Hydrogen"))
    }
}
