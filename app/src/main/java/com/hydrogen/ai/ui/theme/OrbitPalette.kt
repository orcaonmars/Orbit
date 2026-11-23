package com.hydrogen.ai.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.hydrogen.ai.model.AccentId

/**
 * Orbit's color tokens (docs/ORBIT_DESIGN.md, section 3). Warm neutrals, one accent. UI code reads them through
 * [OrbitTheme.colors]; the Material color scheme is derived from the same tokens so stock components match.
 */
@Immutable
data class OrbitPalette(
    val dark: Boolean,
    /** Window and chat background. */
    val bg: Color,
    /** Drawer. */
    val sidebar: Color,
    /** Cards, menus, dialogs. */
    val surface: Color,
    /** Pressed and selected rows, the + button, quiet circles. */
    val surface2: Color,
    /** The composer. */
    val raised: Color,
    /** Your message bubble. */
    val bubble: Color,
    /** Code block body. */
    val code: Color,
    /** Borders, dividers, the horizon arc, the voice ring at rest. */
    val line: Color,
    val text: Color,
    /** Secondary text and icons. */
    val muted: Color,
    /** Hints, timestamps, placeholders, the footer. */
    val faint: Color,
    /** Links, focus, the orbit indicator, the waveform. */
    val accent: Color,
    /** The sun button and primary button fill. */
    val accentFill: Color,
    /** Icons and text on [accentFill]. */
    val onAccent: Color,
    /** Selected chips, the active mode pill. */
    val accentSoft: Color,
    val positive: Color,
    val negative: Color,
    val scrim: Color
)

/** One accent choice: the accent, its fill and the color drawn on it, for Night and for Paper. */
@Immutable
data class OrbitAccent(
    val name: String,
    val nightAccent: Color,
    val nightFill: Color,
    val nightOn: Color,
    val paperAccent: Color,
    val paperFill: Color,
    val paperOn: Color
)

object OrbitAccents {
    // Paper Sun text accent is #9A520A (not the first draft's #A65A0A) so links also pass 4.5:1 on surface-2, and
    // its on-accent is dark like Night's: white on #C46F12 was only 3.73:1, #1D1405 is 4.88:1.
    val Sun = OrbitAccent("Sun", Color(0xFFF2B14C), Color(0xFFF2B14C), Color(0xFF1D1405), Color(0xFF9A520A), Color(0xFFC46F12), Color(0xFF1D1405))
    val Tide = OrbitAccent("Tide", Color(0xFF5FD0BF), Color(0xFF5FD0BF), Color(0xFF04201C), Color(0xFF0F6B63), Color(0xFF0F766E), Color.White)
    val Nebula = OrbitAccent("Nebula", Color(0xFFB9A2FF), Color(0xFFB9A2FF), Color(0xFF1A1033), Color(0xFF6142C2), Color(0xFF6D4ACF), Color.White)
    val Ember = OrbitAccent("Ember", Color(0xFFFF8F70), Color(0xFFFF8F70), Color(0xFF2A0C04), Color(0xFFA8401F), Color(0xFFB4472A), Color.White)
    val Glacier = OrbitAccent("Glacier", Color(0xFF8DBDFF), Color(0xFF8DBDFF), Color(0xFF06182F), Color(0xFF2457B5), Color(0xFF2F64C9), Color.White)

    val all: List<OrbitAccent> = listOf(Sun, Tide, Nebula, Ember, Glacier)

    fun of(id: AccentId): OrbitAccent = when (id) {
        AccentId.SUN -> Sun
        AccentId.TIDE -> Tide
        AccentId.NEBULA -> Nebula
        AccentId.EMBER -> Ember
        AccentId.GLACIER -> Glacier
    }
}

object OrbitPalettes {
    val Night = OrbitPalette(
        dark = true,
        bg = Color(0xFF141312),
        sidebar = Color(0xFF1A1917),
        surface = Color(0xFF1F1E1B),
        surface2 = Color(0xFF282622),
        raised = Color(0xFF22201D),
        bubble = Color(0xFF2B2823),
        code = Color(0xFF0F0E0D),
        line = Color(0xFF34312C),
        text = Color(0xFFF1ECE4),
        muted = Color(0xFFB3AA9C),
        faint = Color(0xFF8E8678),
        accent = Color(0xFFF2B14C),
        accentFill = Color(0xFFF2B14C),
        onAccent = Color(0xFF1D1405),
        accentSoft = Color(0xFFF2B14C).copy(alpha = 0.14f),
        positive = Color(0xFF8FD19E),
        negative = Color(0xFFFF9F8F),
        scrim = Color(0xFF080706).copy(alpha = 0.62f)
    )

    val Paper = OrbitPalette(
        dark = false,
        bg = Color(0xFFF8F5EF),
        sidebar = Color(0xFFF0ECE4),
        surface = Color(0xFFFFFDF9),
        surface2 = Color(0xFFEFEAE1),
        raised = Color(0xFFFFFDF9),
        bubble = Color(0xFFEFE7DA),
        code = Color(0xFFF3EEE6),
        line = Color(0xFFE0D9CC),
        text = Color(0xFF1E1B16),
        muted = Color(0xFF5E574B),
        faint = Color(0xFF6F675A),
        accent = Color(0xFF9A520A),
        accentFill = Color(0xFFC46F12),
        onAccent = Color(0xFF1D1405),
        accentSoft = Color(0xFFC46F12).copy(alpha = 0.12f),
        positive = Color(0xFF2F6B3A),
        negative = Color(0xFFB3261E),
        scrim = Color(0xFF1E1B16).copy(alpha = 0.38f)
    )

    /** Night on true black, for OLED screens. */
    val Midnight = Night.copy(
        bg = Color.Black,
        sidebar = Color(0xFF0A0A09),
        surface = Color(0xFF121110),
        surface2 = Color(0xFF1B1A18),
        raised = Color(0xFF161513),
        bubble = Color(0xFF1F1D1A),
        code = Color(0xFF0A0A09),
        line = Color(0xFF262420)
    )

    fun of(dark: Boolean, pureBlack: Boolean = false, accent: OrbitAccent = OrbitAccents.Sun): OrbitPalette {
        val base = when {
            !dark -> Paper
            pureBlack -> Midnight
            else -> Night
        }
        return if (dark) {
            base.copy(
                accent = accent.nightAccent,
                accentFill = accent.nightFill,
                onAccent = accent.nightOn,
                accentSoft = accent.nightFill.copy(alpha = 0.14f)
            )
        } else {
            base.copy(
                accent = accent.paperAccent,
                accentFill = accent.paperFill,
                onAccent = accent.paperOn,
                accentSoft = accent.paperFill.copy(alpha = 0.12f)
            )
        }
    }
}

val LocalOrbitPalette = staticCompositionLocalOf { OrbitPalettes.Night }

object OrbitTheme {
    val colors: OrbitPalette @Composable @ReadOnlyComposable get() = LocalOrbitPalette.current
}
