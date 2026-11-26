package com.hydrogen.ai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.R
import com.hydrogen.ai.model.ReplyPlaybackPolicy
import com.hydrogen.ai.ui.theme.OrbitTheme

@Composable
internal fun ReplyPlaybackBar(speaker: MessageSpeaker) {
    if (speaker.speakingId == null) return
    val colors = OrbitTheme.colors
    val space = OrbitTheme.space
    var showRates by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = space.m, vertical = space.xs),
        color = colors.raised,
        contentColor = colors.text,
        shape = OrbitTheme.shapes.l,
        border = BorderStroke(1.dp, colors.line)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = space.s), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (speaker.paused) speaker.resume() else speaker.pause() }) {
                Icon(if (speaker.paused) com.hydrogen.ai.ui.OrbitIcons.Play else com.hydrogen.ai.ui.OrbitIcons.Pause,
                    contentDescription = stringResource(if (speaker.paused) R.string.reply_play_resume else R.string.reply_play_pause))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (speaker.paused) R.string.reply_play_paused else R.string.reply_play_reading),
                    style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                speaker.caption?.let { caption ->
                    Text(caption, color = colors.muted, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box {
                TextButton(onClick = { showRates = true }) { Text(speedLabel(speaker.speed)) }
                OrbitMenu(expanded = showRates, onDismissRequest = { showRates = false }) {
                    ReplyPlaybackPolicy.speeds.forEach { rate ->
                        OrbitMenuItem(text = { Text(speedLabel(rate)) }, onClick = {
                            showRates = false
                            speaker.setPlaybackSpeed(rate)
                        })
                    }
                }
            }
            IconButton(onClick = speaker::stop) {
                Icon(com.hydrogen.ai.ui.OrbitIcons.Stop, contentDescription = stringResource(R.string.reply_play_stop))
            }
        }
    }
}

private fun speedLabel(rate: Float): String =
    rate.toString().removeSuffix(".0") + "\u00d7"