package com.hydrogen.ai.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.model.CompareModelsPolicy
import com.hydrogen.ai.model.ModelDisplayName

/**
 * "Ask another model": pick one of the models the server listed (loaded on open when needed) or type a model
 * name, and the latest question is answered again by it, next to the first answer.
 */
@Composable
fun CompareModelsDialog(
    modelList: ModelListUiState,
    answered: Set<String>,
    onLoadModels: () -> Unit,
    onDismiss: () -> Unit,
    onAsk: (String) -> Unit
) {
    LaunchedEffect(Unit) {
        if (modelList.modelIds.isEmpty() && !modelList.isLoading) onLoadModels()
    }
    val choices = CompareModelsPolicy.choices(modelList.modelIds, answered)
    var selected by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    val pick = typed.trim().ifEmpty { selected }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Compare, contentDescription = null) },
        title = { Text("Ask another model") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
            ) {
                Text(
                    "The same question goes to the model you pick and its answer appears below the first one, " +
                        "so you can compare them. Later messages keep using the first answer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    modelList.isLoading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Loading models…", style = MaterialTheme.typography.bodyMedium)
                    }
                    choices.isEmpty() -> Text(
                        if (modelList.modelIds.isEmpty()) {
                            (modelList.message?.let { "$it " } ?: "") + "Type a model name below."
                        } else {
                            "Every listed model has answered. Type another model name below."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> choices.forEach { id ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selected = id; typed = "" }
                                .padding(end = 8.dp)
                        ) {
                            RadioButton(selected = typed.isBlank() && selected == id, onClick = { selected = id; typed = "" })
                            Column {
                                Text(ModelDisplayName.of(id), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (ModelDisplayName.of(id) != id) {
                                    Text(id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(200) },
                    label = { Text("Or type a model name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pick.isNotEmpty() && pick !in answered,
                onClick = { onAsk(pick) }
            ) { Text("Ask") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
