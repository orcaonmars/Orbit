package com.hydrogen.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hydrogen.ai.model.AppearancePrefs

/**
 * The Material color scheme, derived from Orbit's tokens so stock components (sheets, dialogs, menus, switches,
 * text fields) match the screens drawn with [OrbitTheme.colors].
 *
 * - `primary` and `tertiary` are the text-safe `accent`, not `accent-fill`: Material draws TextButton labels,
 *   links, switches and focused labels in them, and the Paper fills are too light for text. The sun button
 *   draws `accent-fill` itself.
 * - `on-` colors on the accent and on errors are `bg`. Every accent is at least 4.5:1 on `bg` (it is used for
 *   links), and contrast is symmetric, so `bg` reads on every accent too.
 * - Containers are `bg` with a little of the fill (14%) or the error color (18%), with `text` on top.
 *
 * ThemeContrastTest checks every pair in Night, Paper and Midnight with every accent.
 */
internal fun orbitColorScheme(p: OrbitPalette): ColorScheme {
    val accentContainer = lerp(p.bg, p.accentFill, 0.14f)
    val base = if (p.dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.accent,
        onPrimary = p.bg,
        primaryContainer = accentContainer,
        onPrimaryContainer = p.text,
        inversePrimary = p.accent,
        secondary = p.muted,
        onSecondary = p.bg,
        secondaryContainer = p.surface2,
        onSecondaryContainer = p.text,
        tertiary = p.accent,
        onTertiary = p.bg,
        tertiaryContainer = accentContainer,
        onTertiaryContainer = p.text,
        background = p.bg,
        onBackground = p.text,
        surface = p.bg,
        onSurface = p.text,
        surfaceVariant = p.surface2,
        onSurfaceVariant = p.muted,
        // Elevated surfaces get a warm neutral lift, never an accent tint.
        surfaceTint = p.line,
        inverseSurface = p.text,
        inverseOnSurface = p.bg,
        error = p.negative,
        onError = p.bg,
        errorContainer = lerp(p.bg, p.negative, 0.18f),
        onErrorContainer = p.text,
        outline = lerp(p.line, p.muted, 0.35f),
        outlineVariant = p.line,
        // Material applies its own alpha to the scrim.
        scrim = p.scrim.copy(alpha = 1f),
        surfaceBright = p.surface2,
        surfaceDim = p.bg,
        surfaceContainerLowest = p.bg,
        surfaceContainerLow = p.sidebar,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface,
        surfaceContainerHighest = p.surface2
    )
}

private val base = Typography()

/**
 * Orbit type on Android (docs/ORBIT_DESIGN.md, section 4), system fonts only. bodyLarge is the reply style
 * (16 sp at 1.65), bodyMedium the UI style (15 sp at 1.4), bodySmall the meta style (12 sp at 1.4).
 */
internal val OrbitTypography = base.copy(
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.sp),
    bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
    labelLarge = base.labelLarge.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    labelMedium = base.labelMedium.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    labelSmall = base.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    titleLarge = base.titleLarge.copy(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = base.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = base.titleSmall.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    // The empty-chat greeting.
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.28).sp
    )
)

/** Current appearance plus a way to change it, for the Appearance sheet. Null in previews and tests. */
@Immutable
data class AppearanceController(
    val prefs: AppearancePrefs,
    val onChange: (AppearancePrefs) -> Unit
)

val LocalAppearanceController = compositionLocalOf<AppearanceController?> { null }

@Composable
fun HydrogenAITheme(
    appearance: AppearancePrefs = AppearancePrefs(),
    darkTheme: Boolean = appearance.isDark(isSystemInDarkTheme()),
    controller: AppearanceController? = null,
    content: @Composable () -> Unit
) {
    val palette = remember(darkTheme, appearance.pureBlack, appearance.accent) {
        OrbitPalettes.of(dark = darkTheme, pureBlack = appearance.pureBlack, accent = OrbitAccents.of(appearance.accent))
    }
    val scheme = remember(palette) { orbitColorScheme(palette) }
    CompositionLocalProvider(
        LocalOrbitPalette provides palette,
        LocalAppearanceController provides controller
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = OrbitTypography,
            content = content
        )
    }
}
