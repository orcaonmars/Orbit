package com.hydrogen.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.ui.theme.LocalOrbitPalette
import com.hydrogen.ai.ui.theme.OrbitPalettes
import com.hydrogen.ai.ui.theme.OrbitTheme

/**
 * A controlled, themed single-line form field for Orbit 0.3 screens.
 *
 * Functional contract:
 * - The caller owns [value] and receives every edit through [onValueChange].
 * - [label] is rendered as the accessible Material label (and screen-reader name).
 * - When [enabled] is false, editing stops but the field stays visible with muted colors.
 * - [singleLine] defaults to true for compact form rows; pass false for multi-line notes.
 * - Optional [placeholder] shows a hint when empty and unfocused.
 */
@Composable
fun OrbitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    placeholder: String? = null
) {
    val colors = OrbitTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        label = { Text(label) },
        placeholder = if (placeholder != null) ({ Text(placeholder) }) else null,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.line,
            disabledBorderColor = colors.line.copy(alpha = 0.5f),
            errorBorderColor = Color.Transparent,
            focusedTextColor = colors.text,
            unfocusedTextColor = colors.text,
            disabledTextColor = colors.muted,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.muted,
            cursorColor = colors.accent
        )
    )
}

@Preview(name = "Paper form field", showBackground = true)
@Composable
private fun PaperFormFieldPreview() {
    CompositionLocalProvider(LocalOrbitPalette provides OrbitPalettes.Paper) {
        FormFieldPreview()
    }
}

@Preview(name = "Night form field", showBackground = true)
@Composable
private fun NightFormFieldPreview() {
    CompositionLocalProvider(LocalOrbitPalette provides OrbitPalettes.Night) {
        FormFieldPreview()
    }
}

@Preview(name = "Large font form field", fontScale = 1.5f, showBackground = true)
@Composable
private fun LargeTextFormFieldPreview() {
    CompositionLocalProvider(LocalOrbitPalette provides OrbitPalettes.Paper) {
        FormFieldPreview()
    }
}

@Composable
private fun FormFieldPreview() {
    val colors = OrbitTheme.colors
    Column(
        modifier = Modifier.background(colors.bg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OrbitTextField(
            value = "Example",
            onValueChange = {},
            label = "Display name"
        )
        OrbitTextField(
            value = "",
            onValueChange = {},
            label = "Email address",
            placeholder = "you@example.com",
            enabled = false
        )
        Text("Preview only", style = MaterialTheme.typography.labelMedium, color = colors.muted)
    }
}
