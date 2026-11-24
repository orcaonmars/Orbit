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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.model.AccentId
import com.hydrogen.ai.model.AppearancePrefs
import com.hydrogen.ai.model.ThemeMode
import com.hydrogen.ai.ui.components.OrbitSectionHeader
import com.hydrogen.ai.ui.theme.AppearanceController
import com.hydrogen.ai.ui.theme.HydrogenAITheme
import com.hydrogen.ai.ui.theme.OrbitAccents
import com.hydrogen.ai.ui.theme.OrbitPalette
import com.hydrogen.ai.ui.theme.OrbitPalettes
import com.hydrogen.ai.ui.theme.OrbitTheme
import com.hydrogen.ai.ui.theme.fillFor
import com.hydrogen.ai.ui.theme.onFillFor

/**
 * Theme, accent and pure black, with a live preview. The whole app (this sheet included) re-themes the
 * moment you tap, so the preview and the screen behind it show exactly what you picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSheet(controller: AppearanceController, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        AppearancePanel(
            controller = controller,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OrbitTheme.space.xl)
                .padding(top = OrbitTheme.space.xs, bottom = OrbitTheme.space.xxl)
        )
    }
}

/**
 * The appearance settings body, kept separate from the sheet so it can be previewed and measured.
 * Every choice writes through [AppearanceController.onChange]; nothing is held in local state.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearancePanel(controller: AppearanceController, modifier: Modifier = Modifier) {
    val prefs = controller.prefs
    val haptics = LocalHapticFeedback.current
    val systemDark = isSystemInDarkTheme()
    val dark = prefs.isDark(systemDark)
    val space = OrbitTheme.space
    val shapes = OrbitTheme.shapes

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(space.s)) {
        Column(verticalArrangement = Arrangement.spacedBy(space.xxs)) {
            Text(
                "Appearance",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                "Theme, accent and pure black. Changes apply across Orbit immediately.",
                style = MaterialTheme.typography.bodyMedium,
                color = OrbitTheme.colors.muted
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(space.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Live preview",
                    style = MaterialTheme.typography.labelMedium,
                    color = OrbitTheme.colors.muted
                )
                Text(
                    previewStateLabel(prefs.themeMode, systemDark, prefs.pureBlack),
                    style = MaterialTheme.typography.labelSmall,
                    color = OrbitTheme.colors.faint
                )
            }
            AppearancePreview()
        }

        Column {
            OrbitSectionHeader("Theme")
            ThemeChooser(
                prefs = prefs,
                systemDark = systemDark,
                onSelect = { mode ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    controller.onChange(prefs.copy(themeMode = mode))
                }
            )
        }

        Column {
            OrbitSectionHeader("Accent")
            Surface(
                shape = shapes.l,
                color = OrbitTheme.colors.surface,
                border = BorderStroke(1.dp, OrbitTheme.colors.line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = space.s, vertical = space.m),
                    verticalArrangement = Arrangement.spacedBy(space.xs)
                ) {
                    Text(
                        "Used for links, selections and the send button.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OrbitTheme.colors.muted,
                        modifier = Modifier.padding(horizontal = space.xs)
                    )
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(space.xs),
                        verticalArrangement = Arrangement.spacedBy(space.xs)
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
                }
            }
        }

        Column {
            OrbitSectionHeader("Display")
            Surface(
                shape = shapes.l,
                color = OrbitTheme.colors.surface,
                border = BorderStroke(1.dp, OrbitTheme.colors.line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = prefs.pureBlack,
                            role = Role.Switch,
                            onValueChange = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                controller.onChange(prefs.copy(pureBlack = it))
                            }
                        )
                        .padding(horizontal = space.l, vertical = space.m),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(space.xxs)
                    ) {
                        Text("Pure black", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (dark) "True black for OLED screens. Easier on the eyes and the battery at night."
                            else "Takes effect whenever the dark theme is on.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OrbitTheme.colors.muted
                        )
                    }
                    Spacer(Modifier.width(space.m))
                    Switch(checked = prefs.pureBlack, onCheckedChange = null)
                }
            }
        }

        Spacer(Modifier.height(space.s))
    }
}

/** The resolved theme palette for a choice card, so each card previews the real tokens. */
internal fun themePaletteFor(
    mode: ThemeMode,
    systemDark: Boolean,
    pureBlack: Boolean,
    accent: AccentId
): OrbitPalette {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    return OrbitPalettes.of(dark = dark, pureBlack = pureBlack, accent = OrbitAccents.of(accent))
}

/** Short, quiet copy under each theme choice. */
internal fun themeDescription(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Follows your phone"
    ThemeMode.LIGHT -> "Always light"
    ThemeMode.DARK -> "Always dark"
}

/** A truthful one-line summary of what the preview is showing right now. */
internal fun previewStateLabel(mode: ThemeMode, systemDark: Boolean, pureBlack: Boolean): String = when (mode) {
    ThemeMode.SYSTEM -> if (systemDark) "System, dark" else "System, light"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> if (pureBlack) "Dark, pure black" else "Dark"
}

/**
 * A row of three cards normally. At large system fonts the cards stack full width instead, so titles
 * and descriptions never get squeezed into a third of the screen.
 */
@Composable
private fun ThemeChooser(
    prefs: AppearancePrefs,
    systemDark: Boolean,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = ThemeMode.values()
    val space = OrbitTheme.space
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(space.s)
        ) {
            modes.forEach { mode ->
                ThemeChoiceCard(
                    mode = mode,
                    selected = prefs.themeMode == mode,
                    systemDark = systemDark,
                    pureBlack = prefs.pureBlack,
                    accent = prefs.accent,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelect(mode) }
                )
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(space.s)
        ) {
            modes.forEach { mode ->
                ThemeChoiceCard(
                    mode = mode,
                    selected = prefs.themeMode == mode,
                    systemDark = systemDark,
                    pureBlack = prefs.pureBlack,
                    accent = prefs.accent,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = { onSelect(mode) }
                )
            }
        }
    }
}

/**
 * A theme choice with a miniature of the real palette. System shows both the light and dark halves
 * so the "follows your phone" behaviour is visible before it is selected.
 */
@Composable
private fun ThemeChoiceCard(
    mode: ThemeMode,
    selected: Boolean,
    systemDark: Boolean,
    pureBlack: Boolean,
    accent: AccentId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val orbit = OrbitTheme.colors
    val space = OrbitTheme.space
    val shape = OrbitTheme.shapes.l
    val palette = themePaletteFor(mode, systemDark, pureBlack, accent)
    val alternate = if (mode == ThemeMode.SYSTEM) {
        themePaletteFor(mode, !systemDark, pureBlack, accent)
    } else {
        null
    }
    val borderColor by animateColorAsState(
        if (selected) orbit.accent else orbit.line,
        label = "theme-card-border"
    )
    Surface(
        shape = shape,
        color = if (selected) orbit.surface2 else orbit.surface,
        contentColor = orbit.text,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        modifier = modifier
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { }
    ) {
        Column(
            modifier = Modifier.padding(space.s),
            verticalArrangement = Arrangement.spacedBy(space.xs)
        ) {
            ThemeSwatch(
                primary = palette,
                alternate = alternate,
                selected = selected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )
            Text(
                mode.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) orbit.text else orbit.muted,
                maxLines = 1
            )
            Text(
                themeDescription(mode),
                style = MaterialTheme.typography.bodySmall,
                color = orbit.muted
            )
        }
    }
}

@Composable
private fun ThemeSwatch(
    primary: OrbitPalette,
    alternate: OrbitPalette?,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = OrbitTheme.shapes.m
    Box(
        modifier = modifier
            .clip(shape)
            .border(1.dp, OrbitTheme.colors.line, shape)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (alternate != null) {
                SwatchPane(alternate, Modifier.weight(1f))
                SwatchPane(primary, Modifier.weight(1f))
            } else {
                SwatchPane(primary, Modifier.fillMaxSize())
            }
        }
        if (selected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(OrbitTheme.space.xxs)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(primary.accentFill)
            ) {
                Icon(
                    OrbitIcons.Check,
                    contentDescription = null,
                    tint = primary.onAccent,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun SwatchPane(palette: OrbitPalette, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(palette.bg)
            .padding(OrbitTheme.space.xs),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(5.dp)
                .align(Alignment.End)
                .clip(CircleShape)
                .background(palette.bubble)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(3.dp)
                .clip(CircleShape)
                .background(palette.line)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .height(3.dp)
                .clip(CircleShape)
                .background(palette.line)
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(palette.raised)
                    .border(1.dp, palette.line, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(palette.accentFill)
            )
        }
    }
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
                    Icon(OrbitIcons.ArrowUpward, contentDescription = null, tint = orbit.onAccent, modifier = Modifier.size(16.dp))
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
        // Grows with its label (never below 52dp) so large font sizes wrap whole swatches, not words mid-letter.
        modifier = Modifier
            .widthIn(min = 52.dp)
            .clip(OrbitTheme.shapes.m)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = OrbitTheme.space.xxs, vertical = OrbitTheme.space.xs)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .border(2.dp, ring, CircleShape)
                .padding(OrbitTheme.space.xs)
                .clip(CircleShape)
                .background(fill)
        ) {
            Icon(
                OrbitIcons.Check,
                contentDescription = null,
                tint = onFill,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { scaleX = check; scaleY = check; alpha = check }
            )
        }
        Spacer(Modifier.height(OrbitTheme.space.xxs))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(name = "Appearance: Paper", showBackground = true, heightDp = 1000)
@Composable
private fun AppearancePaperPreview() = AppearancePreviewHost(AppearancePrefs(themeMode = ThemeMode.LIGHT))

@Preview(name = "Appearance: Night", showBackground = true, heightDp = 1000)
@Composable
private fun AppearanceNightPreview() = AppearancePreviewHost(AppearancePrefs(themeMode = ThemeMode.DARK, pureBlack = true))

@Preview(name = "Appearance: large text", showBackground = true, heightDp = 1200, fontScale = 1.5f)
@Composable
private fun AppearanceLargeTextPreview() = AppearancePreviewHost(AppearancePrefs(themeMode = ThemeMode.SYSTEM))

@Composable
private fun AppearancePreviewHost(prefs: AppearancePrefs) {
    val controller = AppearanceController(prefs) {}
    HydrogenAITheme(
        appearance = prefs,
        darkTheme = prefs.isDark(isSystemInDarkTheme()),
        controller = controller
    ) {
        AppearancePanel(
            controller = controller,
            modifier = Modifier
                .background(OrbitTheme.colors.bg)
                .padding(16.dp)
        )
    }
}
