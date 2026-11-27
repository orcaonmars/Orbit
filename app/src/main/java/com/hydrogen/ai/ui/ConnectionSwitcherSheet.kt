package com.hydrogen.ai.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.R
import com.hydrogen.ai.model.LocalServerPresets
import com.hydrogen.ai.model.ModelDisplayName
import com.hydrogen.ai.model.ProviderPresets
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.ReasoningMode
import com.hydrogen.ai.model.RelayConfig
import com.hydrogen.ai.model.SavedConnection
import com.hydrogen.ai.model.SavedConnections
import com.hydrogen.ai.ui.components.OrbitSearchField
import com.hydrogen.ai.ui.theme.OrbitTheme

/**
 * Opened by tapping the model name in the header. Two levels, like a desktop model menu:
 * 1. Favorites, the three most recent models and the list of providers.
 * 2. Every saved model for one provider.
 * Rows stay quiet: a name, where it runs and a check on the one in use. Favorite, rename and delete sit behind one
 * "more" button per row, and a provider has its own "more" button to delete it with all its models, so the sheet
 * reads as a list of choices rather than a toolbar. Deleting always asks first. Deleting what is in use switches to
 * another saved model first, and is refused when nothing else is saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSwitcherSheet(
    current: RelayConfig,
    connections: List<SavedConnection>,
    onSwitch: (SavedConnection) -> Unit,
    /** Confirmed: drop one saved model. [replacement] is set when it was in use and Orbit must switch first. */
    onRemove: (connection: SavedConnection, replacement: SavedConnection?) -> Unit,
    /** Confirmed: drop every saved model on one provider and the key saved for it. */
    onRemoveProvider: (models: List<SavedConnection>, replacement: SavedConnection?) -> Unit,
    onRename: (SavedConnection) -> Unit,
    onPin: (SavedConnection) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val orbit = OrbitTheme.colors
    val space = OrbitTheme.space
    var query by rememberSaveable { mutableStateOf("") }
    var selectedServer by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = remember(connections, current) { orderProviders(groupByServer(connections), current) }
    var deletingModel by remember { mutableStateOf<SavedConnection?>(null) }
    var deletingProvider by remember { mutableStateOf<Pair<String, List<SavedConnection>>?>(null) }
    val chosen = groups.firstOrNull { it.first == selectedServer }
    val searchable = chosen?.second ?: connections
    val matches = remember(searchable, query) { searchConnections(searchable, query) }
    val serverOf = remember(groups) {
        groups.flatMap { (server, models) -> models.map { it.key to server } }.toMap()
    }
    val goBack = { selectedServer = null; query = "" }
    ModalBottomSheet(
        onDismissRequest = { if (chosen != null) goBack() else onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.l)
                .padding(bottom = space.xxl)
                .animateContentSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                AnimatedVisibility(visible = chosen != null) {
                    IconButton(onClick = goBack) {
                        Icon(
                            OrbitIcons.ArrowBack,
                            contentDescription = stringResource(R.string.switcher_back_services),
                            tint = orbit.text,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    chosen?.let { providerName(it.first, isLocalConnection(it.second.first())) }
                        ?: stringResource(R.string.switcher_title),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .semantics { heading() }
                )
                if (chosen != null) {
                    ProviderMenu(
                        name = providerName(chosen.first, isLocalConnection(chosen.second.first())),
                        onDelete = { deletingProvider = chosen }
                    )
                }
            }
            if (connections.isEmpty()) {
                Text(
                    "Models you chat with show up here, so you can go back to any of them in one tap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = orbit.muted,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = space.m)
                )
            }
            if (searchable.size > 5 || query.isNotEmpty()) {
                Spacer(Modifier.height(space.s))
                OrbitSearchField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.switcher_search),
                    clearDescription = stringResource(R.string.switcher_clear_search)
                )
            }
            if (searchable.isNotEmpty() && matches.isEmpty()) {
                Text(
                    stringResource(R.string.switcher_no_matches),
                    style = MaterialTheme.typography.bodyMedium,
                    color = orbit.muted,
                    modifier = Modifier.padding(vertical = space.xxl, horizontal = space.xs)
                )
            }
            val showModels: @Composable (List<SavedConnection>, Boolean) -> Unit = { models, withServer ->
                models.forEach { connection ->
                    val selected = SavedConnections.isCurrent(connection, current)
                    ModelRow(
                        connection = connection,
                        selected = selected,
                        caption = if (withServer) {
                            serverOf[connection.key]?.let { providerName(it, isLocalConnection(connection)) }
                        } else null,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (!selected) onSwitch(connection)
                            onDismiss()
                        },
                        onRemove = { deletingModel = connection },
                        onRename = { onRename(connection) },
                        onPin = { onPin(connection) }
                    )
                }
            }
            AnimatedContent(
                targetState = chosen?.first,
                transitionSpec = {
                    val forward = targetState != null
                    (slideInHorizontally(tween(220)) { w -> if (forward) w / 8 else -w / 8 } + fadeIn(tween(220)))
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "switcher-level"
            ) { server ->
                Column(Modifier.fillMaxWidth()) {
                    val level = groups.firstOrNull { it.first == server }
                    if (level != null) {
                        Spacer(Modifier.height(space.xs))
                        showModels(SavedConnections.favoritesFirst(matches.filter { it in level.second }), false)
                    } else if (query.isNotBlank()) {
                        groups.forEach { (name, models) ->
                            val found = models.filter { it in matches }
                            if (found.isNotEmpty()) {
                                ServerLabel(name, isLocalConnection(found.first()))
                                showModels(found, false)
                            }
                        }
                    } else if (connections.isNotEmpty()) {
                        val favorites = connections.filter { it.pinned }
                        if (favorites.isNotEmpty()) {
                            SectionLabel(stringResource(R.string.switcher_favorites))
                            showModels(favorites, true)
                        }
                        val recent = connections.filterNot { it.pinned }.take(3)
                        if (recent.isNotEmpty()) {
                            SectionLabel(stringResource(R.string.switcher_recent))
                            showModels(recent, true)
                        }
                        SectionLabel(stringResource(R.string.switcher_services))
                        groups.forEach { (name, models) ->
                            ProviderRow(
                                server = name,
                                local = isLocalConnection(models.first()),
                                count = models.size,
                                favorites = models.count { it.pinned },
                                active = models.any { SavedConnections.isCurrent(it, current) },
                                onClick = { selectedServer = name; query = "" },
                                onDelete = { deletingProvider = name to models }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(space.s))
            HorizontalDivider(thickness = 1.dp, color = orbit.line)
            Spacer(Modifier.height(space.xs))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onOpenSettings)
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 12.dp)
            ) {
                Icon(OrbitIcons.Tune, contentDescription = null, tint = orbit.muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Text(
                    stringResource(R.string.switcher_manage),
                    style = MaterialTheme.typography.bodyLarge,
                    color = orbit.text,
                    modifier = Modifier.weight(1f)
                )
                Icon(OrbitIcons.ChevronRight, contentDescription = null, tint = orbit.faint,
                    modifier = Modifier.size(18.dp))
            }
        }
    }

    deletingModel?.let { target ->
        val inUse = SavedConnections.isCurrent(target, current)
        val replacement = if (inUse) SavedConnections.replacementFor(connections, listOf(target)) else null
        val name = displayName(target)
        DeleteDialog(
            title = if (inUse && replacement == null) "Can't delete this model" else "Delete $name?",
            body = deleteModelText(name, inUse, replacement?.let(::displayName)),
            canDelete = !inUse || replacement != null,
            onConfirm = {
                deletingModel = null
                onRemove(target, replacement)
            },
            onDismiss = { deletingModel = null }
        )
    }

    deletingProvider?.let { (server, models) ->
        val inUse = models.any { SavedConnections.isCurrent(it, current) }
        val replacement = if (inUse) SavedConnections.replacementFor(connections, models) else null
        val name = providerName(server, isLocalConnection(models.first()))
        DeleteDialog(
            title = if (inUse && replacement == null) "Can't delete this provider" else "Delete $name?",
            body = deleteProviderText(name, models.size, inUse, replacement?.let(::displayName)),
            canDelete = !inUse || replacement != null,
            onConfirm = {
                deletingProvider = null
                if (selectedServer == server) goBack()
                onRemoveProvider(models, replacement)
            },
            onDismiss = { deletingProvider = null }
        )
    }
}

private fun displayName(connection: SavedConnection): String =
    connection.label.ifBlank { ModelDisplayName.friendly(connection.modelId) }

/** What deleting one model does, in words. Plain text so tests can read it. */
internal fun deleteModelText(name: String, inUse: Boolean, replacement: String?): String = when {
    inUse && replacement == null ->
        "$name is the only saved model, and Orbit needs one to chat with. Add another model first, then delete this one."
    inUse ->
        "$name is in use, so Orbit will switch to $replacement first. Your chats and the saved key stay. You can add " +
            "it again from Models and providers."
    else ->
        "$name leaves this list. Your chats and the saved key stay. You can add it again from Models and providers."
}

/** What deleting a whole provider does, in words. */
internal fun deleteProviderText(name: String, count: Int, inUse: Boolean, replacement: String?): String {
    if (inUse && replacement == null) {
        return "Every saved model is on $name, and Orbit needs one to chat with. Add a model from another provider " +
            "first, then delete this one."
    }
    val models = if (count == 1) "its saved model" else "its $count saved models"
    val switch = if (inUse) " Orbit will switch to $replacement first." else ""
    return "This removes $models and any API key saved for $name on this phone. Your chats stay.$switch"
}

/** The provider in use comes first; the rest keep most recent first. */
internal fun orderProviders(
    groups: List<Pair<String, List<SavedConnection>>>,
    current: RelayConfig
): List<Pair<String, List<SavedConnection>>> {
    val active = groups.filter { (_, models) -> models.any { SavedConnections.isCurrent(it, current) } }
    return active + groups.filterNot { it in active }
}

@Composable
private fun DeleteDialog(
    title: String,
    body: String,
    canDelete: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            if (canDelete) {
                TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            } else {
                TextButton(onClick = onDismiss) { Text("OK") }
            }
        },
        dismissButton = if (canDelete) {
            { TextButton(onClick = onDismiss) { Text("Cancel") } }
        } else null
    )
}

@Composable
private fun ProviderMenu(name: String, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(OrbitIcons.MoreHorizontal, contentDescription = "Options for $name", tint = OrbitTheme.colors.muted,
                modifier = Modifier.size(20.dp))
        }
        OrbitMenu(expanded = open, onDismissRequest = { open = false }) {
            DeleteMenuItem("Delete provider", "Delete $name") { open = false; onDelete() }
        }
    }
}

@Composable
private fun DeleteMenuItem(label: String, description: String, onClick: () -> Unit) {
    OrbitMenuItem(
        text = { Text(label, color = MaterialTheme.colorScheme.error) },
        leadingIcon = {
            Icon(OrbitIcons.Delete, contentDescription = description, tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp))
        },
        onClick = onClick
    )
}

/** A private address reads as the device it is, not as an IP the owner has to decode. */
private fun providerName(server: String, local: Boolean): String = if (local) "Local server" else server

/**
 * Saved connections grouped by the server they run on. Servers keep the order of their most recently used model and
 * models keep their order inside a server, so the list still reads most recent first.
 */
internal fun searchConnections(connections: List<SavedConnection>, query: String): List<SavedConnection> {
    fun fold(value: String): String = value.lowercase().replace(Regex("[\\s_-]+"), " ").trim()
    val terms = fold(query).split(' ').filter { it.isNotEmpty() }
    if (terms.isEmpty()) return connections
    return connections.filter { connection ->
        val haystack = fold(listOf(
            connection.label,
            connection.modelId,
            ModelDisplayName.friendly(connection.modelId),
            SavedConnections.serverLabel(connection)
        ).joinToString(" "))
        terms.all { it in haystack }
    }
}

/** Group by actual route and endpoint, not a display hostname that could conceal separate services. */
internal fun groupByServer(connections: List<SavedConnection>): List<Pair<String, List<SavedConnection>>> {
    val endpoints = connections.groupBy { connection ->
        connection.route to if (connection.route == ProviderRoute.GOOGLE_AI_STUDIO_DIRECT) {
            "google-direct"
        } else ProviderPresets.normalizeBaseUrl(connection.baseUrl)
    }.values.toList()
    val labels = endpoints.map { SavedConnections.serverLabel(it.first()) }
    val labelCounts = labels.groupingBy { it }.eachCount()
    val seen = mutableMapOf<String, Int>()
    return endpoints.mapIndexed { index, models ->
        val label = labels[index]
        val occurrence = (seen[label] ?: 0) + 1
        seen[label] = occurrence
        val distinctLabel = if ((labelCounts[label] ?: 0) > 1) "$label ($occurrence)" else label
        distinctLabel to models
    }
}

private fun isLocalConnection(connection: SavedConnection): Boolean =
    connection.route != ProviderRoute.GOOGLE_AI_STUDIO_DIRECT &&
        LocalServerPresets.isPrivateServer(connection.baseUrl)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = OrbitTheme.colors.muted,
        modifier = Modifier
            .padding(start = 12.dp, top = OrbitTheme.space.l, bottom = OrbitTheme.space.xs)
            .semantics { heading() }
    )
}

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
            if (local) OrbitIcons.Computer else OrbitIcons.Cloud,
            contentDescription = if (local) "On your network" else "Remote server",
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
private fun ProviderRow(
    server: String,
    local: Boolean,
    count: Int,
    favorites: Int,
    active: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val orbit = OrbitTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(orbit.surface2)
        ) {
            Icon(
                if (local) OrbitIcons.Computer else OrbitIcons.Cloud,
                contentDescription = if (local) "On your network" else "Remote server",
                tint = orbit.muted,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                providerName(server, local),
                style = MaterialTheme.typography.bodyLarge,
                color = orbit.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val detail = listOfNotNull(
                server.takeIf { local },
                pluralStringResource(R.plurals.switcher_model_count, count, count),
                (if (favorites == 1) "1 favorite" else "$favorites favorites").takeIf { favorites > 0 },
                stringResource(R.string.switcher_in_use).takeIf { active }
            ).joinToString(", ")
            Text(detail, style = MaterialTheme.typography.labelMedium, color = orbit.muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ProviderMenu(name = providerName(server, local), onDelete = onDelete)
    }
}

@Composable
private fun ModelRow(
    connection: SavedConnection,
    selected: Boolean,
    caption: String?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit
) {
    val orbit = OrbitTheme.colors
    val name = ModelDisplayName.friendly(connection.modelId)
    var menuOpen by remember { mutableStateOf(false) }
    // The sheet sits on the sidebar color. In Paper, surface-2 is within a shade of it, so the selected row uses the
    // lighter surface there; in Night and Midnight surface-2 is the step up.
    val selectedBand = if (orbit.dark) orbit.surface2 else orbit.surface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) selectedBand else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(start = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    connection.label.ifBlank { name },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = orbit.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (connection.pinned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(OrbitIcons.StarFilled, contentDescription = "Favorite", tint = orbit.faint,
                        modifier = Modifier.size(12.dp))
                }
                if (connection.needsKey) {
                    Spacer(Modifier.width(6.dp))
                    Icon(OrbitIcons.Key, contentDescription = "Uses a key", tint = orbit.faint,
                        modifier = Modifier.size(12.dp))
                }
            }
            val detail = listOfNotNull(
                name.takeIf { connection.label.isNotBlank() },
                caption,
                connection.reasoningMode.label.takeIf { connection.reasoningMode != ReasoningMode.AUTO }
            ).joinToString(", ")
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.labelMedium, color = orbit.muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selected) {
            Icon(OrbitIcons.Check, contentDescription = "In use", tint = orbit.accent,
                modifier = Modifier.size(18.dp))
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(OrbitIcons.MoreHorizontal, contentDescription = "Options for $name", tint = orbit.muted,
                    modifier = Modifier.size(20.dp))
            }
            OrbitMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                OrbitMenuItem(
                    text = { Text(if (connection.pinned) "Remove from favorites" else "Add to favorites") },
                    leadingIcon = {
                        Icon(
                            if (connection.pinned) OrbitIcons.StarFilled else OrbitIcons.StarOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = { menuOpen = false; onPin() }
                )
                OrbitMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = {
                        Icon(OrbitIcons.Edit, contentDescription = "Rename $name", modifier = Modifier.size(18.dp))
                    },
                    onClick = { menuOpen = false; onRename() }
                )
                DeleteMenuItem("Delete", "Delete $name") { menuOpen = false; onRemove() }
            }
        }
    }
}
