package com.hydrogen.ai.ui

import androidx.compose.ui.graphics.Color
import com.hydrogen.ai.ui.components.dialogConfirmColor
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static and JVM contract guards for the reusable OrbitDialog, in addition to the Kotlin compile gate. */
class OrbitDialogTest {
    private val component = Paths.get("src/main/java/com/hydrogen/ai/ui/components/OrbitDialog.kt")
    private fun source(path: java.nio.file.Path): String = String(Files.readAllBytes(path), Charsets.UTF_8)

    private val accent = Color(0xFF9A520A)
    private val negative = Color(0xFFB3261E)

    @Test fun dialogIsGatedByVisibleAndInvokesRealCallbacks() {
        val src = source(component)
        assertTrue(src.contains("visible: Boolean"))
        assertTrue(src.contains("if (!visible) return"))
        assertTrue(src.contains("onConfirm: () -> Unit"))
        assertTrue(src.contains("onDismiss: () -> Unit"))
        assertTrue(src.contains("TextButton(onClick = onConfirm)"))
        assertTrue(src.contains("TextButton(onClick = onDismiss)"))
        assertTrue(src.contains("onDismissRequest?.invoke()"))
        assertTrue(src.contains("if (dismissLabel != null)"))
    }

    @Test fun destructiveConfirmUsesNegativeColorAndOrdinaryConfirmUsesAccent() {
        assertEquals(negative, dialogConfirmColor(destructive = true, accent = accent, negative = negative))
        assertEquals(accent, dialogConfirmColor(destructive = false, accent = accent, negative = negative))
    }

    @Test fun dialogUsesOrbitThemeColorsAndTokens() {
        val src = source(component)
        assertTrue(src.contains("OrbitTheme.colors"))
        assertTrue(src.contains("OrbitTheme.shapes.xl"))
        assertTrue(src.contains("containerColor = colors.surface"))
        assertTrue(src.contains("textContentColor = colors.muted"))
        assertTrue(src.contains("dialogConfirmColor(destructive, colors.accent, colors.negative)"))
        assertFalse(src.contains("Icons.Default"))
    }

    @Test fun previewsRenderTheRealDialogInPaperNightAndLargeFont() {
        val src = source(component)
        assertTrue(src.contains("@Preview(name = \"Paper dialog\""))
        assertTrue(src.contains("@Preview(name = \"Night dialog\""))
        assertTrue(src.contains("@Preview(name = \"Large font dialog\", fontScale = 1.5f"))
        assertTrue(src.contains("LocalOrbitPalette provides OrbitPalettes.Paper"))
        assertTrue(src.contains("LocalOrbitPalette provides OrbitPalettes.Night"))
        // The preview body must compose OrbitDialog itself, not descriptive placeholder text.
        assertTrue(src.contains("fun DialogPreview(destructive: Boolean)"))
        assertTrue(src.contains("OrbitDialog("))
        assertTrue(src.contains("visible = true"))
        assertTrue(src.contains("destructive = destructive"))
        assertFalse(src.contains("Dialog previews are static"))
    }

    @Test fun destructivePreviewExercisesTheDangerVariant() {
        val src = source(component)
        assertTrue(src.contains("@Preview(name = \"Destructive dialog\""))
        assertTrue(src.contains("DialogPreview(destructive = true)"))
        assertTrue(src.contains("confirmLabel = if (destructive) \"Delete\" else \"Move\""))
    }
}
