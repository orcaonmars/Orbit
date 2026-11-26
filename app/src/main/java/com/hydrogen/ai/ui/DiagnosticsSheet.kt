package com.hydrogen.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.BuildConfig
import com.hydrogen.ai.model.ErrorExplainer
import com.hydrogen.ai.model.ErrorLogEntry
import com.hydrogen.ai.model.ProviderError

/** Local-only diagnostics. The data store redacts secrets before these entries reach UI. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsSheet(
    entries: List<ErrorLogEntry>,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var expandedId by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Connection diagnostics", style = MaterialTheme.typography.titleLarge)
            Text(
                "Problems are stored only on this device. Reports exclude chat text, attachments, and API keys.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (entries.isEmpty()) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("No recent connection errors", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "When a provider fails, you'll find a reason and suggested fixes here.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(shape = com.hydrogen.ai.ui.OrbitButtonShape, onClick = {
                        clipboard.setText(
                            AnnotatedString(ErrorExplainer.reportAll(entries, BuildConfig.VERSION_NAME))
                        )
                    }) { Text("Copy report") }
                    TextButton(onClick = { confirmClear = true }) { Text("Clear history") }
                }
                entries.take(50).forEach { entry ->
                    val expanded = expandedId == entry.id
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${entry.service ?: entry.serverHost.ifBlank { "Local service" }}, " +
                                    ErrorExplainer.formatTime(entry.atEpochMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(entry.message, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { expandedId = if (expanded) null else entry.id }) {
                                Text(if (expanded) "Hide details" else "What to do and technical details")
                            }
                            if (expanded) {
                                val error = ProviderError(
                                    category = entry.category, httpStatus = entry.httpStatus,
                                    userMessage = entry.message, technicalDetail = entry.detail,
                                    retryAfterSeconds = entry.retryAfterSeconds,
                                    sourceLayer = entry.sourceLayer
                                )
                                ErrorExplainer.steps(error, entry.service).forEachIndexed { index, step ->
                                    Text("${index + 1}. $step", style = MaterialTheme.typography.bodySmall)
                                }
                                if (entry.detail.isNotBlank()) {
                                    HorizontalDivider()
                                    Text(entry.detail, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = {
                                    clipboard.setText(
                                        AnnotatedString(ErrorExplainer.report(entry, BuildConfig.VERSION_NAME))
                                    )
                                }) { Text("Copy this error") }
                            }
                        }
                    }
                }
                if (entries.size > 50) {
                    Text(
                        "Showing the newest 50 of ${entries.size}. Copy report includes all saved entries.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(shape = com.hydrogen.ai.ui.OrbitButtonShape, onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear diagnostic history?") },
            text = { Text("This deletes saved error reports from this device. It does not delete chats or keys.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }) { Text("Clear history") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}
