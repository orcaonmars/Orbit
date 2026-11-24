package com.hydrogen.ai.ui

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Focused source regression for the initial Orbit icon migration. */
class OrbitIconsMigrationTest {
    private fun source(file: String): String =
        String(Files.readAllBytes(Paths.get("src/main/java/com/hydrogen/ai/ui/$file")), Charsets.UTF_8)

    @Test fun customIconsDeclareNamedVectorsAndConsistentViewport() {
        val icons = source("OrbitIcons.kt")
        assertTrue(icons.contains("object OrbitIcons"))
        assertTrue(icons.contains("val ArrowUpward: ImageVector"))
        assertTrue(icons.contains("val Check: ImageVector"))
        assertTrue(icons.contains("name = \"OrbitArrowUpward\""))
        assertTrue(icons.contains("name = \"OrbitCheck\""))
        assertTrue(icons.contains("val Search: ImageVector"))
        assertTrue(icons.contains("val Close: ImageVector"))
        val widths = Regex("defaultWidth\\s*=\\s*24\\.dp").findAll(icons).count()
        val heights = Regex("viewportHeight\\s*=\\s*24f").findAll(icons).count()
        assertTrue(widths >= 4)
        assertTrue("Every Orbit icon must share the 24dp square viewport", widths == heights)
    }

    @Test fun migratedAppearanceUsesCustomVectorsAndValidPathSyntax() {
        val appearance = source("AppearanceSheet.kt")
        val icons = source("OrbitIcons.kt")
        assertTrue(appearance.contains("Icon(OrbitIcons.ArrowUpward"))
        assertTrue(appearance.contains("OrbitIcons.Check"))
        assertFalse(appearance.contains("Icons.Rounded.ArrowUpward"))
        assertFalse(appearance.contains("Icons.Rounded.Check"))
        assertFalse("Compose PathBuilder.arcTo must take x/y arguments, not a lambda",
            Regex("arcTo\\([\\s\\S]*?\\)\\s*\\{\\s*x\\s*=").containsMatchIn(icons))
    }
}
