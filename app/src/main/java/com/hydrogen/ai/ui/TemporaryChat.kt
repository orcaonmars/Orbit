package com.hydrogen.ai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.ui.theme.OrbitTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** What the banner and the start screen say in a temporary chat. One place, so they never disagree. */
internal object TemporaryChatCopy {
    const val TITLE = "Temporary chat"
    const val BANNER = "Temporary chat. It won't be saved or use memory."
    const val START_LINE = "This one won't be saved, won't show up in your chats and won't use or add to memory."
    const val MENU_START = "Temporary chat"
    const val MENU_END = "End temporary chat"
}

/**
 * The temporary chat mark: Orbit's ring and dot, with the ring broken into dashes, an orbit that leaves no trace.
 * Off, the dot is an empty outline; [on], ring and dot take the accent and the dot fills in. Decorative; callers
 * give screen readers the words.
 */
@Composable
fun TemporaryChatGlyph(
    on: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    color: Color = LocalContentColor.current
) {
    val accent = OrbitTheme.colors.accent
    val tint by animateColorAsState(if (on) accent else color, label = "temporary-glyph")
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val ringWidth = s * 0.085f
        val dotRadius = s * 0.14f
        val radius = s / 2f - dotRadius
        // Twelve dashes that close evenly round the ring, whatever the size.
        val step = (2 * PI * radius).toFloat() / 12f
        drawCircle(
            tint,
            radius = radius,
            center = center,
            style = Stroke(
                width = ringWidth,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(step * 0.5f, step * 0.5f), step * 0.25f)
            )
        )
        // 1 o'clock, where the logo's dot rests.
        val rad = Math.toRadians(REST_DEGREES.toDouble())
        val dot = Offset(center.x + radius * cos(rad).toFloat(), center.y + radius * sin(rad).toFloat())
        if (on) {
            drawCircle(tint, radius = dotRadius, center = dot)
        } else {
            drawCircle(tint, radius = dotRadius - ringWidth / 2f, center = dot, style = Stroke(width = ringWidth))
        }
    }
}

/** Top bar switch on an empty chat: on starts a temporary chat, off goes back to where you were. */
@Composable
fun TemporaryChatToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    IconToggleButton(
        checked = on,
        onCheckedChange = onChange,
        modifier = Modifier.semantics {
            contentDescription = TemporaryChatCopy.TITLE
            stateDescription = if (on) "On" else "Off"
        }
    ) {
        TemporaryChatGlyph(on = on)
    }
}

/** The quiet pill under the top bar for as long as a temporary chat is open. */
@Composable
fun TemporaryChatBanner(modifier: Modifier = Modifier) {
    val orbit = OrbitTheme.colors
    val shape = RoundedCornerShape(50)
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, orbit.line, shape)
                .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp)
                .semantics(mergeDescendants = true) {}
        ) {
            TemporaryChatGlyph(on = true, size = 14.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                TemporaryChatCopy.BANNER,
                style = MaterialTheme.typography.labelMedium,
                color = orbit.muted,
                textAlign = TextAlign.Center
            )
        }
    }
}
