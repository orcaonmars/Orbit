package com.hydrogen.ai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.ui.theme.OrbitTheme
import com.hydrogen.ai.model.CustomPersonaLibrary
import com.hydrogen.ai.model.Persona
import com.hydrogen.ai.model.Personas

/**
 * Lets a horizontally scrolling row run under its parent's side padding, so chips scroll off the screen
 * edge instead of being cut at the padding. Pair it with the same amount of content padding.
 */
internal fun Modifier.bleedHorizontally(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = (amount * 2).roundToPx()
    val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = maxWidth))
    val reported = (placeable.width - extra).coerceIn(constraints.minWidth, constraints.maxWidth)
    layout(reported, placeable.height) { placeable.place((reported - placeable.width) / 2, 0) }
}

/** Icon for a persona chip; plain Hydrogen (no persona) uses the sparkle, imported personas a face. */
internal fun personaIcon(persona: Persona?): ImageVector = when (persona?.id) {
    null -> Icons.Outlined.AutoAwesome
    "tutor" -> Icons.Outlined.School
    "coder" -> Icons.Outlined.Code
    "writer" -> Icons.Outlined.EditNote
    "translator" -> Icons.Outlined.Translate
    "coach" -> Icons.Outlined.Flag
    "brainstorm" -> Icons.Outlined.TipsAndUpdates
    else -> if (CustomPersonaLibrary.isCustom(persona.id)) Icons.Outlined.Face else Icons.Outlined.AutoAwesome
}

/** Icon for a persona starter card, by [com.hydrogen.ai.model.PersonaStarter.icon] key. */
internal fun starterIcon(key: String): ImageVector = when (key) {
    "quiz" -> Icons.Outlined.Quiz
    "math" -> Icons.Outlined.Calculate
    "calendar" -> Icons.Outlined.CalendarMonth
    "code" -> Icons.Outlined.Code
    "bug" -> Icons.Outlined.BugReport
    "review" -> Icons.Outlined.RateReview
    "edit" -> Icons.Outlined.EditNote
    "email" -> Icons.Outlined.Email
    "shorten" -> Icons.Outlined.Compress
    "campaign" -> Icons.Outlined.Campaign
    "translate" -> Icons.Outlined.Translate
    "language" -> Icons.Outlined.Language
    "spellcheck" -> Icons.Outlined.Spellcheck
    "habit" -> Icons.Outlined.Autorenew
    "checklist" -> Icons.Outlined.Checklist
    "decide" -> Icons.Outlined.Balance
    "name" -> Icons.Outlined.Badge
    "gift" -> Icons.Outlined.CardGiftcard
    "rocket" -> Icons.Outlined.RocketLaunch
    else -> Icons.Outlined.Lightbulb
}

/**
 * A scrollable row of assistant chips on the start screen: plain Hydrogen first, then each built-in
 * persona, then [custom] ones (made or imported) and, when [onCreate] is set, a "Create" chip at the end.
 * The selected chip wears the accent gradient. [contentPadding] lets the row scroll edge to edge.
 */
@Composable
fun PersonaPicker(
    active: Persona?,
    onPick: (Persona?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    custom: List<Persona> = emptyList(),
    onCreate: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding)
            .selectableGroup()
    ) {
        (listOf<Persona?>(null) + Personas.all + custom).forEach { persona ->
            PersonaChip(
                label = persona?.name ?: "Orbit",
                icon = personaIcon(persona),
                selected = active?.id == persona?.id,
                onClick = {
                    if (active?.id != persona?.id) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPick(persona)
                    }
                }
            )
        }
        if (onCreate != null) {
            PersonaChip(
                label = "Create",
                icon = Icons.Outlined.Add,
                selected = false,
                onClick = onCreate,
                role = Role.Button
            )
        }
    }
}

@Composable
private fun PersonaChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    role: Role = Role.RadioButton
) {
    val shape = RoundedCornerShape(50)
    val orbit = OrbitTheme.colors
    val content = orbit.text
    val base = Modifier
        .height(38.dp)
        .clip(shape)
        .let {
            if (role == Role.RadioButton) it.selectable(selected = selected, role = role, onClick = onClick)
            else it.clickable(role = role, onClick = onClick)
        }
    // Selected: accent-soft with an accent edge (docs/ORBIT_DESIGN.md, "Selected chips"). Not selected: quiet.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (selected) {
            base
                .background(orbit.accentSoft)
                .border(BorderStroke(1.dp, orbit.accent.copy(alpha = 0.55f)), shape)
        } else {
            base
                .background(orbit.surface)
                .border(BorderStroke(1.dp, orbit.line), shape)
        }.padding(start = 12.dp, end = 14.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) orbit.accent else orbit.muted,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
