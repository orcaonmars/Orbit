package com.hydrogen.ai.ui

import com.hydrogen.ai.model.AccentId
import com.hydrogen.ai.model.ThemeMode
import com.hydrogen.ai.ui.theme.OrbitAccents
import com.hydrogen.ai.ui.theme.OrbitPalettes
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behaviour and source contract for the M8 appearance panel. */
class AppearanceSheetTest {
    private val source = String(
        Files.readAllBytes(Paths.get("src/main/java/com/hydrogen/ai/ui/AppearanceSheet.kt")),
        Charsets.UTF_8
    )

    @Test
    fun everyExistingSettingStillWritesThroughTheController() {
        assertTrue(source.contains("fun AppearanceSheet(controller: AppearanceController, onDismiss: () -> Unit)"))
        assertTrue(source.contains("controller.onChange(prefs.copy(themeMode = mode))"))
        assertTrue(source.contains("controller.onChange(prefs.copy(accent = id))"))
        assertTrue(source.contains("controller.onChange(prefs.copy(pureBlack = it))"))
        assertTrue(source.contains("prefs.themeMode == mode"))
        assertTrue(source.contains("id == prefs.accent"))
        assertTrue(source.contains("Switch(checked = prefs.pureBlack, onCheckedChange = null)"))
    }

    @Test
    fun themeIsAVisualRadioGroupAndSectionsAreHeadings() {
        assertTrue(source.contains("OrbitSectionHeader(\"Theme\")"))
        assertTrue(source.contains("OrbitSectionHeader(\"Accent\")"))
        assertTrue(source.contains("OrbitSectionHeader(\"Display\")"))
        assertTrue(source.contains("Modifier.semantics { heading() }"))
        assertTrue(source.contains("selectableGroup()"))
        assertTrue(source.contains("role = Role.RadioButton"))
        assertTrue(source.contains("role = Role.Switch"))
        assertTrue(source.contains("ThemeChoiceCard("))
        assertFalse(source.contains("SingleChoiceSegmentedButtonRow"))
        assertFalse(source.contains("SegmentedButton"))
    }

    @Test
    fun panelUsesOrbitThemeTokensAndGroupedRows() {
        assertTrue(source.contains("OrbitTheme.colors"))
        assertTrue(source.contains("OrbitTheme.space"))
        assertTrue(source.contains("OrbitTheme.shapes"))
        assertTrue(source.contains("Surface("))
        assertTrue(source.contains("BorderStroke(1.dp, OrbitTheme.colors.line)"))
        assertFalse(source.contains("Color(0x"))
        assertFalse(source.contains("Icons.Default"))
    }

    @Test
    fun systemFollowsThePhoneAndLightIgnoresPureBlack() {
        assertEquals(
            OrbitPalettes.Paper,
            themePaletteFor(ThemeMode.SYSTEM, systemDark = false, pureBlack = false, accent = AccentId.SUN)
        )
        assertEquals(
            OrbitPalettes.Night,
            themePaletteFor(ThemeMode.SYSTEM, systemDark = true, pureBlack = false, accent = AccentId.SUN)
        )
        assertEquals(
            OrbitPalettes.Paper,
            themePaletteFor(ThemeMode.LIGHT, systemDark = true, pureBlack = true, accent = AccentId.SUN)
        )
    }

    @Test
    fun darkPureBlackResolvesToMidnightAndKeepsTheChosenAccent() {
        assertEquals(
            OrbitPalettes.Midnight,
            themePaletteFor(ThemeMode.DARK, systemDark = false, pureBlack = true, accent = AccentId.SUN)
        )
        assertEquals(
            OrbitAccents.Tide.nightFill,
            themePaletteFor(ThemeMode.DARK, systemDark = false, pureBlack = false, accent = AccentId.TIDE).accentFill
        )
        assertEquals(
            OrbitAccents.Tide.paperFill,
            themePaletteFor(ThemeMode.LIGHT, systemDark = true, pureBlack = false, accent = AccentId.TIDE).accentFill
        )
    }

    @Test
    fun chooserCopyIsPresentAndPreviewStateIsTruthful() {
        ThemeMode.values().forEach { assertTrue(themeDescription(it).isNotBlank()) }
        ThemeMode.values().forEach { assertFalse(themeDescription(it).contains("!")) }

        assertEquals("System, dark", previewStateLabel(ThemeMode.SYSTEM, systemDark = true, pureBlack = false))
        assertEquals("System, light", previewStateLabel(ThemeMode.SYSTEM, systemDark = false, pureBlack = true))
        assertEquals("Light", previewStateLabel(ThemeMode.LIGHT, systemDark = true, pureBlack = true))
        assertEquals("Dark", previewStateLabel(ThemeMode.DARK, systemDark = false, pureBlack = false))
        assertEquals("Dark, pure black", previewStateLabel(ThemeMode.DARK, systemDark = false, pureBlack = true))
    }
}
