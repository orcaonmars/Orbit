package com.hydrogen.ai.ui

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-contract checks complementing Kotlin compilation and existing JVM tests. */
class OrbitListRowTest {
    private val source = String(
        Files.readAllBytes(Paths.get("src/main/java/com/hydrogen/ai/ui/components/OrbitListRow.kt")),
        Charsets.UTF_8
    )

    @Test fun rowHasClickableSemanticsAndMinimumTargetSize() {
        assertTrue(source.contains("Surface("))
        assertTrue(source.contains("onClick = onClick"))
        assertTrue(source.contains("enabled = enabled"))
        assertTrue(source.contains("defaultMinSize(minHeight = 56.dp)"))
        assertTrue(source.contains("contentDescription = null"))
    }

    @Test fun themeAndOptionalContentAreUsedConsistently() {
        assertTrue(source.contains("OrbitTheme.colors"))
        assertTrue(source.contains("if (selected) palette.surface2 else palette.surface"))
        assertTrue(source.contains("if (subtitle != null)"))
        assertTrue(source.contains("if (trailingText != null)"))
        assertFalse(source.contains("Color(0x"))
        assertFalse(source.contains("Icons.Default"))
    }

    @Test fun listRowPreviewsIncludeLightNightAndLargeText() {
        assertTrue(source.contains("OrbitPalettes.Paper"))
        assertTrue(source.contains("OrbitPalettes.Night"))
        assertTrue(source.contains("fontScale = 1.5f"))
    }
}
