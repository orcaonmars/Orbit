package com.hydrogen.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.model.PrivacyFacts
import com.hydrogen.ai.model.PrivacySummary

/** The word the user must type before "Delete all local data" runs. */
const val DELETE_ALL_CONFIRM_WORD = "DELETE"

/**
 * Privacy & data: what Orbit keeps on the phone, where messages go, and a way to wipe everything.
 * All wording comes from [PrivacySummary], which is unit-tested.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySheet(
    facts: PrivacyFacts,
    onDeleteAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirming by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Privacy and data", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })

            PrivacySummary.sections(facts).forEach { section ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            section.title,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.semantics { heading() }
                        )
                        section.lines.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Text(
                "Delete all local data",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp).semantics { heading() }
            )
            Text(
                "Removes every chat, attachment, memory note, saved prompt, persona and saved connection from this " +
                    "phone, and forgets your server settings, saved API keys and any PC pairing. Your appearance " +
                    "settings stay.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                shape = com.hydrogen.ai.ui.OrbitButtonShape,
                onClick = { typed = ""; confirming = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Delete all local data") }
        }
    }

    if (confirming) {
        val matches = typed.trim() == DELETE_ALL_CONFIRM_WORD
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Delete everything on this phone?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This can't be undone. Export any chats you want to keep first. Type $DELETE_ALL_CONFIRM_WORD to confirm.")
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it.take(20) },
                        singleLine = true,
                        label = { Text("Type $DELETE_ALL_CONFIRM_WORD") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = matches,
                    onClick = {
                        confirming = false
                        onDeleteAll()
                    }
                ) { Text("Delete everything", color = if (matches) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } }
        )
    }
}
