package com.hydrogen.ai.ui

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static guard for the next reusable UI components, in addition to the actual Kotlin compile gate. */
class OrbitInputComponentsTest {
    private val component = Paths.get("src/main/java/com/hydrogen/ai/ui/components/OrbitInputComponents.kt")
    private val icons = Paths.get("src/main/java/com/hydrogen/ai/ui/OrbitIcons.kt")
    private fun source(path: java.nio.file.Path): String = String(Files.readAllBytes(path), Charsets.UTF_8)

    @Test fun searchFieldIsControlledAndHasAWorkingClearAction() {
        val source = source(component)
        assertTrue(source.contains("value: String"))
        assertTrue(source.contains("onValueChange: (String) -> Unit"))
        assertTrue(source.contains("if (value.isNotEmpty())"))
        assertTrue(source.contains("onClick = { onValueChange(\"\") }"))
        assertTrue(source.contains("singleLine = true"))
        assertTrue(source.contains("enabled = enabled"))
    }

    @Test fun iconButtonsHaveFortyEightDpTargetAndSemanticDescription() {
        val source = source(component)
        assertTrue(source.contains("modifier = modifier.size(48.dp)"))
        assertTrue(source.contains("contentDescription = contentDescription"))
        assertTrue(source.contains("modifier = Modifier.size(24.dp)"))
        assertFalse(source.contains("Icons.Default"))
        assertFalse(source.contains("Icons.Filled"))
    }

    @Test fun searchAndCloseIconsAreFromSharedIconFamily() {
        val source = source(icons)
        assertTrue(source.contains("val Search: ImageVector"))
        assertTrue(source.contains("val Close: ImageVector"))
        assertTrue(source.contains("name = \"OrbitSearch\""))
        assertTrue(source.contains("name = \"OrbitClose\""))
        assertTrue(source(component).contains("OrbitIcons.Search"))
        assertTrue(source(component).contains("OrbitIcons.Close"))
    }

    @Test fun searchFieldPreviewsCoverPaperNightAndLargeFont() {
        val source = source(component)
        assertTrue(source.contains("PaperSearchPreview"))
        assertTrue(source.contains("NightSearchPreview"))
        assertTrue(source.contains("LargeTextSearchPreview"))
        assertTrue(source.contains("fontScale = 1.5f"))
    }
}
