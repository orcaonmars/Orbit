package com.hydrogen.ai.ui

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source contract for the reusable, accessible M4 section header pilot. */
class OrbitSectionHeaderTest {
    private val source = String(
        Files.readAllBytes(Paths.get("src/main/java/com/hydrogen/ai/ui/components/OrbitSectionHeader.kt")),
        Charsets.UTF_8
    )

    @Test fun usesSharedThemeAndAccessibleHeading() {
        assertTrue(source.contains("OrbitTheme.colors.muted"))
        assertTrue(source.contains(".semantics { heading() }"))
        assertTrue(source.contains("MaterialTheme.typography.labelMedium"))
        assertFalse(source.contains("Color(0x"))
    }

    @Test fun previewsUseExistingPaperAndNightPalettes() {
        assertTrue(source.contains("LocalOrbitPalette provides OrbitPalettes.Paper"))
        assertTrue(source.contains("LocalOrbitPalette provides OrbitPalettes.Night"))
        assertTrue(source.contains("fontScale = 1.5f"))
        assertFalse(source.contains("OrbitPalette.light()"))
        assertFalse(source.contains("OrbitPalette.dark()"))
    }
}
