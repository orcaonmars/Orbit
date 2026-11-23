package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QuickActionTest {
    @Test
    fun mapsEachKnownAction() {
        QuickAction.values().forEach { action ->
            assertEquals(action, QuickAction.fromIntentAction(action.intentAction))
        }
    }

    @Test
    fun ignoresNormalLaunchesSharesAndUnknownActions() {
        assertNull(QuickAction.fromIntentAction(null))
        assertNull(QuickAction.fromIntentAction(""))
        assertNull(QuickAction.fromIntentAction("android.intent.action.MAIN"))
        assertNull(QuickAction.fromIntentAction("android.intent.action.SEND"))
        assertNull(QuickAction.fromIntentAction("com.hydrogen.ai.action.new_chat"))
    }

    @Test
    fun systemAssistGestureOpensANewChat() {
        assertEquals("android.intent.action.ASSIST", QuickAction.SYSTEM_ASSIST)
        assertEquals(QuickAction.NEW_CHAT, QuickAction.fromIntentAction("android.intent.action.ASSIST"))
        assertNull(QuickAction.fromIntentAction("android.intent.action.VOICE_COMMAND"))
    }

    @Test
    fun actionsMatchTheShortcutsXml() {
        // res/xml/shortcuts.xml hard-codes these strings; renaming one silently breaks a shortcut.
        assertEquals("com.hydrogen.ai.action.NEW_CHAT", QuickAction.NEW_CHAT.intentAction)
        assertEquals("com.hydrogen.ai.action.ASK_PHOTO", QuickAction.ASK_PHOTO.intentAction)
        assertEquals("com.hydrogen.ai.action.VOICE", QuickAction.VOICE.intentAction)
    }

    @Test
    fun shortcutsTargetTheRightAppIdForEachBuild() {
        // Unit tests run from the app module directory.
        val main = File("src/main/res/xml/shortcuts.xml").readText()
        val debug = File("src/debug/res/xml/shortcuts.xml").readText()
        val gradle = File("build.gradle.kts").readText()
        val packages = Regex("android:targetPackage=\"([^\"]+)\"")
        assertEquals(List(3) { RELEASE_ID }, packages.findAll(main).map { it.groupValues[1] }.toList())
        assertEquals(List(3) { DEBUG_ID }, packages.findAll(debug).map { it.groupValues[1] }.toList())
        QuickAction.values().forEach { assertTrue(main.contains("android:action=\"${it.intentAction}\"")) }
        // Apart from the package, the debug copy must be identical so the two never drift apart.
        assertEquals(main, debug.replace("\"$DEBUG_ID\"", "\"$RELEASE_ID\""))
        assertTrue(gradle.contains("applicationId = \"$RELEASE_ID\""))
        assertTrue(gradle.contains("applicationId.set(\"$DEBUG_ID\")"))
    }

    private companion object {
        const val RELEASE_ID = "io.github.orcaonmars.orbit"
        const val DEBUG_ID = "com.hydrogen.ai"
    }
}
