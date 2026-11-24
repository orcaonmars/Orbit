package com.hydrogen.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.ui.theme.LocalOrbitPalette
import com.hydrogen.ai.ui.theme.OrbitPalettes
import com.hydrogen.ai.ui.theme.OrbitTheme

/** M4 pilot components. Existing screens are intentionally unchanged until visual acceptance. */
enum class OrbitButtonStyle { Primary, Secondary, Tonal, Ghost, Danger }
enum class OrbitButtonSize(val height: Dp) { M(40.dp), L(48.dp) }

internal data class OrbitButtonPaint(val background: Color, val foreground: Color)

internal fun buttonPaint(style: OrbitButtonStyle, primary: Color, onPrimary: Color,
                         surface: Color, text: Color, muted: Color, negative: Color): OrbitButtonPaint =
    when (style) {
        OrbitButtonStyle.Primary -> OrbitButtonPaint(primary, onPrimary)
        OrbitButtonStyle.Secondary -> OrbitButtonPaint(surface, text)
        OrbitButtonStyle.Tonal -> OrbitButtonPaint(primary.copy(alpha = 0.14f), text)
        OrbitButtonStyle.Ghost -> OrbitButtonPaint(Color.Transparent, muted)
        OrbitButtonStyle.Danger -> OrbitButtonPaint(negative.copy(alpha = 0.14f), negative)
    }

/** Loading disables clicks and replaces the leading icon with a progress indicator. */
@Composable
fun OrbitButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    style: OrbitButtonStyle = OrbitButtonStyle.Primary,
    size: OrbitButtonSize = OrbitButtonSize.M,
    enabled: Boolean = true, loading: Boolean = false, leadingIcon: ImageVector? = null
) {
    val colors = OrbitTheme.colors
    val paint = buttonPaint(style, colors.accentFill, colors.onAccent, colors.surface2,
        colors.text, colors.muted, colors.negative)
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.height(size.height),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = paint.background,
            contentColor = paint.foreground,
            disabledContainerColor = paint.background.copy(alpha = 0.5f),
            disabledContentColor = paint.foreground.copy(alpha = 0.6f)
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                    color = paint.foreground)
            } else if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(text)
        }
    }
}

@Composable
fun OrbitDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = OrbitTheme.colors.line)
}

@Composable
fun OrbitEmptyState(title: String, description: String, modifier: Modifier = Modifier,
                    actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val colors = OrbitTheme.colors
    Column(modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Preview(name = "Paper controls", showBackground = true)
@Composable private fun PaperControlsPreview() {
    CompositionLocalProvider(LocalOrbitPalette provides OrbitPalettes.Paper) { ControlsPreview() }
}

@Preview(name = "Night controls", showBackground = true)
@Composable private fun NightControlsPreview() {
    CompositionLocalProvider(LocalOrbitPalette provides OrbitPalettes.Night) { ControlsPreview() }
}

@Preview(name = "Large font controls", fontScale = 1.5f, showBackground = true)
@Composable private fun LargeFontControlsPreview() {
    CompositionLocalProvider(LocalOrbitPalette provides OrbitPalettes.Paper) { ControlsPreview() }
}

@Composable private fun ControlsPreview() {
    Column(Modifier.background(OrbitTheme.colors.bg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OrbitButton("Continue", onClick = {})
        OrbitButton("Try again", onClick = {}, style = OrbitButtonStyle.Secondary,
            size = OrbitButtonSize.L)
        OrbitButton("Loading", onClick = {}, loading = true)
        OrbitDivider()
        OrbitEmptyState("Nothing here yet", "Saved items will appear here.",
            actionLabel = "Refresh", onAction = {})
    }
}
