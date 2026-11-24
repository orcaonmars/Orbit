package com.hydrogen.ai.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.hydrogen.ai.R
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.data.DocumentImporter
import com.hydrogen.ai.data.TextReadResult
import com.hydrogen.ai.model.Project
import com.hydrogen.ai.model.ProjectImportBudget
import com.hydrogen.ai.model.Projects
import com.hydrogen.ai.ui.theme.OrbitTheme
import java.text.NumberFormat
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** Copy for the project sheet, kept together so it reads as one voice. */
internal object ProjectCopy {
    const val NEW_TITLE = "New project"
    const val EXPLAINER =
        "Chats in a project share its instructions and files. They stay on this phone and go to the model only with this project's chats."
    const val NAME = "Name"
    const val INSTRUCTIONS = "Instructions"
    const val INSTRUCTIONS_HINT = "How Orbit should help here. For example: I'm planning a trip to Japan in April. Keep costs in euros."
    const val FILES = "Files"
    const val ADD_FILES = "Add files"
    const val FILES_EMPTY = "Add notes, specs or reference files. Orbit reads their text: text and code files, PDFs and Word files."
    const val CREATE = "Create project"
    const val DONE = "Done"
    const val DELETE = "Delete project"

    /** Under the project's name on a new chat's start screen: what this chat will use. */
    fun startLine(project: Project): String {
        val files = project.files.size
        val fileText = if (files == 1) "1 file" else "$files files"
        return when {
            project.instructions.isNotBlank() && files > 0 -> "Orbit uses this project's instructions and $fileText."
            project.instructions.isNotBlank() -> "Orbit uses this project's instructions."
            files > 0 -> "Orbit uses this project's $fileText."
            else -> "Add instructions or files to this project to shape its chats."
        }
    }

    fun bannerDescription(name: String) = "Project $name. Open project"
}

/**
 * A quiet pill above a project chat's messages naming its project, so it's clear which instructions and files are
 * in play. Tapping it opens the project.
 */
@Composable
fun ProjectChatBanner(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val orbit = OrbitTheme.colors
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, orbit.line),
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = ProjectCopy.bannerDescription(name)
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Icon(
                    Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = orbit.muted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    color = orbit.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Create or edit a project: name, instructions and reference files. A new project is created with Create; after
 * that the sheet stays open on it so files can be added straight away. For a project that exists, files are added
 * and removed at once, and name and instructions are saved with Done or when the sheet is closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSheet(
    project: Project?,
    chatCount: Int,
    importer: DocumentImporter,
    onCreate: (name: String, instructions: String) -> Unit,
    onSave: (projectId: String, name: String, instructions: String) -> Unit,
    onAddFile: (projectId: String, name: String, text: String, truncated: Boolean) -> Boolean,
    onRemoveFile: (projectId: String, fileId: String) -> Unit,
    onDelete: (projectId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Keyed on the id, so the fields fill in when a just-created project opens here.
    var name by rememberSaveable(project?.id) { mutableStateOf(project?.name.orEmpty()) }
    var instructions by rememberSaveable(project?.id) { mutableStateOf(project?.instructions.orEmpty()) }
    var reading by remember(project?.id) { mutableStateOf(false) }
    var pickerTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDiscard by remember(project?.id) { mutableStateOf(false) }
    var confirmDelete by remember(project?.id) { mutableStateOf(false) }
    val nameOk = Projects.normalizeName(name) != null
    val changed = project != null &&
        (Projects.normalizeName(name) != project.name || Projects.cleanInstructions(instructions) != project.instructions)

    val saveAndClose: () -> Unit = {
        if (reading) {
            Toast.makeText(context, R.string.project_import_in_progress, Toast.LENGTH_SHORT).show()
        } else if ((project == null && (name.isNotBlank() || instructions.isNotBlank())) ||
            (project != null && changed && !nameOk)) {
            confirmDiscard = true
        } else {
            if (project != null && changed && nameOk) onSave(project.id, name, instructions)
            onDismiss()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        val targetId = pickerTargetId
        pickerTargetId = null
        val target = project ?: return@rememberLauncherForActivityResult
        if (targetId != target.id || uris.isEmpty()) return@rememberLauncherForActivityResult
        reading = true
        scope.launch {
            val problems = mutableListOf<String>()
            val budget = ProjectImportBudget(target.files.size, Projects.MAX_FILES)
            val uniqueUris = uris.distinct()
            try {
                for (uri in uniqueUris) {
                    if (!budget.beginRead()) break
                    when (val r = importer.readText(context, uri)) {
                        is TextReadResult.Ok -> {
                            if (r.text.isBlank()) {
                                problems += context.getString(R.string.project_file_empty)
                            } else if (onAddFile(target.id, r.displayName, r.text, r.truncated)) {
                                budget.recordAdded()
                            } else {
                                problems += context.getString(R.string.project_file_add_failed)
                            }
                        }
                        is TextReadResult.Failure -> problems += r.reason
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                problems += context.getString(R.string.project_file_add_failed)
            } finally {
                reading = false
            }
            val unprocessed = uniqueUris.size - budget.attempted
            val summary = buildList {
                add(context.getString(R.string.project_import_added, budget.added))
                if (problems.isNotEmpty()) add(context.getString(R.string.project_import_failed_count, problems.size, problems.first()))
                if (unprocessed > 0) {
                    add(context.getString(
                        if (budget.full) R.string.project_import_full_count else R.string.project_import_unprocessed_count,
                        unprocessed
                    ))
                }
            }.joinToString("\n")
            Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.project_discard_title)) },
            text = { Text(stringResource(R.string.project_discard_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onDismiss() }) {
                    Text(stringResource(R.string.project_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.project_keep_editing)) }
            }
        )
    }

    if (confirmDelete && project != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this project?") },
            text = {
                Text(
                    if (chatCount > 0) {
                        "Its ${chatCount} chat${if (chatCount == 1) "" else "s"} move back to your chat list. " +
                            "Its instructions and files are deleted from this phone."
                    } else {
                        "Its instructions and files are deleted from this phone."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(project.id)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    ModalBottomSheet(
        onDismissRequest = saveAndClose,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { next ->
                if (next != SheetValue.Hidden) true
                else if (reading) false
                else if ((project == null && (name.isNotBlank() || instructions.isNotBlank())) ||
                    (project != null && changed && !nameOk)) {
                    confirmDiscard = true
                    false
                } else true
            }
        ),
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
                project?.name ?: ProjectCopy.NEW_TITLE,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                ProjectCopy.EXPLAINER,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.replace('\n', ' ').take(Projects.MAX_NAME) },
                label = { Text(ProjectCopy.NAME) },
                singleLine = true,
                isError = name.isNotEmpty() && !nameOk,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = instructions,
                onValueChange = { instructions = it.take(Projects.MAX_INSTRUCTIONS) },
                label = { Text(ProjectCopy.INSTRUCTIONS) },
                placeholder = { Text(ProjectCopy.INSTRUCTIONS_HINT) },
                minLines = 3,
                maxLines = 10,
                supportingText = {
                    val numbers = remember { NumberFormat.getIntegerInstance() }
                    Text("${numbers.format(instructions.length)} / ${numbers.format(Projects.MAX_INSTRUCTIONS)}")
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            if (project != null) {
                ProjectFilesSection(
                    project = project,
                    reading = reading,
                    onAdd = {
                        pickerTargetId = project.id
                        picker.launch(arrayOf("*/*"))
                    },
                    onRemove = { fileId -> onRemoveFile(project.id, fileId) }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (project != null) {
                    TextButton(onClick = { confirmDelete = true }, enabled = !reading) {
                        Text(ProjectCopy.DELETE, color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (project == null) {
                    Button(onClick = { onCreate(name, instructions) }, enabled = nameOk) { Text(ProjectCopy.CREATE) }
                } else {
                    Button(onClick = saveAndClose, enabled = nameOk && !reading) { Text(ProjectCopy.DONE) }
                }
            }
        }
    }
}

@Composable
private fun ProjectFilesSection(
    project: Project,
    reading: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    val numbers = remember { NumberFormat.getIntegerInstance() }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(
            ProjectCopy.FILES,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        if (reading) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            TextButton(onClick = onAdd, enabled = project.files.size < Projects.MAX_FILES) {
                Icon(OrbitIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(ProjectCopy.ADD_FILES)
            }
        }
    }
    if (project.files.isEmpty()) {
        Text(
            ProjectCopy.FILES_EMPTY,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val entries = remember(project.files) { Projects.fileContext(project) }
    val used = entries.sumOf { it.sentChars }
    val stored = entries.sumOf { it.storedChars }
    val budget = Projects.FILES_BUDGET_CHARS
    var previewId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    val preview = entries.firstOrNull { it.file.id == previewId }
    if (preview != null) {
        AlertDialog(
            onDismissRequest = { previewId = null },
            title = { Text(preview.file.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.project_file_context_count, numbers.format(preview.sentChars), numbers.format(preview.storedChars)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (preview.file.truncated) {
                        Text(stringResource(R.string.project_file_import_truncated), style = MaterialTheme.typography.bodySmall)
                    }
                    SelectionContainer {
                        Text(
                            preview.file.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { previewId = null }) { Text(ProjectCopy.DONE) } }
        )
    }
    // A plain meter: a visible track, no gap and no end dot, so a nearly empty project reads as "nearly empty".
    LinearProgressIndicator(
        progress = { (used.toFloat() / budget).coerceIn(0f, 1f) },
        color = if (stored > used) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.outlineVariant,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
    )
    Text(
        if (stored > used) {
            stringResource(R.string.project_context_partial, numbers.format(used), numbers.format(stored))
        } else {
            stringResource(R.string.project_context_full, numbers.format(used), numbers.format(budget))
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (stored > used) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
    entries.forEach { entry ->
        val file = entry.file
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)
            ) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.project_open_file)) { previewId = file.id }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        file.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when {
                            entry.omitted -> stringResource(R.string.project_file_omitted)
                            entry.partial -> stringResource(R.string.project_file_context_count, numbers.format(entry.sentChars), numbers.format(entry.storedChars))
                            else -> stringResource(R.string.project_file_included, numbers.format(entry.sentChars))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onRemove(file.id) }, enabled = !reading) {
                    Icon(OrbitIcons.Close, contentDescription = "Remove ${file.name}", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
