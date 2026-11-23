package com.hydrogen.ai.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import com.hydrogen.ai.model.AccentId
import com.hydrogen.ai.model.ContrastPolicy
import com.hydrogen.ai.ui.CodePalette
import com.hydrogen.ai.ui.NightCodePalette
import com.hydrogen.ai.ui.PaperCodePalette
import com.hydrogen.ai.ui.codePaletteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every text/background pair the app draws must meet WCAG AA (4.5:1) in Night, Paper and Midnight with each of
 * the five accents (docs/ORBIT_DESIGN.md). Covers both the Material roles that stock components read and the
 * Orbit tokens that the chat screen draws with directly.
 */
class ThemeContrastTest {

    private data class Theme(val name: String, val dark: Boolean, val pureBlack: Boolean)

    private val themes = listOf(
        Theme("Night", dark = true, pureBlack = false),
        Theme("Paper", dark = false, pureBlack = false),
        Theme("Midnight", dark = true, pureBlack = true)
    )

    /** (label, palette) for every theme and accent the Appearance sheet can produce. */
    private val everyPalette: List<Pair<String, OrbitPalette>> = themes.flatMap { theme ->
        AccentId.values().map { id ->
            "${theme.name} ${id.label}" to OrbitPalettes.of(theme.dark, theme.pureBlack, OrbitAccents.of(id))
        }
    }

    private fun materialPairs(s: ColorScheme): List<Triple<String, Color, Color>> = listOf(
        Triple("onBackground/background", s.onBackground, s.background),
        Triple("onSurface/surface", s.onSurface, s.surface),
        Triple("onSurface/surfaceContainerLow (sheets)", s.onSurface, s.surfaceContainerLow),
        Triple("onSurface/surfaceContainerHigh", s.onSurface, s.surfaceContainerHigh),
        Triple("onSurface/surfaceVariant", s.onSurface, s.surfaceVariant),
        Triple("onSurfaceVariant/surface", s.onSurfaceVariant, s.surface),
        Triple("onSurfaceVariant/surfaceVariant", s.onSurfaceVariant, s.surfaceVariant),
        Triple("onSurfaceVariant/surfaceContainerLow", s.onSurfaceVariant, s.surfaceContainerLow),
        Triple("onSurfaceVariant/surfaceContainerHigh", s.onSurfaceVariant, s.surfaceContainerHigh),
        Triple("onSurfaceVariant/surfaceContainerHighest", s.onSurfaceVariant, s.surfaceContainerHighest),
        Triple("onPrimary/primary", s.onPrimary, s.primary),
        Triple("onSecondary/secondary", s.onSecondary, s.secondary),
        Triple("onTertiary/tertiary", s.onTertiary, s.tertiary),
        Triple("onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer),
        Triple("onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer),
        Triple("onTertiaryContainer/tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer),
        Triple("onError/error", s.onError, s.error),
        Triple("onErrorContainer/errorContainer", s.onErrorContainer, s.errorContainer),
        // TextButtons, links and switches read primary/tertiary as text on the page and on sheets.
        Triple("primary/background", s.primary, s.background),
        Triple("primary/surfaceContainerLow", s.primary, s.surfaceContainerLow),
        Triple("primary/surfaceContainerHigh", s.primary, s.surfaceContainerHigh),
        Triple("tertiary/surfaceVariant", s.tertiary, s.surfaceVariant),
        Triple("error/background", s.error, s.background),
        Triple("error/surfaceContainerLow", s.error, s.surfaceContainerLow)
    )

    private fun tokenPairs(p: OrbitPalette): List<Triple<String, Color, Color>> {
        val pairs = mutableListOf<Triple<String, Color, Color>>()
        val text = listOf("bg" to p.bg, "sidebar" to p.sidebar, "surface" to p.surface, "surface2" to p.surface2,
            "raised" to p.raised, "bubble" to p.bubble)
        text.forEach { (name, bg) ->
            pairs += Triple("text/$name", p.text, bg)
            pairs += Triple("muted/$name", p.muted, bg)
        }
        listOf("bg" to p.bg, "sidebar" to p.sidebar, "surface" to p.surface, "raised" to p.raised, "code" to p.code)
            .forEach { (name, bg) -> pairs += Triple("faint/$name", p.faint, bg) }
        // accentSoft is translucent; it is always drawn on the page background.
        val soft = p.accentSoft.compositeOver(p.bg)
        listOf("bg" to p.bg, "sidebar" to p.sidebar, "surface" to p.surface, "surface2" to p.surface2,
            "raised" to p.raised, "code" to p.code, "accentSoft" to soft)
            .forEach { (name, bg) -> pairs += Triple("accent/$name", p.accent, bg) }
        pairs += Triple("text/code", p.text, p.code)
        pairs += Triple("muted/code", p.muted, p.code)
        pairs += Triple("text/accentSoft (selected chips)", p.text, soft)
        pairs += Triple("onAccent/accentFill (send button, swatches)", p.onAccent, p.accentFill)
        pairs += Triple("negative/bg", p.negative, p.bg)
        pairs += Triple("negative/surface", p.negative, p.surface)
        pairs += Triple("positive/bg", p.positive, p.bg)
        return pairs
    }

    private fun codePairs(code: CodePalette, p: OrbitPalette) = listOf(
        Triple("code keyword", code.keyword, p.code),
        Triple("code string", code.string, p.code),
        Triple("code comment", code.comment, p.code),
        Triple("code number", code.number, p.code)
    )

    private fun failures(theme: String, pairs: List<Triple<String, Color, Color>>): List<String> =
        pairs.mapNotNull { (name, fg, bg) ->
            val ratio = ContrastPolicy.contrastRatio(fg.toArgb(), bg.toArgb())
            if (ratio >= ContrastPolicy.AA_NORMAL) null else "$theme $name = ${"%.2f".format(ratio)}:1"
        }

    @Test
    fun materialRolesMeetWcagAaForEveryThemeAndAccent() {
        val bad = everyPalette.flatMap { (label, palette) -> failures(label, materialPairs(orbitColorScheme(palette))) }
        assertTrue("Below 4.5:1 -> $bad", bad.isEmpty())
    }

    @Test
    fun orbitTokensMeetWcagAaForEveryThemeAndAccent() {
        val bad = everyPalette.flatMap { (label, palette) -> failures(label, tokenPairs(palette)) }
        assertTrue("Below 4.5:1 -> $bad", bad.isEmpty())
    }

    @Test
    fun codeColorsMeetWcagAaOnTheCodeBackground() {
        val bad = everyPalette.flatMap { (label, palette) ->
            failures(label, codePairs(codePaletteFor(palette.dark), palette))
        }
        assertTrue("Below 4.5:1 -> $bad", bad.isEmpty())
    }

    @Test
    fun codePaletteFollowsTheThemeSide() {
        assertEquals(NightCodePalette, codePaletteFor(dark = true))
        assertEquals(PaperCodePalette, codePaletteFor(dark = false))
        // Night tokens are light on a dark block and Paper tokens dark on a light one.
        assertTrue(ContrastPolicy.relativeLuminance(OrbitPalettes.Night.code.toArgb()) < 0.5)
        assertTrue(ContrastPolicy.relativeLuminance(OrbitPalettes.Paper.code.toArgb()) >= 0.5)
    }

    @Test
    fun midnightIsTrueBlackAndOnlyChangesTheDarkSurfaces() {
        val midnight = OrbitPalettes.of(dark = true, pureBlack = true)
        assertEquals(Color.Black, midnight.bg)
        assertEquals(Color.Black, orbitColorScheme(midnight).background)
        // Pure black means nothing in the light theme.
        assertEquals(OrbitPalettes.of(dark = false), OrbitPalettes.of(dark = false, pureBlack = true))
        assertEquals(OrbitPalettes.Night.text, midnight.text)
    }

    @Test
    fun everyAccentIdHasItsOwnAccentAndSunIsTheDefault() {
        assertEquals(OrbitAccents.all, AccentId.values().map(OrbitAccents::of))
        assertEquals(OrbitAccents.all.size, OrbitAccents.all.map { it.nightFill }.toSet().size)
        assertEquals(OrbitAccents.Sun, OrbitAccents.of(com.hydrogen.ai.model.AppearancePrefs().accent))
    }

    @Test
    fun materialPrimaryIsTheTextSafeAccentNotTheFill() {
        // Paper's fills are too light for text, so stock TextButtons and links must read `accent`, not `accentFill`.
        val paper = OrbitPalettes.of(dark = false, accent = OrbitAccents.Sun)
        val scheme = orbitColorScheme(paper)
        assertEquals(paper.accent, scheme.primary)
        assertEquals(paper.accent, scheme.tertiary)
        assertEquals(paper.bg, scheme.background)
        assertEquals(paper.text, scheme.onSurface)
    }
}
