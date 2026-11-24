package com.hydrogen.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.model.Personal
import com.hydrogen.ai.model.PersonalProfile

/** Copy for the Personal sheet, kept together so it reads as one voice. */
internal object PersonalCopy {
    const val TITLE = "Personal"
    const val EXPLAINER =
        "Orbit adds this to every chat, temporary ones too. It stays on this phone. Leave a box empty to skip it."
    const val ABOUT_HINT = "Your work, what you're learning, anything that helps"
    const val STYLE_HINT = "Or write your own"
    const val QUICK_PICKS = "Quick picks"
    const val CLEAR = "Clear all"
    const val SAVE = "Save"
}

/**
 * Settings → Personal: what Orbit calls you, what it should know about you and how it should reply. Nothing is
 * saved until Save, so backing out of the sheet leaves the old profile as it was.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonalSheet(
    profile: PersonalProfile,
    onSave: (PersonalProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var about by remember { mutableStateOf(profile.about) }
    var style by remember { mutableStateOf(profile.replyStyle) }

    val draft = PersonalProfile(name = name, about = about, replyStyle = style)
    val changed = Personal.clean(draft) != Personal.clean(profile)
    val anything = !draft.isEmpty

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
            Text(
                PersonalCopy.TITLE,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                PersonalCopy.EXPLAINER,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = name,
                // One line, and no more than the limit: a pasted newline becomes a space.
                onValueChange = { name = it.replace('\n', ' ').take(Personal.MAX_NAME) },
                label = { Text(Personal.NAME_LABEL) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = about,
                onValueChange = { about = it.take(Personal.MAX_FIELD) },
                label = { Text(Personal.ABOUT_LABEL) },
                placeholder = { Text(PersonalCopy.ABOUT_HINT) },
                minLines = 3,
                maxLines = 8,
                supportingText = { FieldCount(about.length) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                Personal.STYLE_LABEL,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                PersonalCopy.QUICK_PICKS,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                val colors = MaterialTheme.colorScheme
                Personal.STYLE_CHIPS.forEach { chip ->
                    val selected = Personal.hasChip(style, chip)
                    // The theme's secondaryContainer sits close to the sheet colour, so a picked
                    // chip used to look like it had lost its outline. Accent fill, accent edge and
                    // a check mark make "on" read at a glance, and not by colour alone.
                    FilterChip(
                        selected = selected,
                        onClick = { style = Personal.toggleChip(style, chip) },
                        label = { Text(chip.label) },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    OrbitIcons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.primaryContainer,
                            selectedLabelColor = colors.onPrimaryContainer,
                            selectedLeadingIconColor = colors.primary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            selectedBorderColor = colors.primary,
                            selectedBorderWidth = 1.dp
                        )
                    )
                }
            }
            OutlinedTextField(
                value = style,
                onValueChange = { style = it.take(Personal.MAX_FIELD) },
                placeholder = { Text(PersonalCopy.STYLE_HINT) },
                minLines = 3,
                maxLines = 8,
                supportingText = { FieldCount(style.length) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        name = ""
                        about = ""
                        style = ""
                    },
                    enabled = anything
                ) { Text(PersonalCopy.CLEAR) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onSave(Personal.clean(draft)) },
                    enabled = changed
                ) { Text(PersonalCopy.SAVE) }
            }
        }
    }
}

@Composable
private fun FieldCount(length: Int) {
    Text("$length / ${Personal.MAX_FIELD}")
}
