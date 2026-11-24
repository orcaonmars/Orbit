package com.hydrogen.ai.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared Orbit Studio design tokens. Do not duplicate OrbitPalette or change
 * the existing Night, Paper, Midnight or accent choices.
 *
 * These are semantic constants for gradual screen migration, not a second theme.
 */
@Immutable
data class OrbitSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
    val huge: Dp = 48.dp
)

@Immutable
data class OrbitShapeTokens(
    val xs: RoundedCornerShape = RoundedCornerShape(6.dp),
    val s: RoundedCornerShape = RoundedCornerShape(8.dp),
    val m: RoundedCornerShape = RoundedCornerShape(12.dp),
    val l: RoundedCornerShape = RoundedCornerShape(16.dp),
    val xl: RoundedCornerShape = RoundedCornerShape(20.dp),
    val xxl: RoundedCornerShape = RoundedCornerShape(28.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50)
)

/** Central durations and easing for UI animation. Consumers must respect reduced motion. */
@Immutable
data class OrbitMotionTokens(
    val fastMs: Int = 120,
    val standardMs: Int = 220,
    val emphasizedMs: Int = 350,
    val longMs: Int = 500,
    val standardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f),
    val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f),
    val dragSpringDampingRatio: Float = 0.88f,
    val dragSpringStiffness: Float = 420f
)

internal val LocalOrbitSpacing = staticCompositionLocalOf { OrbitSpacing() }
internal val LocalOrbitShapeTokens = staticCompositionLocalOf { OrbitShapeTokens() }
internal val LocalOrbitMotionTokens = staticCompositionLocalOf { OrbitMotionTokens() }
internal val LocalOrbitTypography = staticCompositionLocalOf<Typography> { OrbitTypography }
