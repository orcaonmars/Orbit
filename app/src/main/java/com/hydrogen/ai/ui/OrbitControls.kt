package com.hydrogen.ai.ui

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.ui.theme.OrbitTheme
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** True when the phone's animations are turned off (Settings > Accessibility > Remove animations). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/** Where the dot rests when nothing is happening: 1 o'clock, which is the static logo. */
internal const val REST_DEGREES = -60f
/** Straight up, where the voice chat dot waits while listening. */
internal const val TOP_DEGREES = -90f
private const val TURN_MS = 1_200

/**
 * The dot's angle on the ring, in degrees clockwise from 3 o'clock. While [spinning] it goes round once every
 * 1.2 s; when it stops it eases forward to [rest] instead of jumping back. Reduced motion keeps it at rest.
 */
@Composable
internal fun rememberOrbitAngle(spinning: Boolean, reducedMotion: Boolean, rest: Float = REST_DEGREES): Float {
    val angle = remember { Animatable(rest) }
    LaunchedEffect(spinning, reducedMotion, rest) {
        if (spinning && !reducedMotion) {
            while (true) {
                angle.animateTo(angle.value + 360f, tween(TURN_MS, easing = LinearEasing))
            }
        } else {
            val turns = ceil((angle.value - rest) / 360f)
            val target = rest + turns * 360f
            if (reducedMotion) angle.snapTo(target) else angle.animateTo(target, tween(600, easing = FastOutSlowInEasing))
        }
    }
    return angle.value
}

internal fun DrawScope.drawOrbit(ringColor: Color, dotColor: Color, degrees: Float, ringWidth: Float, dotRadius: Float) {
    val radius = size.minDimension / 2f - dotRadius
    drawCircle(ringColor, radius = radius, center = center, style = Stroke(width = ringWidth))
    val rad = Math.toRadians(degrees.toDouble())
    drawCircle(
        dotColor,
        radius = dotRadius,
        center = Offset(center.x + radius * cos(rad).toFloat(), center.y + radius * sin(rad).toFloat())
    )
}

/**
 * Orbit's activity mark: a thin ring with a dot. The dot travels round while Orbit is thinking or writing and
 * rests at 1 o'clock otherwise. Decorative; the caller gives screen readers the words.
 */
@Composable
fun OrbitIndicator(
    spinning: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    ringColor: Color = OrbitTheme.colors.line,
    dotColor: Color = OrbitTheme.colors.accent
) {
    val degrees = rememberOrbitAngle(spinning, rememberReducedMotion())
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        drawOrbit(ringColor, dotColor, degrees, ringWidth = s * 0.09f, dotRadius = s * 0.14f)
    }
}

/**
 * The sun button: Orbit's one filled accent circle. Send, voice chat and the dictation check use it. With
 * [stopping] it becomes Stop while a reply streams: a small rounded square in the middle and the orbit
 * indicator running round the edge.
 */
@Composable
fun SunButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    stopping: Boolean = false
) {
    val colors = OrbitTheme.colors
    val fill by animateColorAsState(if (enabled) colors.accentFill else colors.surface2, tween(120), label = "sun-fill")
    val tint by animateColorAsState(if (enabled) colors.onAccent else colors.faint, tween(120), label = "sun-icon")
    val degrees = rememberOrbitAngle(stopping, rememberReducedMotion())
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        if (stopping) {
            Canvas(Modifier.size(size)) {
                val s = this.size.minDimension
                val square = s * 0.30f
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(center.x - square / 2f, center.y - square / 2f),
                    size = Size(square, square),
                    cornerRadius = CornerRadius(s * 0.06f)
                )
                inset(s * 0.10f) {
                    drawOrbit(tint.copy(alpha = 0.35f), tint, degrees, ringWidth = s * 0.035f, dotRadius = s * 0.05f)
                }
            }
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/** Text buttons use a modest 12 dp corner, the same as OrbitButton, instead of Material's default full pill. */
val OrbitButtonShape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
