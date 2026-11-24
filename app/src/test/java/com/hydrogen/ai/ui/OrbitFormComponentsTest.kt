package com.hydrogen.ai.ui

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static contract guard for the reusable OrbitTextField, in addition to the Kotlin compile gate. */
class OrbitFormComponentsTest {
    private val component = Paths.get("src/main/java/com/hydrogen/ai/ui/components/OrbitFormComponents.kt")
    private fun source(path: java.nio.file.Path): String = String(Files.readAllBytes(path), Charsets.UTF_8)

    @Test fun textFieldIsControlledWithRealEditCallback() {
        val source = source(component)
        assertTrue(source.contains("value: String"))
        assertTrue(source.contains("onValueChange: (String) -> Unit"))
        assertTrue(source.contains("onValueChange = onValueChange"))
        assertTrue(source.contains("label = { Text(label) }"))
    }

    @Test fun textFieldSupportsDisabledSingleLineAndThemedBorders() {
        val source = source(component)
        assertTrue(source.contains("enabled: Boolean = true"))
        assertTrue(source.contains("singleLine: Boolean = true"))
        assertTrue(source.contains("enabled = enabled"))
        assertTrue(source.contains("singleLine = singleLine"))
        assertTrue(source.contains("focusedBorderColor = colors.accent"))
        assertTrue(source.contains("unfocusedBorderColor = colors.line"))
    }

    @Test fun textFieldPreviewsCoverPaperNightAndLargeFont() {
        val source = source(component)
        assertTrue(source.contains("PaperFormFieldPreview"))
        assertTrue(source.contains("NightFormFieldPreview"))
        assertTrue(source.contains("LargeTextFormFieldPreview"))
        assertTrue(source.contains("fontScale = 1.5f"))
        assertFalse(source.contains("Icons.Default"))
    }
}
