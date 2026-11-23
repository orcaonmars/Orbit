package com.hydrogen.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.model.PromptTemplate
import com.hydrogen.ai.model.SavedPrompt

/** Asks for each {{blank}} in a saved prompt, then hands back the filled-in text. */
@Composable
fun PromptTemplateDialog(
    prompt: SavedPrompt,
    onDismiss: () -> Unit,
    onUse: (String) -> Unit
) {
    val variables = remember(prompt.text) { PromptTemplate.variables(prompt.text) }
    val values = remember(prompt.id) { mutableStateMapOf<String, String>() }
    val complete = variables.all { values[it].orEmpty().isNotBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                PromptTemplate.blanked(prompt.text),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Fill in the blanks for this prompt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                variables.forEach { name ->
                    OutlinedTextField(
                        value = values[name].orEmpty(),
                        onValueChange = { values[name] = it },
                        label = { Text(PromptTemplate.label(name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = complete,
                onClick = { onUse(PromptTemplate.fill(prompt.text, values.mapValues { it.value.trim() })) }
            ) { Text("Use prompt") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
