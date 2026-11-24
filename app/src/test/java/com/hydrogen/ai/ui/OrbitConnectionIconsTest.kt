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
    fun connectionSwitcherKeepsActionsAndContentDescriptions() {
        val sheet = source("ConnectionSwitcherSheet.kt")
        assertTrue(sheet.contains("onSwitch(connection)"))
        assertTrue(sheet.contains("onRemove(connection)"))
        assertTrue(sheet.contains("onClick = onRename"))
        assertTrue(sheet.contains("onClick = onRemove"))
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
