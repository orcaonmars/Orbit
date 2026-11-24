package com.hydrogen.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.R
import androidx.compose.ui.res.stringResource
import com.hydrogen.ai.ui.components.OrbitSearchField
import com.hydrogen.ai.ui.theme.OrbitTheme
import com.hydrogen.ai.model.MemoryItem
import com.hydrogen.ai.model.UserMemory

/**
 * "Memory you control": notes Orbit adds to every chat. Everything here stays on the phone; the notes travel
 * only inside the system message sent to the model the user is chatting with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorySheet(
    items: List<MemoryItem>,
    enabled: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onAdd: (String) -> Boolean,
    onUpdate: (id: String, text: String) -> Boolean,
    onRemove: (String) -> Unit,
    onSetPinned: (String, Boolean) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var newText by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val visible = remember(items, query, editingId) { UserMemory.search(items, query).let { matches -> items.filter { it in matches || it.id == editingId } } }
    val space = OrbitTheme.space

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.xl)
                .padding(bottom = space.xxxl),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Memory", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(
                "Orbit adds these notes to every chat, so you don't have to repeat yourself. They stay on this " +
                    "phone and are sent only to the model you're chatting with. Tip: type \"remember that …\" in " +
                    "any chat to add one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(value = enabled, role = Role.Switch, onValueChange = onSetEnabled),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use memory in chats", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (enabled) "On: notes are added to every chat." else "Off: notes are kept but not sent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // The whole row toggles; the switch itself is decorative for accessibility.
                Switch(checked = enabled, onCheckedChange = null)
            }

            val submitNew: () -> Unit = {
                if (newText.isNotBlank()) {
                    if (onAdd(newText)) {
                        newText = ""
                    } else {
                        addError = "That note is already saved."
                    }
                }
            }
            // The add action lives inside the field (and on the keyboard's Done key) so it stays
            // reachable while the keyboard covers the bottom of the sheet.
            OutlinedTextField(
                value = newText,
                onValueChange = {
                    newText = it.take(UserMemory.MAX_ITEM_CHARS)
                    addError = null
                },
                label = { Text("New note") },
                placeholder = { Text("e.g. I prefer metric units") },
                supportingText = {
                    Text(addError ?: "${newText.length} / ${UserMemory.MAX_ITEM_CHARS}")
                },
                trailingIcon = {
                    IconButton(onClick = submitNew, enabled = newText.isNotBlank()) {
                        Icon(Icons.Filled.AddCircle, contentDescription = "Add note")
                    }
                },
                isError = addError != null,
                minLines = 1,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submitNew() }),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "Saved notes (${items.size} of ${UserMemory.MAX_ITEMS})",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() }
            )
            if (items.isEmpty()) {
                Text(
                    "Nothing saved yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (items.size >= 4 || query.isNotEmpty()) {
                OrbitSearchField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.memory_search),
                    clearDescription = stringResource(R.string.memory_clear_search)
                )
            }
            if (items.isNotEmpty() && visible.isEmpty()) {
                Text(stringResource(R.string.memory_no_matches), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            visible.sortedByDescending { it.pinned }.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (editingId == item.id) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = editText,
                                onValueChange = {
                                    editText = it.take(UserMemory.MAX_ITEM_CHARS)
                                    editError = null
                                },
                                label = { Text("Edit note") },
                                supportingText = { Text(editError ?: "${editText.length} / ${UserMemory.MAX_ITEM_CHARS}") },
                                isError = editError != null,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { editingId = null; editError = null }) { Text("Cancel") }
                                TextButton(
                                    enabled = editText.isNotBlank(),
                                    onClick = {
                                        val unchanged = UserMemory.clean(editText) == item.text
                                        if (unchanged || onUpdate(item.id, editText)) {
                                            editingId = null
                                            editError = null
                                        } else {
                                            editError = "Another note already says that."
                                        }
                                    }
                                ) { Text("Save") }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                item.text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                editingId = item.id
                                editText = item.text
                                editError = null
                            }) {
                                Icon(OrbitIcons.Edit, contentDescription = "Edit note: ${item.text.take(40)}")
                            }
                            IconButton(onClick = {
                                if (editingId == item.id) editingId = null
                                onRemove(item.id)
                            }) {
                                Icon(OrbitIcons.Delete, contentDescription = "Delete note: ${item.text.take(40)}")
                            }
                        }
                        TextButton(onClick = { onSetPinned(item.id, !item.pinned) }, modifier = Modifier.padding(horizontal = space.s)) {
                            Text(stringResource(if (item.pinned) R.string.memory_unpin else R.string.memory_pin))
                        }
                        }
                    }
                }
            }
            if (items.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { confirmClear = true }) {
                        Text("Forget everything", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Forget all ${items.size} notes?") },
            text = { Text("This deletes every memory note from this phone. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    editingId = null
                    onClearAll()
                }) { Text("Forget everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}
