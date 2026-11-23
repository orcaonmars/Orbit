package com.hydrogen.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.ui.theme.OrbitTheme

/**
 * The Orbit mark: a ring with a small dot resting at 1 o'clock, like a moon on its path
 * (docs/ORBIT_DESIGN.md, section 1). The same drawing as [OrbitIndicator] when it isn't moving.
 */
@Composable
fun HydrogenMark(size: Dp, modifier: Modifier = Modifier) {
    val colors = OrbitTheme.colors
    OrbitIndicator(spinning = false, modifier = modifier, size = size, ringColor = colors.muted, dotColor = colors.accent)
}

/**
 * Shown where a reply is about to appear: the orbit indicator turning, plus a word. With reduced motion the dot
 * stays still and the word carries the meaning. Screen readers hear "Orbit is thinking".
 */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier, label: String = "Thinking") {
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = "Orbit is ${label.lowercase()}" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        OrbitIndicator(spinning = true, size = 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OrbitTheme.colors.muted)
    }
}

/** Small rounded-square icon tile in the accent, used on starter and onboarding cards. */
@Composable
fun BrandIconTile(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = OrbitTheme.colors.accent) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(OrbitTheme.colors.accentSoft),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}
