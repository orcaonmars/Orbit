package com.hydrogen.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.model.LocalServerPresets
import com.hydrogen.ai.model.ModelDisplayName
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import com.hydrogen.ai.model.SavedConnection
import com.hydrogen.ai.model.SavedConnections
import com.hydrogen.ai.ui.theme.OrbitTheme

/**
 * Opened by tapping the model name (docs/ORBIT_DESIGN.md, "Header"): the models you have used, grouped under the
 * server they run on, with a check on the one in use and "Manage connections" at the bottom. At most
 * [SavedConnections.MAX] models are kept, so the list never needs the search field the desktop menu has.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSwitcherSheet(
    current: RelayConfig,
    connections: List<SavedConnection>,
    onSwitch: (SavedConnection) -> Unit,
    onRemove: (SavedConnection) -> Unit,
    onRename: (SavedConnection) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val orbit = OrbitTheme.colors
    val groups = remember(connections) { groupByServer(connections) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Switch model",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .semantics { heading() }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (connections.isEmpty()) {
                    "Models you chat with show up here, so you can go back to any of them in one tap."
                } else {
                    "Recently used. Keys are never copied between servers."
                },
                style = MaterialTheme.typography.bodySmall,
                color = orbit.muted,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            groups.forEach { (server, models) ->
                val first = models.first()
                val local = first.route != ProviderRoute.GOOGLE_AI_STUDIO_DIRECT &&
                    LocalServerPresets.isPrivateServer(first.baseUrl)
                ServerLabel(server = server, local = local)
                models.forEach { connection ->
                    val selected = SavedConnections.isCurrent(connection, current)
                    ModelRow(
                        connection = connection,
                        selected = selected,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (!selected) onSwitch(connection)
                            onDismiss()
                        },
                        onRemove = if (selected) null else ({ onRemove(connection) }),
                        onRename = { onRename(connection) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = orbit.line)
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = onOpenSettings)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 12.dp)
            ) {
                Icon(Icons.Outlined.Tune, contentDescription = null, tint = orbit.muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("Manage connections", style = MaterialTheme.typography.bodyMedium, color = orbit.text)
            }
        }
    }
}

/**
 * Saved connections grouped by the server they run on. Servers keep the order of their most recently used model and
 * models keep their order inside a server, so the list still reads most recent first.
 */
internal fun groupByServer(connections: List<SavedConnection>): List<Pair<String, List<SavedConnection>>> =
    connections.groupBy { SavedConnections.serverLabel(it) }.toList()

@Composable
private fun ServerLabel(server: String, local: Boolean) {
    val orbit = OrbitTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 4.dp)
            .semantics(mergeDescendants = true) { heading() }
    ) {
        Icon(
            if (local) Icons.Outlined.Computer else Icons.Outlined.Cloud,
            contentDescription = if (local) "On your network" else "Online",
            tint = orbit.faint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            server,
            style = MaterialTheme.typography.labelMedium,
            color = orbit.faint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ModelRow(
    connection: SavedConnection,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    onRename: () -> Unit
) {
    val orbit = OrbitTheme.colors
    val name = ModelDisplayName.of(connection.modelId)
    // The sheet sits on the sidebar color. In Paper, surface-2 is within a shade of it, so the selected row uses the
    // lighter surface there; in Night and Midnight surface-2 is the step up.
    val selectedBand = if (orbit.dark) orbit.surface2 else orbit.surface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) selectedBand else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(start = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                connection.label.ifBlank { name },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = orbit.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (connection.label.isNotBlank()) {
                Text(name, style = MaterialTheme.typography.labelSmall,
                    color = orbit.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (connection.needsKey) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.Key, contentDescription = "Uses a key", tint = orbit.faint, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "In use", tint = orbit.accent,
                modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onRename) {
            Icon(Icons.Outlined.Edit, contentDescription = "Rename $name", tint = orbit.muted,
                modifier = Modifier.size(18.dp))
        }
        if (!selected && onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Close, contentDescription = "Forget $name", tint = orbit.faint,
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}
