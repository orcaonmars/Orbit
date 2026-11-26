package com.hydrogen.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.model.Persona
import com.hydrogen.ai.model.PersonaDraft
import com.hydrogen.ai.model.PersonaEditor
import com.hydrogen.ai.model.PersonaShareCodec
import com.hydrogen.ai.model.SharedPersona

/**
 * Create or edit your own assistant: a name and instructions are required; description, greeting and up
 * to four conversation starters are optional. [onSave] returns null when saved, or a message to show
 * (for example a name already in use). When [onImportInstead] is set, a link opens the import dialog.
 */
@Composable
fun PersonaEditorDialog(
    editing: Persona?,
    onDismiss: () -> Unit,
    onSave: (SharedPersona) -> String?,
    onImportInstead: (() -> Unit)? = null
) {
    val initial = remember(editing) { editing?.let(PersonaEditor::fromPersona) ?: PersonaDraft() }
    var name by remember(editing) { mutableStateOf(initial.name) }
    var tagline by remember(editing) { mutableStateOf(initial.tagline.takeIf { it != PersonaEditor.DEFAULT_TAGLINE }.orEmpty()) }
    var instructions by remember(editing) { mutableStateOf(initial.instructions) }
    // An automatic greeting stays automatic, so renaming the assistant renames its greeting too.
    var greeting by remember(editing) {
        mutableStateOf(initial.greeting.takeIf { it != PersonaEditor.defaultGreeting(initial.name) }.orEmpty())
    }
    var starters by remember(editing) {
        mutableStateOf(List(PersonaShareCodec.MAX_STARTERS) { initial.starterPrompts.getOrElse(it) { "" } })
    }
    var error by remember(editing) { mutableStateOf<String?>(null) }
    val sentence = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(com.hydrogen.ai.ui.OrbitIcons.Person, contentDescription = null) },
        title = { Text(if (editing == null) "Create an assistant" else "Edit ${editing.name}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Give it a name and tell it how to behave. It works with every model and stays on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(PersonaShareCodec.MAX_NAME); error = null },
                    label = { Text("Name") },
                    placeholder = { Text("Chef") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it.take(PersonaShareCodec.MAX_INSTRUCTIONS); error = null },
                    label = { Text("Instructions") },
                    placeholder = { Text("You are a friendly chef. Suggest simple recipes with what I have…") },
                    minLines = 3,
                    maxLines = 8,
                    keyboardOptions = sentence,
                    supportingText = {
                        Text("${instructions.length} / ${PersonaShareCodec.MAX_INSTRUCTIONS}")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tagline,
                    onValueChange = { tagline = it.take(PersonaShareCodec.MAX_TAGLINE); error = null },
                    label = { Text("Short description (optional)") },
                    placeholder = { Text("Recipes from your fridge") },
                    singleLine = true,
                    keyboardOptions = sentence,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = greeting,
                    onValueChange = { greeting = it.take(PersonaShareCodec.MAX_GREETING); error = null },
                    label = { Text("Greeting (optional)") },
                    placeholder = { Text("What's in your fridge?") },
                    singleLine = true,
                    keyboardOptions = sentence,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Conversation starters (optional)",
                    style = MaterialTheme.typography.labelLarge
                )
                starters.forEachIndexed { index, value ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { text ->
                            starters = starters.toMutableList().also {
                                it[index] = text.take(PersonaShareCodec.MAX_STARTER_PROMPT)
                            }
                            error = null
                        },
                        placeholder = { Text(if (index == 0) "Suggest a dinner with " else "Starter ${index + 1}") },
                        singleLine = true,
                        keyboardOptions = sentence,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (onImportInstead != null) {
                    TextButton(onClick = onImportInstead) { Text("Have a code? Import an assistant") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && instructions.isNotBlank(),
                onClick = {
                    PersonaEditor.build(PersonaDraft(name, tagline, greeting, instructions, starters))
                        .onSuccess { shared -> error = onSave(shared) }
                        .onFailure { error = it.message ?: "Please check the fields." }
                }
            ) { Text(if (editing == null) "Create" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
