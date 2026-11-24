package com.hydrogen.ai.ui

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression contract for the small M3_2 Personal sheet icon migration. */
class OrbitPersonalIconMigrationTest {
    @Test fun selectedStyleChipUsesSharedOrbitCheck() {
        val path = Paths.get("src/main/java/com/hydrogen/ai/ui/PersonalSheet.kt")
        val source = String(Files.readAllBytes(path), Charsets.UTF_8)
        assertTrue(source.contains("OrbitIcons.Check"))
        assertFalse(source.contains("Icons.Rounded.Check"))
        assertFalse(source.contains("import androidx.compose.material.icons.Icons"))
        assertFalse(source.contains("import androidx.compose.material.icons.rounded.Check"))
        assertTrue(source.contains("modifier = Modifier.size(FilterChipDefaults.IconSize)"))
        assertTrue(source.contains("selectedLeadingIconColor = colors.primary"))
    }
}