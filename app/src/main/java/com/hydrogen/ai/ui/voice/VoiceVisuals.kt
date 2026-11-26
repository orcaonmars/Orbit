package com.hydrogen.ai.ui.voice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrogen.ai.model.Dictation
import com.hydrogen.ai.model.VoiceConversation
import com.hydrogen.ai.ui.SunButton
import com.hydrogen.ai.ui.TOP_DEGREES
import com.hydrogen.ai.ui.rememberOrbitAngle
import com.hydrogen.ai.ui.rememberReducedMotion
import com.hydrogen.ai.ui.theme.OrbitTheme
import kotlin.math.cos
import kotlin.math.sin

private val BarWidth = 3.dp
private val BarGap = 3.dp

/**
 * The live mic level: 28 rounded accent bars, newest on the right, scrolling left as you talk.
 * Quiet bars stay as short dots so the strip never looks empty.
 */
@Composable
fun DictationWaveform(levels: List<Float>, modifier: Modifier = Modifier) {
    val color = OrbitTheme.colors.accent
    val bars = Dictation.bars(levels)
    val width = BarWidth * bars.size + BarGap * (bars.size - 1)
    Canvas(modifier.width(width).height(28.dp)) {
        val barPx = BarWidth.toPx()
        val step = barPx + BarGap.toPx()
        bars.forEachIndexed { index, level ->
            val h = (size.height * level).coerceAtLeast(barPx)
            drawRoundRect(
                color = color,
                topLeft = Offset(index * step, (size.height - h) / 2f),
                size = Size(barPx, h),
                cornerRadius = CornerRadius(barPx / 2f)
            )
        }
    }
}

/**
 * The composer's toolbar while dictating: cancel, the waveform, the timer and the check. The check stops
 * listening and keeps the words in the box without sending them.
 */
@Composable
fun DictationToolbar(speech: InAppSpeech, onCancel: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OrbitTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCancel) {
            Icon(com.hydrogen.ai.ui.OrbitIcons.Close, contentDescription = "Cancel recording", tint = colors.muted)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = Dictation.recordingLabel(speech.elapsedMs) },
            contentAlignment = Alignment.Center
        ) {
            DictationWaveform(speech.levels)
        }
        Text(
            text = Dictation.formatElapsed(speech.elapsedMs),
            color = colors.faint,
            fontSize = 13.sp,
            style = TextStyle(fontFeatureSettings = "tnum"),
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        SunButton(
            icon = com.hydrogen.ai.ui.OrbitIcons.Check,
            contentDescription = "Stop and keep the text",
            onClick = onDone,
            enabled = !speech.finishing
        )
    }
}

private val OrbSize = 168.dp

/**
 * The voice chat orb: a thin ring with the sun dot.
 * Listening: the ring breathes with your voice, the dot waits at the top. Thinking: the dot orbits.
 * Speaking: the ring turns accent and two faint rings ripple out. Reduced motion keeps everything still.
 */
@Composable
fun VoiceOrb(
    phase: VoiceConversation.Phase,
    listening: Boolean,
    level: Float,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    val reducedMotion = rememberReducedMotion()
    val speaking = phase == VoiceConversation.Phase.SPEAKING
    val ringColor by animateColorAsState(if (speaking) colors.accent else colors.line, tween(240), label = "orb-ring")
    val targetScale = if (!reducedMotion && listening && phase == VoiceConversation.Phase.LISTENING) {
        1f + 0.10f * level.coerceIn(0f, 1f)
    } else {
        1f
    }
    val orbScale by animateFloatAsState(targetScale, tween(120), label = "orb-scale")
    val degrees = rememberOrbitAngle(
        spinning = phase == VoiceConversation.Phase.THINKING,
        reducedMotion = reducedMotion,
        rest = TOP_DEGREES
    )
    val ripple = if (speaking && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "orb-ripple")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Restart),
            label = "ripple"
        )
        p
    } else {
        null
    }
    val dotColor = colors.accentFill
    // Room around the ring for the ripples.
    Canvas(modifier.size(OrbSize * 1.5f)) {
        val ringRadius = OrbSize.toPx() / 2f
        val stroke = 2.dp.toPx()
        val dotRadius = 7.dp.toPx()
        if (ripple != null) {
            listOf(ripple, (ripple + 0.5f) % 1f).forEach { p ->
                drawCircle(
                    color = colors.accent.copy(alpha = 0.32f * (1f - p)),
                    radius = ringRadius * (1f + 0.42f * p),
                    style = Stroke(width = stroke)
                )
            }
        }
        scale(orbScale) {
            drawCircle(ringColor, radius = ringRadius, style = Stroke(width = stroke))
            val rad = Math.toRadians(degrees.toDouble())
            drawCircle(
                dotColor,
                radius = dotRadius,
                center = Offset(
                    center.x + ringRadius * cos(rad).toFloat(),
                    center.y + ringRadius * sin(rad).toFloat()
                )
            )
        }
    }
}

/**
 * Full-screen hands-free voice chat. The orb in the middle shows what Orbit is doing; the status line says it
 * in words; the caption shows the last thing said (yours while you talk, Orbit's sentence while it reads).
 */
@Composable
fun VoiceChatScreen(
    phase: VoiceConversation.Phase,
    listening: Boolean,
    level: Float,
    muted: Boolean,
    caption: String?,
    note: String,
    onOrbTap: () -> Unit,
    onToggleMute: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    BackHandler(onBack = onEnd)
    val status = VoiceConversation.status(phase, muted, listening)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            // Swallow taps so nothing underneath reacts.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = note,
            color = colors.faint,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = orbActionLabel(phase, muted, listening), onClick = onOrbTap)
                .semantics { contentDescription = status },
            contentAlignment = Alignment.Center
        ) {
            VoiceOrb(phase = phase, listening = listening, level = level)
        }
        AnimatedContent(
            targetState = status,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
            label = "voice-status"
        ) { text ->
            Text(
                text = text,
                color = colors.muted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = caption.orEmpty(),
                color = colors.text,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            VoiceRoundButton(
                onClick = onToggleMute,
                label = if (muted) "Turn mic on" else "Mute mic",
                background = colors.surface2
            ) {
                Icon(if (muted) com.hydrogen.ai.ui.OrbitIcons.MicOff else com.hydrogen.ai.ui.OrbitIcons.Mic, contentDescription = null, tint = colors.text)
            }
            VoiceRoundButton(
                onClick = onEnd,
                label = "End voice chat",
                background = colors.negative.copy(alpha = 0.18f)
            ) {
                Icon(com.hydrogen.ai.ui.OrbitIcons.Close, contentDescription = null, tint = colors.negative)
            }
        }
    }
}

private fun orbActionLabel(phase: VoiceConversation.Phase, muted: Boolean, listening: Boolean): String = when (phase) {
    VoiceConversation.Phase.SPEAKING -> "Interrupt"
    VoiceConversation.Phase.THINKING -> "Wait"
    VoiceConversation.Phase.LISTENING -> when {
        listening -> "Done talking"
        muted -> "Turn mic on and talk"
        else -> "Talk"
    }
}

@Composable
private fun VoiceRoundButton(
    onClick: () -> Unit,
    label: String,
    background: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
