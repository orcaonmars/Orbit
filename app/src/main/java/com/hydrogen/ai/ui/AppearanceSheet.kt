package com.hydrogen.ai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.model.AccentId
import com.hydrogen.ai.model.ThemeMode
import com.hydrogen.ai.ui.theme.AppearanceController
import com.hydrogen.ai.ui.theme.OrbitAccents
import com.hydrogen.ai.ui.theme.OrbitTheme
import com.hydrogen.ai.ui.theme.fillFor
import com.hydrogen.ai.ui.theme.onFillFor

/**
 * Theme, accent and pure black, with a live preview. The whole app (this sheet included) re-themes the
 * moment you tap, so the preview and the screen behind it show exactly what you picked.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppearanceSheet(controller: AppearanceController, onDismiss: () -> Unit) {
    val prefs = controller.prefs
    val haptics = LocalHapticFeedback.current
    val dark = prefs.isDark(isSystemInDarkTheme())
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Appearance", style = MaterialTheme.typography.titleLarge)
            AppearancePreview()

            SectionLabel("Theme")
            val modes = ThemeMode.values()
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = prefs.themeMode == mode,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            controller.onChange(prefs.copy(themeMode = mode))
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        label = { Text(mode.label) }
                    )
                }
            }

            SectionLabel("Accent")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val themeDark = OrbitTheme.colors.dark
                AccentId.values().forEach { id ->
                    val accent = OrbitAccents.of(id)
                    AccentSwatch(
                        label = id.label,
                        fill = accent.fillFor(themeDark),
                        onFill = accent.onFillFor(themeDark),
                        selected = id == prefs.accent,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            controller.onChange(prefs.copy(accent = id))
                        }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .toggleable(
                        value = prefs.pureBlack,
                        role = Role.Switch,
                        onValueChange = { controller.onChange(prefs.copy(pureBlack = it)) }
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pure black", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (dark) "True black for OLED screens. Easier on the eyes and the battery at night."
                        else "Takes effect whenever the dark theme is on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = prefs.pureBlack, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/** A miniature chat in the current theme: your bubble, a reply with a link, and the composer. */
@Composable
private fun AppearancePreview() {
    val orbit = OrbitTheme.colors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = orbit.bg,
        border = BorderStroke(1.dp, orbit.line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .background(orbit.bubble)
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text("Explain black holes simply", color = orbit.text, style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.Top) {
                HydrogenMark(size = 18.dp, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    buildAnnotatedString {
                        append("A place where gravity is so strong that not even light gets out. ")
                        withStyle(SpanStyle(color = orbit.accent, fontWeight = FontWeight.Medium)) {
                            append("Read more")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = orbit.text
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(orbit.raised)
                    .border(1.dp, orbit.line, RoundedCornerShape(28.dp))
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Text(
                    "Message Orbit",
                    color = orbit.faint,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                // Decorative: the real button is in the chat. Not focusable, so TalkBack doesn't offer a dead Send.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(orbit.accentFill)
                ) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = orbit.onAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AccentSwatch(label: String, fill: Color, onFill: Color, selected: Boolean, onClick: () -> Unit) {
    val ring by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
        label = "accent-ring"
    )
    val check by animateFloatAsState(
        if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "accent-check"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // Grows with its label (never below 68dp) so large font sizes wrap whole swatches, not words mid-letter.
        modifier = Modifier
            .widthIn(min = 68.dp)
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .border(2.dp, ring, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(fill)
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = onFill,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { scaleX = check; scaleY = check; alpha = check }
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
