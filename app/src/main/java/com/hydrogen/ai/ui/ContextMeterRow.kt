package com.hydrogen.ai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.model.ContextUsage
import com.hydrogen.ai.ui.theme.OrbitTheme

/**
 * The context ring above the composer (docs/ORBIT_DESIGN.md, Composer): hidden until a chat uses half of the
 * model's context budget, then a 16 dp ring that fills as the chat grows, with one plain line beside it. The ring
 * turns amber near the limit and red once the oldest messages stop being sent.
 */
@Composable
fun ContextMeterRow(usage: ContextUsage?, modifier: Modifier = Modifier) {
    val level = usage?.level ?: ContextUsage.Level.HIDDEN
    AnimatedVisibility(
        visible = level != ContextUsage.Level.HIDDEN,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val scheme = MaterialTheme.colorScheme
        val orbit = OrbitTheme.colors
        val tint = when (level) {
            ContextUsage.Level.TRIMMED -> scheme.error
            ContextUsage.Level.NEAR -> scheme.tertiary
            else -> orbit.accent
        }
        val progress by animateFloatAsState(
            targetValue = if (level == ContextUsage.Level.TRIMMED) 1f else (usage?.fraction ?: 0f).coerceIn(0f, 1f),
            label = "context-progress"
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
        ) {
            ContextRing(progress = progress, track = orbit.line, fill = tint)
            Spacer(Modifier.width(8.dp))
            Text(
                text = usage?.label.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = if (level == ContextUsage.Level.NOTICE) orbit.muted else tint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Decorative: the label beside it carries the words for screen readers. */
@Composable
private fun ContextRing(progress: Float, track: Color, fill: Color) {
    Canvas(Modifier.size(16.dp)) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
        drawArc(
            fill,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
    }
}
