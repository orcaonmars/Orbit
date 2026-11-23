package com.hydrogen.ai.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.companion.CompanionApproval
import com.hydrogen.ai.companion.CompanionDefaults
import com.hydrogen.ai.companion.CompanionPairingInputParser
import com.hydrogen.ai.companion.CompanionPathHelper
import com.hydrogen.ai.companion.CompanionStatusText
import com.hydrogen.ai.companion.WorkActionType
import com.hydrogen.ai.companion.WorkTask
import com.hydrogen.ai.companion.WorkTaskResult

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CompanionPairingSheet(
    companionState: CompanionUiState,
    onDismiss: () -> Unit,
    onCheckHealth: (baseUrl: String, deviceName: String) -> Unit,
    onPair: (baseUrl: String, deviceName: String, pairingId: String, pairingCode: String) -> Unit,
    onRevoke: () -> Unit,
    onClearLocalSession: () -> Unit,
    onRequestApproval: (actionType: WorkActionType, targetPath: String, summary: String) -> Unit = { _, _, _ -> },
    onRefreshApproval: () -> Unit = {},
    onWaitForApproval: () -> Unit = {},
    onStopWaitingForApproval: () -> Unit = {},
    onStartWorkTask: () -> Unit = {},
    onRefreshWorkTask: () -> Unit = {},
    onCancelWorkTask: () -> Unit = {},
    onStageWorkTarget: (actionType: WorkActionType, targetPath: String, offsetBytes: Int?) -> Unit = { _, _, _ -> },
    onClearWorkState: () -> Unit = {},
    onInsertResultIntoDraft: () -> Unit = {}
) {
    var baseUrl by remember(companionState.config) { mutableStateOf(companionState.config.baseUrl) }
    var deviceName by remember(companionState.config) { mutableStateOf(companionState.config.deviceName) }
    var pairingId by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var showEndpointSettings by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pairingBusy = companionState.isCheckingHealth || companionState.isPairing || companionState.isRevoking
    val busy = pairingBusy || companionState.isWorkBusy

    LaunchedEffect(Unit) {
        if (!busy) {
            onCheckHealth(baseUrl, deviceName)
        }
    }

    LaunchedEffect(companionState.session) {
        if (companionState.session != null) {
            pairingId = ""
            pairingCode = ""
            showEndpointSettings = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Windows PC Companion", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            CompanionStatusCard(companionState)
            // FlowRow, not a weighted Row: at large font sizes the second button moves to its own line
            // instead of squeezing "Check connection" until it breaks mid-word.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedButton(
                    onClick = { onCheckHealth(baseUrl, deviceName) },
                    enabled = !busy
                ) {
                    Text(if (companionState.isCheckingHealth) "Checking…" else "Check connection")
                }
                TextButton(
                    onClick = { showEndpointSettings = !showEndpointSettings },
                    enabled = !busy
                ) {
                    Text(if (showEndpointSettings) "Hide address" else "Change address")
                }
            }
            if (showEndpointSettings) {
                Text(
                    text = "Encrypted end to end (X25519 + HKDF-SHA256 + AES-256-GCM). Default address on this device: " +
                        "${CompanionDefaults.defaultUrl()}. On Wi-Fi, turn on Wi-Fi mode on your PC and type the address " +
                        "it shows, like 192.168.1.20.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("PC Companion address") },
                    placeholder = { Text(CompanionDefaults.defaultUrl()) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("Name for this phone") },
                    placeholder = { Text(CompanionDefaults.DEFAULT_DEVICE_NAME) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            HorizontalDivider()
            CompanionPairingActions(
                companionState = companionState,
                pairingBusy = pairingBusy,
                pairingId = pairingId,
                pairingCode = pairingCode,
                onPairingIdChange = { raw ->
                    val parsed = CompanionPairingInputParser.normalize(raw, pairingCode)
                    if (parsed.pairingCode != pairingCode && pairingCode.isBlank()) {
                        pairingId = parsed.pairingId
                        pairingCode = parsed.pairingCode
                    } else {
                        pairingId = raw
                    }
                },
                onPairingCodeChange = { raw ->
                    val parsed = CompanionPairingInputParser.normalize(pairingId, raw)
                    if (parsed.pairingCode != raw && parsed.pairingCode.isNotBlank()) {
                        pairingCode = parsed.pairingCode
                    } else {
                        pairingCode = raw
                    }
                },
                onPair = {
                    onPair(baseUrl, deviceName, pairingId, pairingCode)
                },
                onRevoke = onRevoke,
                onClearLocalSession = onClearLocalSession
            )
            if (companionState.session != null) {
                HorizontalDivider()
                CompanionWorkSection(
                    companionState = companionState,
                    pairingBusy = pairingBusy,
                    busy = busy,
                    onRequestApproval = onRequestApproval,
                    onRefreshApproval = onRefreshApproval,
                    onWaitForApproval = onWaitForApproval,
                    onStopWaitingForApproval = onStopWaitingForApproval,
                    onStartWorkTask = onStartWorkTask,
                    onRefreshWorkTask = onRefreshWorkTask,
                    onCancelWorkTask = onCancelWorkTask,
                    onStageWorkTarget = onStageWorkTarget,
                    onClearWorkState = onClearWorkState,
                    onInsertResultIntoDraft = onInsertResultIntoDraft
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CompanionStatusCard(companionState: CompanionUiState) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val session = companionState.session
            val health = companionState.health
            val paired = session != null
            Text(
                text = CompanionStatusText.headline(paired),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (paired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            CompanionStatusText.summary(paired, health).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
            CompanionStatusText.safetyNote(health)?.let { note ->
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CompanionStatusText.safetyWarning(health)?.let { warning ->
                Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            companionState.statusMessage?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            companionState.errorMessage?.let { err ->
                Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            val details = CompanionStatusText.technicalLines(session, health)
            if (details.isNotEmpty()) {
                var showDetails by rememberSaveable { mutableStateOf(false) }
                TextButton(
                    onClick = { showDetails = !showDetails },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    Text(if (showDetails) "Hide technical details" else "Technical details")
                }
                if (showDetails) {
                    details.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompanionPairingActions(
    companionState: CompanionUiState,
    pairingBusy: Boolean,
    pairingId: String,
    pairingCode: String,
    onPairingIdChange: (String) -> Unit,
    onPairingCodeChange: (String) -> Unit,
    onPair: () -> Unit,
    onRevoke: () -> Unit,
    onClearLocalSession: () -> Unit
) {
    if (companionState.session == null) {
        var revealCode by remember { mutableStateOf(false) }
        val normalized = CompanionPairingInputParser.normalize(pairingId, pairingCode)
        Text(
            text = "Pair with your PC",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "On your PC, open Orbit PC Companion and click Generate pairing code. Type the pairing ID and " +
                "code it shows. Each code works once, for a short time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = pairingId,
            onValueChange = onPairingIdChange,
            label = { Text("Pairing ID") },
            placeholder = { Text("Paste the ID, or the whole pairing text") },
            singleLine = true,
            enabled = !pairingBusy,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = pairingCode,
            onValueChange = onPairingCodeChange,
            label = { Text("Pairing code") },
            singleLine = true,
            enabled = !pairingBusy,
            visualTransformation = if (revealCode) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { revealCode = !revealCode }, enabled = !pairingBusy) {
                    Text(if (revealCode) "Hide" else "Show")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = onPair,
            enabled = !pairingBusy && normalized.pairingId.isNotBlank() && normalized.pairingCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            // "Pair PC Companion" is matched by the Hydrogen-QA pairing scripts; keep it in step with them.
            Text(if (companionState.isPairing) "Pairing…" else "Pair PC Companion")
        }
    } else {
        Text(
            text = "Nothing runs on your PC until you approve it there.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = onRevoke,
                enabled = !pairingBusy
            ) {
                Text(if (companionState.isRevoking) "Unpairing…" else "Unpair")
            }
            OutlinedButton(
                onClick = onClearLocalSession,
                enabled = !pairingBusy
            ) {
                Text("Remove from phone")
            }
        }
    }
}

@Composable
private fun CompanionWorkSection(
    companionState: CompanionUiState,
    pairingBusy: Boolean,
    busy: Boolean,
    onRequestApproval: (actionType: WorkActionType, targetPath: String, summary: String) -> Unit,
    onRefreshApproval: () -> Unit,
    onWaitForApproval: () -> Unit,
    onStopWaitingForApproval: () -> Unit,
    onStartWorkTask: () -> Unit,
    onRefreshWorkTask: () -> Unit,
    onCancelWorkTask: () -> Unit,
    onStageWorkTarget: (actionType: WorkActionType, targetPath: String, offsetBytes: Int?) -> Unit,
    onClearWorkState: () -> Unit,
    onInsertResultIntoDraft: () -> Unit
) {
    var selectedAction by remember(
        companionState.activeApprovalAction,
        companionState.activeApprovalTargetPath,
        companionState.pendingWorkOffsetBytes
    ) {
        mutableStateOf(companionState.activeApprovalAction)
    }
    var targetPath by remember(
        companionState.activeApprovalTargetPath,
        companionState.activeApprovalAction,
        companionState.pendingWorkOffsetBytes
    ) {
        mutableStateOf(companionState.activeApprovalTargetPath)
    }
    var summary by remember { mutableStateOf("") }

    Text(
        text = "Look at files on your PC",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        text = "List a shared folder or preview a text file, read-only. Only folders you shared in Orbit PC Companion " +
            "can be used, and each request has to be approved there on your PC.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (companionState.health?.workEnabled == false) {
        Text(
            text = "Share a folder in Orbit PC Companion on your PC before asking for files.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    if (companionState.activeApproval != null || companionState.activeTask != null) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = onClearWorkState,
                enabled = !pairingBusy && !companionState.isRequestingApproval && !companionState.isCancellingWorkTask
            ) {
                Text("Clear Work State")
            }
        }
    }

    companionState.activeApproval?.let { approval ->
        val consumedByVisibleTask = approval.status == "CONSUMED" &&
            companionState.activeTask?.approvalId == approval.approvalId
        if (!consumedByVisibleTask) {
            CompanionApprovalCard(
                approval = approval,
                targetPath = companionState.activeApprovalTargetPath,
                requestedOffsetBytes = companionState.pendingWorkOffsetBytes,
                isPollingApproval = companionState.isPollingApproval,
                isRunningWorkTask = companionState.isRunningWorkTask,
                pairingBusy = pairingBusy,
                busy = busy,
                onRefreshApproval = onRefreshApproval,
                onWaitForApproval = onWaitForApproval,
                onStopWaitingForApproval = onStopWaitingForApproval,
                onStartWorkTask = onStartWorkTask
            )
        }
    }

    companionState.activeTask?.let { task ->
        CompanionWorkTaskCard(
            task = task,
            currentTargetPath = companionState.activeApprovalTargetPath,
            isRunningWorkTask = companionState.isRunningWorkTask,
            isCancellingWorkTask = companionState.isCancellingWorkTask,
            pairingBusy = pairingBusy,
            busy = busy,
            onRefreshWorkTask = onRefreshWorkTask,
            onCancelWorkTask = onCancelWorkTask,
            onStageWorkTarget = onStageWorkTarget,
            onInsertResultIntoDraft = onInsertResultIntoDraft
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WorkActionType.entries.forEach { action ->
            FilterChip(
                selected = selectedAction == action,
                onClick = { if (!busy) selectedAction = action },
                enabled = !busy,
                label = { Text(action.displayName) }
            )
        }
    }

    OutlinedTextField(
        value = targetPath,
        onValueChange = { targetPath = it },
        label = { Text("Absolute Allowlisted PC Path") },
        placeholder = {
            Text(
                if (selectedAction == WorkActionType.LIST_DIRECTORY) "e.g. C:\\Users\\<user>\\Documents\\Orbit-Shared"
                else "e.g. C:\\Users\\<user>\\Documents\\Orbit-Shared\\notes.txt"
            )
        },
        supportingText = {
            Text("The full path, starting with the drive letter, inside a folder you shared on your PC.")
        },
        singleLine = true,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = summary,
        onValueChange = { summary = it },
        label = { Text("Note for your PC (optional)") },
        placeholder = { Text("Shown on your PC when you approve") },
        singleLine = true,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth()
    )

    if (companionState.pendingWorkOffsetBytes != null && selectedAction == WorkActionType.READ_FILE_PREVIEW) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Staged chunk offset: ${companionState.pendingWorkOffsetBytes} B",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(
                onClick = { onStageWorkTarget(selectedAction, targetPath, null) },
                enabled = !busy
            ) {
                Text("Reset Offset")
            }
        }
    }

    companionState.errorMessage?.let { err ->
        Text(
            text = err,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    } ?: companionState.statusMessage?.let { msg ->
        Text(
            text = msg,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }

    Button(
        onClick = {
            val cleanSummary = summary.trim().ifEmpty {
                "${selectedAction.displayName}: ${targetPath.trim()}"
            }
            onRequestApproval(selectedAction, targetPath, cleanSummary)
        },
        enabled = !busy && targetPath.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (companionState.isRequestingApproval) "Requesting Approval..." else "Request PC Approval")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompanionApprovalCard(
    approval: CompanionApproval,
    targetPath: String,
    requestedOffsetBytes: Int?,
    isPollingApproval: Boolean,
    isRunningWorkTask: Boolean,
    pairingBusy: Boolean,
    busy: Boolean,
    onRefreshApproval: () -> Unit,
    onWaitForApproval: () -> Unit,
    onStopWaitingForApproval: () -> Unit,
    onStartWorkTask: () -> Unit
) {
    val scopeDisplay = targetPath.trim().ifBlank { approval.targetName }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // "Approval " stays at the start: QA scripts match it on screen.
            Text(
                text = "Approval request · " +
                    (WorkActionType.values().firstOrNull { it.wireName == approval.actionType }?.displayName ?: approval.actionType),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = scopeDisplay,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            if (approval.isAssistantProposed) {
                Text(
                    text = approval.reason?.let { "Suggested by Orbit in chat: “$it”" } ?: "Suggested by Orbit in chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (requestedOffsetBytes != null && approval.actionType == WorkActionType.READ_FILE_PREVIEW.wireName) {
                Text(
                    text = "Starting at byte $requestedOffsetBytes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = CompanionStatusText.approvalLine(approval.status, waiting = isPollingApproval),
                style = MaterialTheme.typography.bodySmall,
                color = when (approval.status) {
                    "APPROVED" -> MaterialTheme.colorScheme.primary
                    "DENIED", "EXPIRED", "REVOKED" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            // The ID matches the request shown in Orbit PC Companion on the PC.
            Text(
                text = "Request ${approval.approvalId} · ${approval.status}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // FlowRow, not a weighted Row: at large font sizes a button moves to its own line instead of squeezing.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (approval.status == "PENDING" && isPollingApproval) {
                    OutlinedButton(
                        onClick = onStopWaitingForApproval,
                        enabled = !pairingBusy
                    ) {
                        Text("Stop Waiting")
                    }
                } else if (approval.status == "PENDING") {
                    OutlinedButton(
                        onClick = onRefreshApproval,
                        enabled = !busy
                    ) {
                        Text("Check Once")
                    }
                    OutlinedButton(
                        onClick = onWaitForApproval,
                        enabled = !busy
                    ) {
                        Text("Wait for PC")
                    }
                }
                if (approval.status == "APPROVED") {
                    Button(
                        onClick = onStartWorkTask,
                        enabled = !busy
                    ) {
                        Text(if (isRunningWorkTask) "Running..." else "Run Approved Task")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompanionWorkTaskCard(
    task: WorkTask,
    currentTargetPath: String,
    isRunningWorkTask: Boolean,
    isCancellingWorkTask: Boolean,
    pairingBusy: Boolean,
    busy: Boolean,
    onRefreshWorkTask: () -> Unit,
    onCancelWorkTask: () -> Unit,
    onStageWorkTarget: (actionType: WorkActionType, targetPath: String, offsetBytes: Int?) -> Unit,
    onInsertResultIntoDraft: () -> Unit
) {
    val active = task.status == "QUEUED" || task.status == "RUNNING"
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = CompanionStatusText.taskLine(task.status, task.progressPercent),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = when (task.status) {
                    "COMPLETED" -> MaterialTheme.colorScheme.primary
                    "FAILED", "CANCELLED" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = (WorkActionType.values().firstOrNull { it.wireName == task.actionType }?.displayName ?: task.actionType) +
                    " · ${task.targetName}",
                style = MaterialTheme.typography.bodySmall
            )
            task.error?.takeIf { it.isNotBlank() }?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = "Task ${task.taskId} · ${task.status}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (active) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        onClick = onRefreshWorkTask,
                        enabled = !busy
                    ) {
                        Text(if (isRunningWorkTask) "Polling..." else "Refresh Task")
                    }
                    OutlinedButton(
                        onClick = onCancelWorkTask,
                        enabled = !pairingBusy && !isCancellingWorkTask
                    ) {
                        Text(if (isCancellingWorkTask) "Cancelling..." else "Cancel Task")
                    }
                }
            }
            task.result?.let { result ->
                WorkTaskResultPreview(
                    task = task,
                    result = result,
                    currentTargetPath = currentTargetPath,
                    busy = busy,
                    onStageWorkTarget = onStageWorkTarget,
                    onInsertResultIntoDraft = onInsertResultIntoDraft
                )
            }
        }
    }
}

@Composable
private fun WorkTaskResultPreview(
    task: WorkTask,
    result: WorkTaskResult,
    currentTargetPath: String,
    busy: Boolean,
    onStageWorkTarget: (actionType: WorkActionType, targetPath: String, offsetBytes: Int?) -> Unit,
    onInsertResultIntoDraft: () -> Unit
) {
    val canInsertIntoDraft = CompanionPathHelper.formatWorkResultForDraft(task) != null
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (result.actionType == WorkActionType.LIST_DIRECTORY.wireName || result.entries.isNotEmpty()) {
                val parent = CompanionPathHelper.parentPath(currentTargetPath)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Directory: ${result.directoryName ?: "-"} · ${result.entryCount ?: result.entries.size} entries" +
                            if (result.truncated) " (truncated)" else "",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (parent != null) {
                        TextButton(
                            onClick = { onStageWorkTarget(WorkActionType.LIST_DIRECTORY, parent, null) },
                            enabled = !busy
                        ) {
                            Text("Up")
                        }
                    }
                }
                if (result.entries.isEmpty()) {
                    Text(
                        text = "(empty directory)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Tap a folder or file to set it up as your next request.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    result.entries.take(50).forEach { entry ->
                        val sizeSuffix = entry.sizeBytes?.let { " ($it B)" }.orEmpty()
                        val nextAction = when (entry.kind) {
                            "dir" -> WorkActionType.LIST_DIRECTORY
                            "file" -> WorkActionType.READ_FILE_PREVIEW
                            else -> null
                        }
                        val nextPath = CompanionPathHelper.joinPath(currentTargetPath, entry.name)
                        val rowModifier = if (nextAction != null && !busy) {
                            Modifier
                                .fillMaxWidth()
                                .clickable { onStageWorkTarget(nextAction, nextPath, null) }
                                .padding(vertical = 3.dp)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        }
                        Text(
                            text = "[${entry.kind}] ${entry.name}$sizeSuffix" +
                                if (entry.kind == "symlink") " (blocked)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (nextAction != null) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = rowModifier
                        )
                    }
                }
            } else {
                val startOffset = result.offsetBytes ?: 0
                val bytesRead = result.bytesRead ?: 0
                val totalBytes = result.totalSizeBytes ?: 0L
                val endOffset = startOffset + bytesRead
                val encodingLabel = result.encoding?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                Text(
                    text = "File: ${result.fileName ?: "-"} · $startOffset..$endOffset/$totalBytes B$encodingLabel" +
                        if (result.truncated) " (truncated)" else "",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                result.sha256?.takeIf { it.isNotBlank() }?.let { digest ->
                    Text(
                        text = "SHA-256: ${digest.take(16)}...",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (result.isBinary == true) {
                    Text(
                        text = "This isn't a text file, so there's no preview.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = result.previewText.orEmpty().ifEmpty { "(empty file)" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    )
                    if (result.truncated && bytesRead > 0 && currentTargetPath.isNotBlank()) {
                        val nextOffset = startOffset + bytesRead
                        OutlinedButton(
                            onClick = {
                                onStageWorkTarget(WorkActionType.READ_FILE_PREVIEW, currentTargetPath, nextOffset)
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Set up the next part (from byte $nextOffset)")
                        }
                    }
                }
            }
            if (canInsertIntoDraft) {
                OutlinedButton(
                    onClick = onInsertResultIntoDraft,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add to message")
                }
            }
        }
    }
}
