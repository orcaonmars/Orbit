package com.hydrogen.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.ui.OrbitIcons
import com.hydrogen.ai.ui.theme.LocalOrbitPalette
import com.hydrogen.ai.ui.theme.OrbitPalettes
import com.hydrogen.ai.ui.theme.OrbitTheme

/** A consistent, accessible 48dp icon target across Orbit screens. */
@Composable
fun OrbitIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    val colors = OrbitTheme.colors
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp).background(
            if (selected) colors.surface2 else Color.Transparent,
            RoundedCornerShape(12.dp)
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = if (enabled && selected) colors.text else colors.muted
        )
    }
}

/**
 * A controlled search field. The caller owns its value and filtering logic.
 * Clear is shown only for non-blank input and restores the empty query immediately.
 */
@Composable
fun OrbitSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    clearDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = OrbitTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        shape = RoundedCornerShape(18.dp),
        leadingIcon = {
            Icon(
                imageVector = OrbitIcons.Search,
                contentDescription = null,
                tint = colors.muted
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                OrbitIconButton(
                    icon = OrbitIcons.Close,
                    contentDescription = clearDescription,
                    onClick = { onValueChange("") },
                    enabled = enabled
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.line,
            focusedTextColor = colors.text,
            unfocusedTextColor = colors.text,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.muted,
            cursorColor = colors.accent
        )
    )
}

@Preview(name = "Paper search", showBackground = true)
@Composable
private fun PaperSearchPreview() {
    CompositionLocalProvider(LocalOrbitPalette provides OrbitPalettes.Paper) {
        SearchControlsPreview()
    }
}

@Preview(name = "Night search", showBackground = true)
@Composable
private fun NightSearchPreview() {
    CompositionLocalProvider(LocalOrbitPalette provides OrbitPalettes.Night) {
        SearchControlsPreview()
    }
}

@Preview(name = "Large font search", fontScale = 1.5f, showBackground = true)
@Composable
private fun LargeTextSearchPreview() {
    CompositionLocalProvider(LocalOrbitPalette provides OrbitPalettes.Paper) {
        SearchControlsPreview()
    }
}

@Composable
private fun SearchControlsPreview() {
    val colors = OrbitTheme.colors
    Column(
        modifier = Modifier.background(colors.bg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OrbitSearchField(
            value = "Example",
            onValueChange = {},
            label = "Search chats",
            clearDescription = "Clear search"
        )
        OrbitIconButton(
            icon = OrbitIcons.Close,
            contentDescription = "Close",
            onClick = {},
            selected = true
        )
        Text("Preview only", style = MaterialTheme.typography.labelMedium, color = colors.muted)
    }
}
