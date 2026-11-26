package com.hydrogen.ai.ui

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source guard for the connection switcher icon migration. The sheet is the first mixed-family screen moved to the
 * original Orbit outline set: cloud/computer server labels, key, rename, forget, in-use check and manage connections.
 */
class OrbitConnectionIconsTest {
    private fun source(name: String): String =
        String(Files.readAllBytes(Paths.get("src/main/java/com/hydrogen/ai/ui/$name")), Charsets.UTF_8)

    @Test
    fun sharedIconsAddNetworkKeyEditAndTuneVariants() {
        val icons = source("OrbitIcons.kt")
        val expected = listOf(
            "Cloud" to "OrbitCloud",
            "Computer" to "OrbitComputer",
            "Key" to "OrbitKey",
            "Edit" to "OrbitEdit",
            "Tune" to "OrbitTune"
        )
        expected.forEach { (property, vectorName) ->
            assertTrue("Missing OrbitIcons.$property", icons.contains("val $property: ImageVector"))
            assertTrue("Missing vector name $vectorName", icons.contains("name = \"$vectorName\""))
        }
        assertTrue(icons.contains("strokeLineWidth = 1.75f"))
        assertTrue(icons.contains("strokeLineCap = StrokeCap.Round"))
        assertTrue(icons.contains("strokeLineJoin = StrokeJoin.Round"))
    }

    @Test
    fun connectionSwitcherUsesOnlyOrbitIcons() {
        val sheet = source("ConnectionSwitcherSheet.kt")
        listOf("Check", "Close", "Cloud", "Computer", "Key", "Edit", "Tune").forEach { icon ->
            assertTrue("Missing OrbitIcons.$icon usage", sheet.contains("OrbitIcons.$icon"))
        }
        assertFalse("ConnectionSwitcherSheet must not import Material icons",
            sheet.contains("import androidx.compose.material.icons"))
        assertFalse("ConnectionSwitcherSheet must not reference Material Icons.*",
            Regex("(?<![A-Za-z])Icons\\.").containsMatchIn(sheet))
    }

    @Test
    fun replyActionsAndCodeCopyUseOrbitIcons() {
        val chat = source("ChatScreen.kt")
        listOf("Copy", "Stop", "Speaker", "Refresh", "Share", "Compare", "Branch", "Pin").forEach { icon ->
            assertTrue("Missing OrbitIcons.$icon usage", chat.contains("OrbitIcons.$icon"))
        }
        listOf("Icons.Outlined.ContentCopy", "Icons.Default.MoreVert", "Icons.Filled.PushPin").forEach { old ->
            assertFalse("ChatScreen still uses $old", chat.contains(old))
        }
        assertFalse(source("MarkdownMessage.kt").contains("Icons.Default.ContentCopy"))
    }

    @Test
    fun chatScreenDrawsNoMaterialIcons() {
        val chat = source("ChatScreen.kt")
        val uses = Regex("(?<![A-Za-z.])Icons\\.(Default|Filled|Outlined|Rounded|AutoMirrored)\\.").findAll(chat).count()
        assertTrue("ChatScreen still draws $uses Material icons", uses == 0)
        assertTrue(chat.contains("label = \"composer-action\""))
    }

    @Test
    fun textButtonsAreNotPills() {
        val files = listOf(
            "AskPcCardView.kt", "CompanionPairingSheet.kt", "DiagnosticsSheet.kt", "DocumentCard.kt",
            "PersonalSheet.kt", "PrivacySheet.kt", "ProjectSheet.kt", "RelaySettingsSheet.kt", "WhatsNewSheet.kt"
        )
        val call = Regex("(?<![A-Za-z_.])(Button|OutlinedButton|FilledTonalButton|ElevatedButton)\\(")
        for (name in files) {
            val text = source(name)
            for (m in call.findAll(text)) {
                val head = text.substring(m.range.last + 1, minOf(text.length, m.range.last + 120))
                assertTrue("$name has a pill button at ${m.range.first}", head.contains("OrbitButtonShape"))
            }
        }
    }

    @Test
    fun settingsAndVoiceScreensDrawNoMaterialIcons() {
        val material = Regex("(?<![A-Za-z.])Icons\\.(Default|Filled|Outlined|Rounded|AutoMirrored)\\.")
        for (name in listOf("AboutSheet.kt", "RelaySettingsSheet.kt", "ReplyPlaybackBar.kt", "ProjectSheet.kt", "MemorySheet.kt", "DocumentCard.kt", "AskPcCardView.kt")) {
            assertTrue("$name still draws Material icons", material.findAll(source(name)).count() == 0)
        }
    }

    @Test
    fun connectionSwitcherKeepsActionsAndContentDescriptions() {
        val sheet = source("ConnectionSwitcherSheet.kt")
        assertTrue(sheet.contains("onSwitch(connection)"))
        assertTrue(sheet.contains("onRemove(connection)"))
        // Row actions live in one overflow menu per row (two-level switcher redesign).
        assertTrue(sheet.contains("OrbitIcons.MoreHorizontal"))
        assertTrue(sheet.contains("onClick = { menuOpen = false; onRename() }"))
        assertTrue(sheet.contains("onClick = { menuOpen = false; onRemove() }"))
        assertTrue(sheet.contains("onClick = { menuOpen = false; onPin() }"))
        assertTrue(sheet.contains("OrbitIcons.ArrowBack"))
        assertTrue(sheet.contains("onOpenSettings"))
        assertTrue(sheet.contains("onDismiss()"))
        assertTrue(sheet.contains("contentDescription = \"In use\""))
        assertTrue(sheet.contains("contentDescription = \"Rename \$name\""))
        assertTrue(sheet.contains("contentDescription = \"Forget \$name\""))
        assertTrue(sheet.contains("contentDescription = \"Uses a key\""))
        assertTrue(sheet.contains("contentDescription = if (local) \"On your network\" else \"Remote server\""))
        assertTrue(sheet.contains("role = Role.RadioButton"))
        assertTrue(sheet.contains("role = Role.Button"))
    }
}
