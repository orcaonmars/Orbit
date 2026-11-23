package com.hydrogen.ai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.companion.AskPcProposal

/**
 * "Orbit suggests asking your PC" under an assistant reply. Shows exactly what would be sent (action and full
 * path) and the AI's reason as a quote. Sending needs a tap here, and the request still has to be approved on
 * the PC; see [AskPcCard] for the states.
 */
@Composable
internal fun AskPcSuggestionCard(
    proposal: AskPcProposal,
    model: AskPcCardModel,
    onAction: (AskPcCardAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Computer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = AskPcCard.TITLE,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Text(text = proposal.actionLabel, style = MaterialTheme.typography.labelLarge, color = muted)
            // The exact path that will be sent, so nothing about the request is hidden from the user.
            SelectionContainer {
                Text(
                    text = proposal.targetPath,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                )
            }
            proposal.reason?.let { reason ->
                Text(
                    text = "“$reason”",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = muted
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            ) {
                if (model.showProgress) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = muted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = model.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (model.isError) MaterialTheme.colorScheme.error else muted
                )
            }
            val action = model.action
            val label = model.actionLabel
            if (action != null && label != null) {
                when (action) {
                    AskPcCardAction.SEND, AskPcCardAction.SEND_AGAIN, AskPcCardAction.RUN, AskPcCardAction.ADD_RESULT ->
                        Button(onClick = { onAction(action) }, enabled = model.actionEnabled) { Text(label) }
                    AskPcCardAction.OPEN_COMPANION, AskPcCardAction.CHECK_APPROVAL, AskPcCardAction.CHECK_TASK ->
                        OutlinedButton(onClick = { onAction(action) }, enabled = model.actionEnabled) { Text(label) }
                }
            }
        }
    }
}
