package com.hydrogen.ai.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.model.StartGreeting
import com.hydrogen.ai.ui.theme.OrbitTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The phone's local time, refreshed once a minute so the greeting and the sky stay right if the screen stays open. */
@Composable
internal fun rememberLocalMinute(): LocalTime {
    val now by produceState(initialValue = LocalTime.now().withSecond(0).withNano(0)) {
        while (true) {
            val current = LocalTime.now()
            delay(((60 - current.second) * 1_000L).coerceAtLeast(1_000L))
            value = LocalTime.now().withSecond(0).withNano(0)
        }
    }
    return now
}

/**
 * The start screen's horizon: a thin arc with the sun on it by day and a hollow moon by night, placed by the
 * local time (docs/ORBIT_DESIGN.md, "Empty state"). The dot slides along the arc once when the screen opens.
 * Purely decorative, so screen readers skip it; the greeting below carries the words.
 */
@Composable
internal fun HorizonArc(time: LocalTime, modifier: Modifier = Modifier) {
    val orbit = OrbitTheme.colors
    val sky = StartGreeting.skyPosition(time.hour * 60 + time.minute)
    val reducedMotion = rememberReducedMotion()
    val travel = remember { Animatable(if (reducedMotion) sky.fraction else 0f) }
    LaunchedEffect(sky.fraction, sky.day, reducedMotion) {
        // A new day or night starts the dot at the left again rather than sliding it backwards across the sky.
        if (sky.fraction < travel.value - 0.5f) travel.snapTo(0f)
        if (reducedMotion) travel.snapTo(sky.fraction)
        else travel.animateTo(sky.fraction, tween(durationMillis = 600, easing = FastOutSlowInEasing))
    }
    Canvas(
        modifier = modifier
            .size(width = 240.dp, height = 52.dp)
            .clearAndSetSemantics { }
    ) {
        val stroke = 1.5.dp.toPx()
        val dot = 5.dp.toPx()
        val glow = dot * 2f
        val left = glow
        val right = size.width - glow
        val top = glow
        val baseline = size.height - stroke
        val radiusX = (right - left) / 2f
        val radiusY = baseline - top
        drawArc(
            color = orbit.line,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(left, top),
            size = Size(right - left, radiusY * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        val angle = PI * (1.0 - travel.value)
        val at = Offset(
            x = (left + radiusX + radiusX * cos(angle)).toFloat(),
            y = (baseline - radiusY * sin(angle)).toFloat()
        )
        if (sky.day) {
            drawCircle(orbit.accentSoft, radius = glow, center = at)
            drawCircle(orbit.accentFill, radius = dot, center = at)
        } else {
            drawCircle(orbit.bg, radius = dot, center = at)
            drawCircle(orbit.muted, radius = dot, center = at, style = Stroke(width = stroke))
        }
    }
}
