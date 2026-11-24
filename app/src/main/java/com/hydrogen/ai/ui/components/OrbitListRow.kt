package com.hydrogen.ai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.ui.OrbitIcons
import com.hydrogen.ai.ui.theme.LocalOrbitPalette
import com.hydrogen.ai.ui.theme.OrbitPalettes
import com.hydrogen.ai.ui.theme.OrbitTheme

/**
 * Reusable clickable list row for chat, model and settings surfaces.
 * Surface owns the click semantics and ripples; callers provide a real action.
 * The row is at least 56dp tall and adapts its height for large system fonts.
 */
@Composable
fun OrbitListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    trailingText: String? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    showChevron: Boolean = false
) {
    val palette = OrbitTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) palette.surface2 else palette.surface,
        contentColor = if (enabled) palette.text else palette.muted
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = palette.muted
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.muted)
                }
            }
            if (trailingText != null) {
                Text(
                    trailingText,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.muted
                )
            }
            if (showChevron) {
                Icon(OrbitIcons.ChevronDown, contentDescription = null,
                    modifier = Modifier.size(18.dp).rotate(-90f), tint = palette.muted)
            }
        }
    }
}

@Preview(name = "Orbit list: Paper", showBackground = true)
@Composable
private fun OrbitListRowPaperPreview() {
    CompositionLocalProvider(LocalOrbitPalette provides OrbitPalettes.Paper) {
        RowPreview()
    }
}

@Preview(name = "Orbit list: Night", showBackground = true)
@Composable
private fun OrbitListRowNightPreview() {
    CompositionLocalProvider(LocalOrbitPalette provides OrbitPalettes.Night) {
        RowPreview()
    }
}

@Preview(name = "Orbit list: large text", showBackground = true, fontScale = 1.5f)
@Composable
private fun OrbitListRowLargeTextPreview() {
    CompositionLocalProvider(LocalOrbitPalette provides OrbitPalettes.Paper) {
        RowPreview()
    }
}

@Composable
private fun RowPreview() {
    OrbitListRow(
        title = "Local AI",
        subtitle = "Model available on this computer",
        leadingIcon = OrbitIcons.Check,
        trailingText = "Ready",
        onClick = {},
        selected = true
    )
}
