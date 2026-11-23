package com.hydrogen.ai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.data.MAX_BACKUP_KEYS
import com.hydrogen.ai.model.GoogleModelCatalog
import com.hydrogen.ai.model.HydrogenDefaults
import com.hydrogen.ai.model.LocalServerPresets
import com.hydrogen.ai.model.ProviderCapabilities
import com.hydrogen.ai.model.ProviderCatalog
import com.hydrogen.ai.model.PresetGroup
import com.hydrogen.ai.model.ProviderPreset
import com.hydrogen.ai.model.ProviderPresets
import com.hydrogen.ai.model.ProviderReadiness
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import com.hydrogen.ai.model.VerificationState

private const val GOOGLE_KEY_PAGE = "https://aistudio.google.com/apikey"
private const val MAX_MODEL_CHOICES = 40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelaySettingsSheet(
    relayConfig: RelayConfig,
    hasToken: Boolean,
    maskedTokenLabel: String,
    capabilities: ProviderCapabilities,
    readiness: ProviderReadiness,
    isCheckingHealth: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        route: ProviderRoute,
        baseUrl: String,
        modelId: String,
        token: String?,
        maxContextTokens: Int,
        maxImages: Int,
        streamingEnabled: Boolean,
        rememberTokenInKeystore: Boolean,
        webSearchEnabled: Boolean
    ) -> Unit,
    onCheckHealth: () -> Unit,
    onDeleteCredential: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    modelList: ModelListUiState = ModelListUiState(),
    onLoadModels: () -> Unit = {},
    /** Opens the sheet on this provider card (first-run setup); null keeps the saved provider. */
    initialPreset: ProviderPreset? = null,
    /** The PC's home Wi-Fi address (e.g. "192.168.1.20") from the Companion pairing; null hides the Wi-Fi choice. */
    pcWifiHost: String? = null,
    /** Masked backup keys for the saved service, in the order they are tried. */
    backupKeyLabels: List<String> = emptyList(),
    onAddBackupKey: (String) -> Unit = {},
    onRemoveBackupKey: (Int) -> Unit = {}
) {
    // "Load models" saves the draft first, which changes relayConfig. A blank model is stored as the relay
    // placeholder, so detect() alone would flip the sheet from Custom/LM Studio to "Self-hosted relay" and put
    // the placeholder back in the model box. While the sheet is open, the card the user saved with wins.
    var savedWith by remember { mutableStateOf<ProviderPreset?>(null) }
    var savedWithUrl by remember { mutableStateOf<String?>(null) }
    val savedPreset = remember(relayConfig, savedWith, savedWithUrl) {
        savedWith?.takeIf {
            it.route == relayConfig.route && savedWithUrl == relayConfig.effectiveBaseUrl
        } ?: ProviderPresets.detect(relayConfig)
    }
    var preset by remember(relayConfig) { mutableStateOf(savedPreset) }
    var baseUrl by remember(relayConfig) { mutableStateOf(relayConfig.baseUrl) }
    // A blank model is stored as the relay placeholder; don't show that for LM Studio/Ollama/Custom.
    var modelId by remember(relayConfig) {
        mutableStateOf(
            if (savedPreset.defaultModelId.isEmpty() &&
                relayConfig.modelId.trim() == com.hydrogen.ai.model.HydrogenDefaults.PRIMARY_MODEL_ID
            ) "" else relayConfig.modelId
        )
    }
    var maxContextText by remember(relayConfig) { mutableStateOf(relayConfig.maxContextTokensBudget.toString()) }
    var maxImagesText by remember(relayConfig) { mutableStateOf(relayConfig.maxImagesPerRequest.toString()) }
    var streamingEnabled by remember(relayConfig) { mutableStateOf(relayConfig.streamingEnabled) }
    var rememberKey by remember(relayConfig) { mutableStateOf(relayConfig.rememberTokenInKeystore) }
    var webSearch by remember(relayConfig) { mutableStateOf(relayConfig.webSearchEnabled) }
    var tokenInput by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }
    var providerSearch by rememberSaveable { mutableStateOf("") }
    var showAllHosted by rememberSaveable { mutableStateOf(false) }
    var showCatalog by rememberSaveable { mutableStateOf(!relayConfig.isConfigured) }
    var confirmDeleteCredential by remember { mutableStateOf(false) }
    var showBackups by remember { mutableStateOf(false) }
    var backupInput by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current

    fun choose(next: ProviderPreset) {
        showCatalog = false
        focusManager.clearFocus()
        if (next == preset) return
        preset = next
        validationError = null
        tokenInput = ""
        confirmDeleteCredential = false
        val isSaved = next == savedPreset
        baseUrl = if (isSaved) relayConfig.baseUrl else next.defaultBaseUrl
        modelId = if (isSaved) relayConfig.modelId else next.defaultModelId
    }

    LaunchedEffect(initialPreset) { initialPreset?.let { choose(it) } }

    fun persistDraftIfValid(requireModel: Boolean = true): Boolean {
        ProviderPresets.validate(preset, baseUrl, modelId, requireModel)?.let {
            validationError = it
            return false
        }
        val contextTokens = maxContextText.toIntOrNull()
        val imageLimit = maxImagesText.toIntOrNull()
        if (contextTokens == null || contextTokens <= 0 || imageLimit == null || imageLimit <= 0) {
            validationError = "Advanced: enter positive numbers for context tokens and max images."
            showAdvanced = true
            return false
        }
        validationError = null
        savedWith = preset
        val chosenUrl = if (preset.showsBaseUrl) ProviderPresets.normalizeBaseUrl(baseUrl) else preset.defaultBaseUrl
        savedWithUrl = chosenUrl
        onSave(
            preset.route,
            chosenUrl,
            modelId,
            tokenInput.ifBlank { null },
            contextTokens,
            imageLimit,
            streamingEnabled,
            rememberKey,
            webSearch
        )
        tokenInput = ""
        return true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Models & API keys", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Keys are encrypted on this device and only ever sent to the provider you pick.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ConnectionStatusRow(readiness = readiness, isCurrent = preset == savedPreset)
            Text(
                text = "Selected: ${preset.title}" + when {
                    preset.showsBaseUrl && baseUrl.isNotBlank() -> " · ${ProviderPresets.normalizeBaseUrl(baseUrl)}"
                    !preset.showsBaseUrl && preset.hostLabel.isNotBlank() -> " · ${preset.hostLabel}"
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (showCatalog) {
            SectionLabel("Choose a provider")
            OutlinedTextField(
                value = providerSearch,
                onValueChange = { providerSearch = it },
                label = { Text("Find a service") },
                placeholder = { Text("Name, protocol or hostname") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            val groups = ProviderCatalog.grouped(providerSearch)
            if (groups.isEmpty()) {
                Text(
                    "No services match. Choose Custom to connect another compatible endpoint.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { providerSearch = ""; choose(ProviderPreset.CUSTOM) }) {
                    Text("Choose Custom")
                }
            }
            groups.forEach { (group, options) ->
                val shown = if (group == PresetGroup.HOSTED && providerSearch.isBlank() && !showAllHosted) {
                    options.filter { it in ProviderPresets.FEATURED_HOSTED || it == preset }
                } else options
                Text(
                    "${group.label} (${options.size})",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    shown.forEach { option ->
                        ProviderOptionRow(
                            preset = option,
                            selected = option == preset,
                            onClick = { choose(option) }
                        )
                    }
                }
                if (group == PresetGroup.HOSTED && providerSearch.isBlank() &&
                    options.size > ProviderPresets.FEATURED_HOSTED.size) {
                    TextButton(onClick = { showAllHosted = !showAllHosted }) {
                        Text(if (showAllHosted) "Show fewer services" else "See all hosted services")
                    }
                }
            }
            } else {
                OutlinedButton(
                    onClick = { showCatalog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Change provider · ${preset.title}")
                }
            }

            SectionLabel("Connection")
            if (preset.showsBaseUrl) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; validationError = null },
                    label = { Text("Server URL") },
                    placeholder = {
                        Text(if (preset == ProviderPreset.CUSTOM) "https://api.provider.com/v1" else preset.defaultBaseUrl)
                    },
                    supportingText = { Text(urlHint(preset, baseUrl)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                val pcServers = pcServerChoices(preset, pcWifiHost)
                if (pcServers.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pcServers.forEach { (label, url) ->
                            val chosen = ProviderPresets.normalizeBaseUrl(baseUrl).equals(url, ignoreCase = true)
                            FilterChip(
                                selected = chosen,
                                onClick = { baseUrl = url; validationError = null },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
            val onWifi = preset.showsBaseUrl && LocalServerPresets.isWifiServer(ProviderPresets.normalizeBaseUrl(baseUrl))
            // A custom server on this PC (or the emulator's host) may run without a key, and chat works without one.
            val customOnPc = preset == ProviderPreset.CUSTOM &&
                LocalServerPresets.isLocalServer(ProviderPresets.normalizeBaseUrl(baseUrl))
            val keyStatus = when {
                onWifi -> "Not sent over Wi-Fi. Use a server with no key, or connect over USB."
                preset == savedPreset && hasToken -> "Saved ($maskedTokenLabel). Leave blank to keep it."
                preset.keyRequired && !customOnPc -> "Required. Stored encrypted, never logged."
                else -> "Optional for servers on this PC."
            }
            val pastedKeyCount = if (onWifi) 0 else ProviderPresets.splitPastedKeys(tokenInput).size
            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                label = { Text(preset.keyLabel) },
                supportingText = {
                    Text(
                        if (pastedKeyCount > 1) "$pastedKeyCount keys found. The first is your main key, the others " +
                            "become backup keys."
                        else keyStatus
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            if (preset == savedPreset && hasToken) {
                if (confirmDeleteCredential) {
                    Text(
                        if (backupKeyLabels.isEmpty()) "Remove this service's saved key? Other service keys are left alone."
                        else "Remove this service's saved key and its backup keys? Other service keys are left alone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Row {
                        TextButton(onClick = { confirmDeleteCredential = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            onDeleteCredential()
                            tokenInput = ""
                            confirmDeleteCredential = false
                        }) { Text("Remove key") }
                    }
                } else {
                    TextButton(onClick = { confirmDeleteCredential = true }) {
                        Text("Remove saved API key")
                    }
                }
            }
            if (preset == ProviderPreset.GOOGLE_GEMINI) {
                TextButton(onClick = { runCatching { uriHandler.openUri(GOOGLE_KEY_PAGE) } }) {
                    Text("Get a free Gemini API key at aistudio.google.com")
                }
            }
            if (preset == savedPreset && hasToken && !onWifi) {
                TextButton(onClick = { showBackups = !showBackups }) {
                    Text(
                        if (backupKeyLabels.isEmpty()) "Add backup keys"
                        else "Backup keys (${backupKeyLabels.size})"
                    )
                }
                if (showBackups) {
                    Text(
                        "If your main key hits a limit, runs out of credits or stops working, Orbit sends the same " +
                            "message again with the next key here, so the chat carries on as it was. Keys are tried " +
                            "in this order and are only ever sent to this service.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Some services, Google included, don't allow extra keys to get around their limits. Check " +
                            "the service's terms first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    backupKeyLabels.forEachIndexed { index, label ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Backup ${index + 1}: $label",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { onRemoveBackupKey(index) }) { Text("Remove") }
                        }
                    }
                    if (backupKeyLabels.size < MAX_BACKUP_KEYS) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = backupInput,
                                onValueChange = { backupInput = it },
                                label = { Text("Backup keys") },
                                supportingText = { Text("Paste one key, or several, one per line.") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = false,
                                maxLines = 3,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    onAddBackupKey(backupInput)
                                    backupInput = ""
                                },
                                enabled = backupInput.isNotBlank(),
                                shape = RoundedCornerShape(20.dp)
                            ) { Text("Add") }
                        }
                    }
                }
            }

            SectionLabel("Model")
            OutlinedTextField(
                value = modelId,
                onValueChange = { modelId = it; validationError = null },
                label = { Text("Model name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            val isGoogle = preset == ProviderPreset.GOOGLE_GEMINI
            val listUrl = if (isGoogle) HydrogenDefaults.GOOGLE_AI_STUDIO_BASE_URL else ProviderPresets.normalizeBaseUrl(baseUrl)
            val listedForThisUrl = modelList.baseUrl == listUrl && !modelList.isLoading
            if (isGoogle && !(listedForThisUrl && modelList.modelIds.isNotEmpty())) {
                ModelChoiceList(ids = GoogleModelCatalog.suggestions, selectedId = modelId.trim(), onPick = { modelId = it })
            }
            run {
                OutlinedButton(
                    onClick = { if (persistDraftIfValid(requireModel = false)) onLoadModels() },
                    enabled = !modelList.isLoading && (isGoogle || baseUrl.isNotBlank()),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        when {
                            modelList.isLoading -> "Loading models…"
                            isGoogle -> "Load all models for this key"
                            else -> "Load models from server"
                        }
                    )
                }
                if (listedForThisUrl) {
                    modelList.message?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (modelList.modelIds.isEmpty()) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ModelChoiceList(
                        ids = modelList.modelIds.take(MAX_MODEL_CHOICES),
                        selectedId = modelId.trim(),
                        onPick = { modelId = it }
                    )
                    if (modelList.modelIds.size > MAX_MODEL_CHOICES) {
                        Text(
                            text = "Showing $MAX_MODEL_CHOICES of ${modelList.modelIds.size}; type any other name above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            validationError?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (preset.route == ProviderRoute.GOOGLE_AI_STUDIO_DIRECT) {
                Column {
                    SwitchRow("Search the web", webSearch) { webSearch = it }
                    Text(
                        "Gemini looks things up with Google Search and lists its sources under the answer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = { if (persistDraftIfValid()) onDismiss() },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) { Text("Save") }
            OutlinedButton(
                onClick = { if (persistDraftIfValid()) onCheckHealth() },
                enabled = !isCheckingHealth,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) { Text(if (isCheckingHealth) "Testing…" else "Test connection") }

            OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
                Text("Connection diagnostics")
            }
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text("Advanced")
                Icon(
                    imageVector = if (showAdvanced) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null
                )
            }
            if (showAdvanced) {
                OutlinedTextField(
                    value = maxContextText,
                    onValueChange = { maxContextText = it.filter(Char::isDigit) },
                    label = { Text("Maximum context tokens") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = maxImagesText,
                    onValueChange = { maxImagesText = it.filter(Char::isDigit) },
                    label = { Text("Maximum images per request") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                SwitchRow("Stream responses", streamingEnabled) { streamingEnabled = it }
                SwitchRow("Remember key after restart", rememberKey) { rememberKey = it }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Verified capabilities", style = MaterialTheme.typography.titleSmall)
                CapabilityRow("Configured", if (readiness.configured) VerificationState.PASS else VerificationState.FAIL)
                CapabilityRow("Server reachable", readiness.relayReachable)
                CapabilityRow("Key accepted", readiness.authenticated)
                CapabilityRow("Text generation", readiness.textGeneration)
                CapabilityRow("Streaming", readiness.streaming)
                CapabilityRow("Images / attachments", readiness.vision)
                CapabilityRow("Tools", readiness.tools)
                CapabilityRow("PC Work mode", readiness.workMode)
                Text(
                    text = capabilities.sharedQuotaNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * "USB" and "Wi-Fi" one-tap addresses for LM Studio / Ollama. The Wi-Fi one appears only once the phone knows the
 * PC's home Wi-Fi address from the Companion; on the emulator there is nothing to choose.
 */
internal fun pcServerChoices(preset: ProviderPreset, pcWifiHost: String?): List<Pair<String, String>> {
    if (preset != ProviderPreset.LM_STUDIO && preset != ProviderPreset.OLLAMA) return emptyList()
    if (LocalServerPresets.pcHost == LocalServerPresets.EMULATOR_HOST) return emptyList()
    val port = preset.defaultBaseUrl.substringAfterLast(':')
    val wifi = LocalServerPresets.wifiPresets(pcWifiHost).firstOrNull { it.baseUrl.endsWith(":$port") }
        ?: return emptyList()
    return listOf("USB" to preset.defaultBaseUrl, "Wi-Fi" to wifi.baseUrl)
}

internal fun urlHint(preset: ProviderPreset, baseUrl: String = ""): String = when (preset) {
    ProviderPreset.LM_STUDIO, ProviderPreset.OLLAMA -> {
        val port = preset.defaultBaseUrl.substringAfterLast(':')
        when {
            LocalServerPresets.isWifiServer(ProviderPresets.normalizeBaseUrl(baseUrl)) ->
                if (preset == ProviderPreset.OLLAMA) {
                    "Your PC over Wi-Fi: Ollama must listen on the network (OLLAMA_HOST=0.0.0.0). " +
                        "Chats are not encrypted on your home network."
                } else {
                    "Your PC over Wi-Fi: turn on \"Serve on Local Network\" in LM Studio. " +
                        "Chats are not encrypted on your home network."
                }
            LocalServerPresets.pcHost == LocalServerPresets.EMULATOR_HOST ->
                "Emulator address for the PC this emulator runs on."
            else -> "Your PC over USB: run adb reverse tcp:$port tcp:$port on the PC first."
        }
    }
    ProviderPreset.HYDROGEN_RELAY -> "Your relay's https:// address, or this PC's local address."
    ProviderPreset.CUSTOM -> "Paste the address from the service's docs, like https://api.example.com/v1. " +
        "A full .../chat/completions address works too."
    ProviderPreset.CUSTOM_ANTHROPIC -> "The service's HTTPS Anthropic Messages API address."
    else -> ""
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ProviderOptionRow(preset: ProviderPreset, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(preset.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    text = preset.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ModelChoiceList(ids: List<String>, selectedId: String, onPick: (String) -> Unit) {
    if (ids.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            ids.forEachIndexed { index, id ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Surface(onClick = { onPick(id) }, color = Color.Transparent) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = id,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (id == selectedId) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One-line connection summary at the top of the sheet: coloured dot + plain words, details on failure. */
@Composable
private fun ConnectionStatusRow(readiness: ProviderReadiness, isCurrent: Boolean) {
    val failed = readiness.relayReachable == VerificationState.FAIL ||
        readiness.authenticated == VerificationState.FAIL ||
        readiness.textGeneration == VerificationState.FAIL
    val verified = readiness.relayReachable == VerificationState.PASS && !failed
    val (color, label) = when {
        !isCurrent -> MaterialTheme.colorScheme.onSurfaceVariant to "New provider: save, then test."
        !readiness.configured -> Color(0xFFE8A317) to "Not set up yet"
        failed -> MaterialTheme.colorScheme.error to "Connection problem"
        verified -> MaterialTheme.colorScheme.tertiary to "Connected"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to "Saved, not tested yet"
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
            if (isCurrent && (failed || !readiness.configured) && readiness.statusDetail.isNotBlank()) {
                Text(
                    text = readiness.statusDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CapabilityRow(label: String, state: VerificationState) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = state.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = when (state) {
                VerificationState.PASS -> MaterialTheme.colorScheme.tertiary
                VerificationState.FAIL -> MaterialTheme.colorScheme.error
                VerificationState.UNKNOWN, VerificationState.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
