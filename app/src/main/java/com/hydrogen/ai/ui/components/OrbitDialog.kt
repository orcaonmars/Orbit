package com.hydrogen.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.ui.theme.LocalOrbitPalette
import com.hydrogen.ai.ui.theme.OrbitPalettes
import com.hydrogen.ai.ui.theme.OrbitTheme

/** Danger text is reserved for destructive confirms; ordinary confirms use the accent. */
internal fun dialogConfirmColor(destructive: Boolean, accent: Color, negative: Color): Color =
    if (destructive) negative else accent

/**
 * A reusable, themed Material3 [AlertDialog] for Orbit 0.3 screens.
 *
 * Functional contract:
 * - The dialog is shown only while [visible] is true; the caller owns that state.
 * - [title] and [message] are rendered with the current [OrbitTheme.colors].
 * - [onConfirm] fires when the confirm button is tapped (label via [confirmLabel]).
 * - [onDismiss] fires when the cancel/dismiss button is tapped (label via [dismissLabel])
 *   or when [onDismissRequest] triggers (scrim tap / back gesture). [onDismissRequest]
 *   is an optional extra hook that runs before [onDismiss].
 * - When [destructive] is true, the confirm label uses the palette's negative color so
 *   destructive actions read as distinct from ordinary confirms (danger on the right).
 * - [dismissLabel] is null when the caller only needs a single acknowledging action.
 */
@Composable
fun OrbitDialog(
    visible: Boolean,
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = "OK",
    dismissLabel: String? = null,
    destructive: Boolean = false,
    onDismissRequest: (() -> Unit)? = null
) {
    if (!visible) return
    val colors = OrbitTheme.colors
    AlertDialog(
        modifier = modifier,
        containerColor = colors.surface,
        titleContentColor = colors.text,
        textContentColor = colors.muted,
        shape = OrbitTheme.shapes.xl,
        onDismissRequest = {
            onDismissRequest?.invoke()
            onDismiss()
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = dialogConfirmColor(destructive, colors.accent, colors.negative)
                )
            }
        },
        dismissButton = {
            if (dismissLabel != null) {
                TextButton(onClick = onDismiss) {
                    Text(text = dismissLabel, color = colors.muted)
                }
            }
        },
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) }
    )
}

@Preview(name = "Paper dialog", showBackground = true)
@Composable
private fun PaperDialogPreview() {
    CompositionLocalProvider(LocalOrbitPalette provides OrbitPalettes.Paper) {
        DialogPreview(destructive = false)
    }
}

@Preview(name = "Night dialog", showBackground = true)
@Composable
private fun NightDialogPreview() {
    CompositionLocalProvider(LocalOrbitPalette provides OrbitPalettes.Night) {
        DialogPreview(destructive = false)
    }
}

@Preview(name = "Large font dialog", fontScale = 1.5f, showBackground = true)
@Composable
private fun LargeTextDialogPreview() {
    CompositionLocalProvider(LocalOrbitPalette provides OrbitPalettes.Paper) {
        DialogPreview(destructive = false)
    }
}

@Preview(name = "Destructive dialog", showBackground = true)
@Composable
private fun DestructiveDialogPreview() {
    CompositionLocalProvider(LocalOrbitPalette provides OrbitPalettes.Paper) {
        DialogPreview(destructive = true)
    }
}

/**
 * Renders the real [OrbitDialog] so previews show the shipped surface instead of a sketch.
 * Preview callbacks are intentionally inert; screens wire [OrbitDialog] to state.
 */
@Composable
private fun DialogPreview(destructive: Boolean) {
    val colors = OrbitTheme.colors
    Column(
        modifier = Modifier.background(colors.bg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Behind the dialog",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted
        )
        OrbitDialog(
            visible = true,
            title = if (destructive) "Delete this chat?" else "Move to project?",
            message = if (destructive) {
                "This removes the conversation from this device. It cannot be undone."
            } else {
                "Choose where this conversation should live. You can move it again later."
            },
            onConfirm = {},
            onDismiss = {},
            confirmLabel = if (destructive) "Delete" else "Move",
            dismissLabel = "Cancel",
            destructive = destructive
        )
    }
}
