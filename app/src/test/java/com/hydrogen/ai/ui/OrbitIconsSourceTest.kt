package com.hydrogen.ai.ui

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Focused source contract for the first M3 icon migration; remaining screens are legacy. */
class OrbitIconsSourceTest {
    private fun source(name: String): String =
        String(Files.readAllBytes(Paths.get("src/main/java/com/hydrogen/ai/ui/$name")), Charsets.UTF_8)

    @Test
    fun sharedIconsProvideOriginalRoundedVectors() {
        val icons = source("OrbitIcons.kt")
        assertTrue(icons.contains("object OrbitIcons"))
        assertTrue(icons.contains("val ArrowUpward: ImageVector"))
        assertTrue(icons.contains("val Check: ImageVector"))
        assertTrue(icons.contains("strokeLineWidth = 1.75f"))
        assertTrue(icons.contains("strokeLineCap = StrokeCap.Round"))
        assertTrue(icons.contains("strokeLineJoin = StrokeJoin.Round"))
        assertFalse(icons.contains("Icons.Default"))
    }

    @Test
    fun appearanceSheetUsesSharedIconsWithoutLegacyImports() {
        val appearance = source("AppearanceSheet.kt")
        assertTrue(appearance.contains("Icon(OrbitIcons.ArrowUpward"))
        assertTrue(appearance.contains("OrbitIcons.Check"))
        assertFalse(appearance.contains("Icons.Rounded."))
        assertFalse(appearance.contains("import androidx.compose.material.icons."))
    }
}
