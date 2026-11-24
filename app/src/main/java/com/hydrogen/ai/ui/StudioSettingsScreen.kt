package com.hydrogen.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hydrogen.ai.R
import com.hydrogen.ai.model.SettingsSearch
import com.hydrogen.ai.ui.components.OrbitDivider
import com.hydrogen.ai.ui.components.OrbitEmptyState
import com.hydrogen.ai.ui.components.OrbitListRow
import com.hydrogen.ai.ui.components.OrbitSearchField
import com.hydrogen.ai.ui.theme.OrbitTheme

private data class SettingsDestination(
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector? = null,
    val open: () -> Unit,
    val launchesPicker: Boolean = false
)

/** Searchable settings hub. All entries invoke existing pages, not placeholder actions. */
@Composable
fun StudioSettingsScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onPersonal: () -> Unit,
    onMemory: () -> Unit,
    onModels: () -> Unit,
    onAppearance: (() -> Unit)?,
    onPrivacy: () -> Unit,
    onCompanion: () -> Unit,
    onAbout: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onExportMarkdown: () -> Unit,
    onDismiss: () -> Unit
) {
    val space = OrbitTheme.space
    val colors = OrbitTheme.colors
    val preferences = listOfNotNull(
        SettingsDestination(stringResource(R.string.studio_settings_personal),
            stringResource(R.string.studio_settings_personal_hint), OrbitIcons.Edit, onPersonal),
        SettingsDestination(stringResource(R.string.studio_settings_memory),
            stringResource(R.string.studio_settings_memory_hint), open = onMemory),
        if (onAppearance != null) SettingsDestination(stringResource(R.string.studio_settings_appearance),
            stringResource(R.string.studio_settings_appearance_hint), OrbitIcons.Tune, onAppearance) else null
    )
    val sections = listOf(
        stringResource(R.string.studio_settings_preferences) to preferences,
        stringResource(R.string.studio_settings_connections) to listOf(
            SettingsDestination(stringResource(R.string.studio_settings_models),
                stringResource(R.string.studio_settings_models_hint), OrbitIcons.Key, onModels),
            SettingsDestination(stringResource(R.string.studio_settings_companion), icon = OrbitIcons.Computer, open = onCompanion)
        ),
        stringResource(R.string.studio_settings_data) to listOf(
            SettingsDestination(stringResource(R.string.studio_settings_privacy),
                stringResource(R.string.studio_settings_privacy_hint), open = onPrivacy),
            SettingsDestination(stringResource(R.string.studio_settings_backup),
                stringResource(R.string.studio_settings_backup_hint), open = onBackup, launchesPicker = true),
            SettingsDestination(stringResource(R.string.studio_settings_restore),
                stringResource(R.string.studio_settings_restore_hint), open = onRestore, launchesPicker = true),
            SettingsDestination(stringResource(R.string.studio_settings_export),
                stringResource(R.string.studio_settings_export_hint), open = onExportMarkdown, launchesPicker = true)
        ),
        stringResource(R.string.studio_settings_information) to listOf(
            SettingsDestination(stringResource(R.string.studio_settings_about), open = onAbout)
        )
    ).map { (section, rows) ->
        section to rows.filter { SettingsSearch.matches(query, section, it.title, it.subtitle.orEmpty()) }
    }.filter { it.second.isNotEmpty() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = colors.bg, contentColor = colors.text) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = space.l, vertical = space.s),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.studio_settings), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).semantics { heading() })
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.studio_settings_done)) }
                }
                OrbitSearchField(query, onQueryChange, stringResource(R.string.studio_settings_search),
                    stringResource(R.string.studio_settings_clear_search),
                    modifier = Modifier.widthIn(max = 640.dp).padding(horizontal = space.l, vertical = space.s))
                Column(Modifier.weight(1f).widthIn(max = 640.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(space.l),
                    verticalArrangement = Arrangement.spacedBy(space.xl)) {
                    if (sections.isEmpty()) {
                        OrbitEmptyState(stringResource(R.string.studio_settings_no_results),
                            stringResource(R.string.studio_settings_search_hint))
                    }
                    sections.forEach { (title, rows) ->
                        Column(verticalArrangement = Arrangement.spacedBy(space.s)) {
                            Text(title, color = colors.muted, style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = space.xs).semantics { heading() })
                            Surface(shape = OrbitTheme.shapes.l, color = colors.surface,
                                modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    rows.forEachIndexed { index, destination ->
                                        if (index > 0) OrbitDivider(Modifier.padding(horizontal = space.l))
                                        OrbitListRow(destination.title, destination.open,
                                            subtitle = destination.subtitle, leadingIcon = destination.icon, showChevron = !destination.launchesPicker)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
