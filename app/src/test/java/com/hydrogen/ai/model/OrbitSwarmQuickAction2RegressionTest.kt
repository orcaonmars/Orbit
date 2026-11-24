package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrbitSwarmQuickAction2RegressionTest {

    @Test
    fun `fromIntentAction trims whitespace for SYSTEM_ASSIST`() {
        val result = QuickAction.fromIntentAction("  android.intent.action.ASSIST  ")
        assertEquals(QuickAction.NEW_CHAT, result)
    }

    @Test
    fun `fromIntentAction trims whitespace for ASK_PHOTO`() {
        val result = QuickAction.fromIntentAction("  com.hydrogen.ai.action.ASK_PHOTO  ")
        assertEquals(QuickAction.ASK_PHOTO, result)
    }

    @Test
    fun `fromIntentAction trims whitespace for VOICE`() {
        val result = QuickAction.fromIntentAction("  com.hydrogen.ai.action.VOICE  ")
        assertEquals(QuickAction.VOICE, result)
    }

    @Test
    fun `fromIntentAction returns null for blank after trim`() {
        val result = QuickAction.fromIntentAction("   ")
        assertNull(result)
    }

    @Test
    fun `fromIntentAction returns null for unknown action`() {
        val result = QuickAction.fromIntentAction("com.unknown.ACTION")
        assertNull(result)
    }
}