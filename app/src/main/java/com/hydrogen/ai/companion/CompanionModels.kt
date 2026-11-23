package com.hydrogen.ai.companion

import com.hydrogen.ai.model.LocalServerPresets
import kotlinx.serialization.Serializable

object CompanionDefaults {
    const val PORT = 18812
    const val DEFAULT_COMPANION_URL = "http://10.0.2.2:18812"

    /** Emulator: the host PC alias. Real phone: loopback through `adb reverse tcp:18812 tcp:18812`. */
    fun defaultUrl(): String = LocalServerPresets.pcUrl(PORT)
    const val DEFAULT_DEVICE_NAME = "Android Orbit"
    const val ENCRYPTED_TRANSPORT_ID = "x25519-hkdf-aes256gcm"
}

@Serializable
data class CompanionConfig(
    val baseUrl: String = CompanionDefaults.defaultUrl(),
    val deviceName: String = CompanionDefaults.DEFAULT_DEVICE_NAME
)

@Serializable
data class CompanionCapabilities(
    val health: Boolean = false,
    val devicePairing: Boolean = false,
    val encryptedTransport: Boolean = false,
    val explicitApprovals: Boolean = false,
    val pathAllowlist: Boolean = false,
    val remoteFileRead: Boolean = false,
    val remoteCommandExecution: Boolean = false,
    val remoteTaskManagement: Boolean = false
)

@Serializable
data class CompanionHealthSnapshot(
    val service: String = "",
    val version: String = "",
    val status: String = "unpaired",
    val workEnabled: Boolean = false,
    val authenticated: Boolean = false,
    val approvalRequired: Boolean = true,
    val pairedDeviceCount: Int = 0,
    val allowlistRootCount: Int = 0,
    val capabilities: CompanionCapabilities = CompanionCapabilities()
)

@Serializable
data class PairingCompleteRequest(
    val pairingId: String,
    val pairingCode: String,
    val deviceId: String,
    val deviceName: String,
    val clientPublicKeyB64: String
)

@Serializable
data class PairingCompleteResponse(
    val paired: Boolean = false,
    val deviceId: String = "",
    val keyId: String = "",
    val serverPublicKeyB64: String = "",
    val encryptedTransport: String = "",
    val pairedAt: Long = 0L,
    /** Pairing v2 (docs/WIFI_MODE_SPEC.md); blank from an older Companion. */
    val serverProof: String = ""
)

@Serializable
data class EncryptedEnvelope(
    val ivB64: String,
    val ciphertextB64: String
)

@Serializable
data class RevokeResponse(
    val revoked: Boolean = false,
    val deviceId: String = ""
)

@Serializable
data class CompanionErrorPayload(
    val error: String = "",
    val code: String = ""
)

data class CompanionSession(
    val baseUrl: String,
    val deviceId: String,
    val deviceName: String,
    val keyId: String,
    val sessionKeyBytes: ByteArray,
    val encryptedTransport: String,
    val pairedAtEpochSec: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompanionSession) return false
        return baseUrl == other.baseUrl &&
            deviceId == other.deviceId &&
            deviceName == other.deviceName &&
            keyId == other.keyId &&
            sessionKeyBytes.contentEquals(other.sessionKeyBytes) &&
            encryptedTransport == other.encryptedTransport &&
            pairedAtEpochSec == other.pairedAtEpochSec
    }

    override fun hashCode(): Int {
        var result = baseUrl.hashCode()
        result = 31 * result + deviceId.hashCode()
        result = 31 * result + deviceName.hashCode()
        result = 31 * result + keyId.hashCode()
        result = 31 * result + sessionKeyBytes.contentHashCode()
        result = 31 * result + encryptedTransport.hashCode()
        result = 31 * result + pairedAtEpochSec.hashCode()
        return result
    }
}

sealed interface CompanionResult<out T> {
    data class Success<T>(val value: T) : CompanionResult<T>
    data class Failure(
        val code: String,
        val userMessage: String,
        val httpStatus: Int? = null
    ) : CompanionResult<Nothing>
}

enum class WorkActionType(val wireName: String, val displayName: String) {
    LIST_DIRECTORY("LIST_DIRECTORY", "List Directory"),
    READ_FILE_PREVIEW("READ_FILE_PREVIEW", "Read File Preview")
}

@Serializable
data class ApprovalRequestBody(
    val actionType: String,
    val targetPath: String,
    val summary: String,
    val idempotencyKey: String
)

@Serializable
data class ApprovalStatusBody(
    val approvalId: String
)

@Serializable
data class CompanionApproval(
    val approvalId: String = "",
    val actionType: String = "",
    val targetName: String = "",
    val status: String = "PENDING",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    /** "user" or "assistant-proposed" (PROTOCOL.md); older companions omit it. */
    val origin: String = ORIGIN_USER,
    /** The companion's cleaned copy of the AI's reason; null for ordinary requests. */
    val reason: String? = null
) {
    val isAssistantProposed: Boolean
        get() = origin == ORIGIN_ASSISTANT_PROPOSED

    companion object {
        const val ORIGIN_USER = "user"
        const val ORIGIN_ASSISTANT_PROPOSED = "assistant-proposed"
    }
}

@Serializable
data class WorkTaskStartBody(
    val approvalId: String,
    val actionType: String,
    val targetPath: String,
    val idempotencyKey: String,
    val maxBytes: Int? = null,
    val offsetBytes: Int? = null,
    val maxEntries: Int? = null
)

@Serializable
data class WorkTaskIdBody(
    val taskId: String
)

@Serializable
data class WorkDirectoryEntry(
    val name: String = "",
    val kind: String = "other",
    val sizeBytes: Long? = null
)

@Serializable
data class WorkTaskResult(
    val actionType: String = "",
    val fileName: String? = null,
    val offsetBytes: Int? = null,
    val bytesRead: Int? = null,
    val totalSizeBytes: Long? = null,
    val truncated: Boolean = false,
    val isBinary: Boolean? = null,
    val encoding: String? = null,
    val sha256: String? = null,
    val previewText: String? = null,
    val directoryName: String? = null,
    val entryCount: Int? = null,
    val entries: List<WorkDirectoryEntry> = emptyList()
)

@Serializable
data class WorkTask(
    val taskId: String = "",
    val approvalId: String = "",
    val actionType: String = "",
    val targetName: String = "",
    val status: String = "QUEUED",
    val progressPercent: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val result: WorkTaskResult? = null,
    val error: String? = null
)

@Serializable
data class WorkTaskEnvelope(
    val task: WorkTask,
    val deduplicated: Boolean = false
)

object CompanionPathHelper {
    private const val MAX_DRAFT_INSERT_CHARS = 3_500

    fun joinPath(basePath: String, childName: String): String {
        val cleanChild = childName.trim().trim('/', '\\')
        if (cleanChild.isEmpty()) return basePath.trim()
        val cleanBase = basePath.trim()
        if (cleanBase.isEmpty() || cleanBase == ".") return cleanChild
        val separator = if ('\\' in cleanBase && '/' !in cleanBase) '\\' else '/'
        val trimmedBase = cleanBase.trimEnd('/', '\\')
        return "$trimmedBase$separator$cleanChild"
    }

    fun parentPath(path: String): String? {
        val clean = path.trim()
        if (clean.isEmpty() || clean == ".") return null
        val isWindows = '\\' in clean && '/' !in clean
        val trimmed = clean.trimEnd('/', '\\')
        if (trimmed.length <= 2 && trimmed.endsWith(":")) return null
        val lastSep = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
        if (lastSep < 0) return "."
        val parent = trimmed.substring(0, lastSep)
        if (parent.isEmpty()) return if (isWindows) null else "/"
        if (parent.endsWith(":")) return "$parent\\"
        return parent
    }

    fun formatWorkResultForDraft(task: WorkTask): String? {
        val result = task.result ?: return null
        val body = if (result.actionType == WorkActionType.LIST_DIRECTORY.wireName || result.entries.isNotEmpty()) {
            val header = buildString {
                append("[PC Directory: ")
                append(result.directoryName ?: task.targetName.ifBlank { "." })
                append(" · ")
                append(result.entryCount ?: result.entries.size)
                append(" entries")
                if (result.truncated) append(", truncated")
                append("]")
            }
            val lines = if (result.entries.isEmpty()) {
                "(empty directory)"
            } else {
                result.entries.take(50).joinToString("\n") { entry ->
                    val size = entry.sizeBytes?.let { " ($it B)" }.orEmpty()
                    "- [${entry.kind}] ${entry.name}$size"
                }
            }
            "$header\n$lines"
        } else {
            if (result.isBinary == true) return null
            val text = result.previewText.orEmpty()
            if (text.isEmpty()) return null
            val startOffset = result.offsetBytes ?: 0
            val readLen = result.bytesRead ?: 0
            val total = result.totalSizeBytes ?: readLen.toLong()
            val header = "[PC File: ${result.fileName ?: task.targetName} · bytes $startOffset..${startOffset + readLen} of $total B]"
            "$header\n```\n$text\n```"
        }
        return if (body.length <= MAX_DRAFT_INSERT_CHARS) {
            body
        } else {
            body.take(MAX_DRAFT_INSERT_CHARS) + "\n...(preview truncated for chat draft)"
        }
    }
}

data class ParsedPairingInputs(
    val pairingId: String,
    val pairingCode: String
)

object CompanionPairingInputParser {
    private val PAIRING_ID_TOKEN = Regex("""(?i)\bpairingId\s*=\s*([^\s,;]+)""")
    private val PAIRING_CODE_TOKEN = Regex("""(?i)\bpairingCode\s*=\s*([^\s,;]+)""")
    private val DASHBOARD_ID_TOKEN = Regex("""(?im)^\s*pairing\s*id\s*:\s*([^\s,;]+)""")
    private val DASHBOARD_CODE_TOKEN = Regex("""(?im)^\s*pairing\s*code\s*:\s*([^\s,;]+)""")
    private val DASHBOARD_ID_PREFIX = Regex("""(?i)^pairing\s*id\s*:\s*""")
    private val DASHBOARD_CODE_PREFIX = Regex("""(?i)^pairing\s*code\s*:\s*""")

    fun normalize(rawPairingId: String, rawPairingCode: String): ParsedPairingInputs {
        val trimmedIdInput = rawPairingId.trim()
        val trimmedCodeInput = rawPairingCode.trim()

        val extractedId = PAIRING_ID_TOKEN.find(trimmedIdInput)?.groupValues?.getOrNull(1)?.trim()
            ?: DASHBOARD_ID_TOKEN.find(trimmedIdInput)?.groupValues?.getOrNull(1)?.trim()
        val extractedCodeFromId = PAIRING_CODE_TOKEN.find(trimmedIdInput)?.groupValues?.getOrNull(1)?.trim()
            ?: DASHBOARD_CODE_TOKEN.find(trimmedIdInput)?.groupValues?.getOrNull(1)?.trim()
        val extractedCodeFromCode = PAIRING_CODE_TOKEN.find(trimmedCodeInput)?.groupValues?.getOrNull(1)?.trim()

        val normalizedId = (extractedId ?: trimmedIdInput.replace(DASHBOARD_ID_PREFIX, "")).trim()
        val rawCodeResolved = extractedCodeFromCode
            ?: trimmedCodeInput.replace(DASHBOARD_CODE_PREFIX, "").trim()
                .ifEmpty { extractedCodeFromId.orEmpty() }
        val normalizedCode = rawCodeResolved.trim()
        return ParsedPairingInputs(
            pairingId = normalizedId,
            pairingCode = normalizedCode
        )
    }
}
