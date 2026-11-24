package com.hydrogen.ai.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.ui.components.OrbitButtonSize
import com.hydrogen.ai.ui.components.OrbitButtonStyle
import com.hydrogen.ai.ui.components.buttonPaint
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM contracts for the M4 reusable component pilot, not a screenshot test. */
class OrbitCoreComponentsTest {
    private val primary = Color(0xFFDD8822)
    private val onPrimary = Color.Black
    private val surface = Color.Gray
    private val text = Color.White
    private val muted = Color.LightGray
    private val negative = Color.Red

    private fun paint(style: OrbitButtonStyle) =
        buttonPaint(style, primary, onPrimary, surface, text, muted, negative)

    @Test fun allButtonStylesMapToSemanticColors() {
        assertEquals(primary, paint(OrbitButtonStyle.Primary).background)
        assertEquals(onPrimary, paint(OrbitButtonStyle.Primary).foreground)
        assertEquals(surface, paint(OrbitButtonStyle.Secondary).background)
        assertEquals(text, paint(OrbitButtonStyle.Secondary).foreground)
        assertEquals(primary.copy(alpha = 0.14f), paint(OrbitButtonStyle.Tonal).background)
        assertEquals(Color.Transparent, paint(OrbitButtonStyle.Ghost).background)
        assertEquals(muted, paint(OrbitButtonStyle.Ghost).foreground)
        assertEquals(negative, paint(OrbitButtonStyle.Danger).foreground)
    }

    @Test fun buttonSizesMatchDesignContract() {
        assertEquals(40.dp, OrbitButtonSize.M.height)
        assertEquals(48.dp, OrbitButtonSize.L.height)
    }

    @Test fun sourceIncludesLoadingGuardAndThreePreviewModes() {
        val source = String(Files.readAllBytes(Paths.get(
            "src/main/java/com/hydrogen/ai/ui/components/OrbitCoreComponents.kt"
        )), Charsets.UTF_8)
        assertTrue(source.contains("enabled = enabled && !loading"))
        assertTrue(source.contains("leadingIcon: ImageVector? = null"))
        assertTrue(source.contains("fun OrbitDivider("))
        assertTrue(source.contains("fun OrbitEmptyState("))
        assertTrue(source.contains("actionLabel != null && onAction != null"))
        assertTrue(source.contains("PaperControlsPreview"))
        assertTrue(source.contains("NightControlsPreview"))
        assertTrue(source.contains("LargeFontControlsPreview"))
        assertFalse(source.contains("Icons.Default"))
    }
}
