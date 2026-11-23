package com.hydrogen.ai.companion

import com.hydrogen.ai.model.LocalServerPresets
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface CompanionClient {
    suspend fun checkHealth(baseUrl: String): CompanionResult<CompanionHealthSnapshot>

    suspend fun pairDevice(
        baseUrl: String,
        deviceName: String,
        pairingId: String,
        pairingCode: String
    ): CompanionResult<CompanionSession>

    suspend fun revokePairing(session: CompanionSession): CompanionResult<RevokeResponse>

    suspend fun requestApproval(
        session: CompanionSession,
        actionType: WorkActionType,
        targetPath: String,
        summary: String,
        idempotencyKey: String
    ): CompanionResult<CompanionApproval>

    /**
     * Sends a request the AI suggested (origin "assistant-proposed"). Call it only after the user tapped
     * Send on the suggestion card; the Windows owner still approves or denies it on the PC.
     * [summary] must come from the phone's fixed template, never from model text.
     * Fails closed for clients that don't support proposals.
     */
    suspend fun proposeApproval(
        session: CompanionSession,
        actionType: WorkActionType,
        targetPath: String,
        summary: String,
        idempotencyKey: String,
        reason: String?
    ): CompanionResult<CompanionApproval> =
        CompanionResult.Failure("proposals_unsupported", "This connection can't send AI suggestions to your PC.")

    suspend fun getApprovalStatus(
        session: CompanionSession,
        approvalId: String
    ): CompanionResult<CompanionApproval>

    suspend fun startWorkTask(
        session: CompanionSession,
        approvalId: String,
        actionType: WorkActionType,
        targetPath: String,
        idempotencyKey: String,
        maxBytes: Int? = null,
        offsetBytes: Int? = null,
        maxEntries: Int? = null
    ): CompanionResult<WorkTaskEnvelope>

    suspend fun getWorkTaskStatus(
        session: CompanionSession,
        taskId: String
    ): CompanionResult<WorkTaskEnvelope>

    suspend fun cancelWorkTask(
        session: CompanionSession,
        taskId: String
    ): CompanionResult<WorkTaskEnvelope>
}

object CompanionUrlSafety {
    private val LOOPBACK_HOSTS = setOf("127.0.0.1", "localhost", "10.0.2.2", "::1")
    private val DOTTED_QUAD = Regex("""^(0|[1-9][0-9]{0,2})(\.(0|[1-9][0-9]{0,2})){3}$""")

    fun validate(rawUrl: String): String? {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return "Companion URL is required."
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return "Invalid companion URL."
        val scheme = uri.scheme?.lowercase() ?: return "Companion URL must include http:// or https://."
        val host = uri.host?.lowercase()?.trim('[', ']') ?: return "Companion URL is missing a host."
        return when {
            scheme == "https" -> null
            scheme == "http" && host in LOOPBACK_HOSTS -> null
            scheme == "http" && privateIpv4(host) != null -> null
            scheme == "http" -> "Plain http:// only works for this PC over USB (127.0.0.1), the emulator (10.0.2.2) " +
                "or a PC on your Wi-Fi typed as numbers, like 192.168.1.20. Use https:// for anything else."
            else -> "Unsupported URL scheme '$scheme'."
        }
    }

    /**
     * What the owner typed, as a companion URL: "192.168.1.23" and "192.168.1.23:18812" become
     * "http://192.168.1.23:18812". A plain-http loopback or Wi-Fi address without a port gets the Companion port.
     */
    fun normalize(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return withScheme
        val host = uri.host?.lowercase()?.trim('[', ']') ?: return withScheme
        val plainLocal = uri.scheme.equals("http", ignoreCase = true) &&
            (host in LOOPBACK_HOSTS || privateIpv4(host) != null)
        if (uri.port != -1 || !plainLocal || !uri.rawPath.isNullOrEmpty()) return withScheme
        val hostPart = if (':' in host) "[$host]" else host
        return "${uri.scheme.lowercase()}://$hostPart:${CompanionDefaults.PORT}"
    }

    /**
     * True for USB/emulator addresses, where the legacy pairing code may still travel (it never leaves the PC).
     * 10.0.2.2 is the PC only inside the emulator; on a real phone it is an ordinary Wi-Fi address.
     */
    fun isLoopback(url: String): Boolean {
        val host = runCatching { URI(url.trim()).host }.getOrNull()?.lowercase()?.trim('[', ']') ?: return false
        if (host == LocalServerPresets.EMULATOR_HOST) return LocalServerPresets.pcHost == LocalServerPresets.EMULATOR_HOST
        return host in LOOPBACK_HOSTS
    }

    /** The private IPv4 literal of a plain-http Wi-Fi address (sent over [LanHttpTransport]), else null. */
    fun lanIpv4Host(url: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("http", ignoreCase = true)) return null
        val host = uri.host ?: return null
        if (privateIpv4(host) == null || isLoopback(url)) return null
        return host
    }

    /**
     * The four bytes of a private-network IPv4 literal (10/8, 172.16/12, 192.168/16, Tailscale 100.64/10),
     * else null. Same ranges as the PC's Wi-Fi peer filter. No DNS is ever involved.
     */
    fun privateIpv4(host: String): ByteArray? {
        if (!DOTTED_QUAD.matches(host)) return null
        val parts = host.split('.').map { it.toInt() }
        if (parts.any { it > 255 }) return null
        val (a, b) = parts[0] to parts[1]
        val private = a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 100 && b in 64..127)
        return if (private) ByteArray(4) { parts[it].toByte() } else null
    }
}

class HttpCompanionClient(
    private val clockSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
    private val connectTimeoutMs: Int = 6_000,
    private val readTimeoutMs: Int = 10_000,
    /** Tests only: where a Wi-Fi address is actually dialed (lets JVM tests use 127.0.0.1). Identity in the app. */
    private val lanDialAddress: (ByteArray) -> ByteArray = { it }
) : CompanionClient {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun checkHealth(baseUrl: String): CompanionResult<CompanionHealthSnapshot> =
        withContext(Dispatchers.IO) {
            val cleanUrl = baseUrl.trim().trimEnd('/')
            CompanionUrlSafety.validate(cleanUrl)?.let { reason ->
                return@withContext CompanionResult.Failure("unsafe_url", reason)
            }
            executeHttp("GET", "$cleanUrl/health", body = null) { _, responseBody ->
                val snapshot = json.decodeFromString(CompanionHealthSnapshot.serializer(), responseBody)
                CompanionResult.Success(snapshot)
            }
        }

    override suspend fun pairDevice(
        baseUrl: String,
        deviceName: String,
        pairingId: String,
        pairingCode: String
    ): CompanionResult<CompanionSession> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        CompanionUrlSafety.validate(cleanUrl)?.let { reason ->
            return@withContext CompanionResult.Failure("unsafe_url", reason)
        }
        val cleanId = pairingId.trim()
        val cleanCode = pairingCode.trim()
        // Never cut an emoji in half at 64: a lone surrogate would change the bytes the pairing proof covers.
        val cleanName = deviceName.trim().ifBlank { CompanionDefaults.DEFAULT_DEVICE_NAME }.take(64)
            .let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
        if (cleanId.isEmpty() || cleanCode.isEmpty()) {
            return@withContext CompanionResult.Failure("missing_pairing_fields", "Pairing ID and pairing code are required.")
        }

        val deviceId = "and_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val keyPair = CompanionCrypto.generateX25519KeyPair()
        val clientPublicKeyB64 = CompanionCrypto.encodeB64(keyPair.publicKey)
        // Pairing v2 (docs/WIFI_MODE_SPEC.md): prove the code instead of sending it. The legacy code field goes
        // only to USB/emulator addresses, where it never leaves the PC, so an older Companion still pairs.
        val loopback = CompanionUrlSafety.isLoopback(cleanUrl)
        val payloadJson = buildJsonObject {
            put("pairingId", cleanId)
            if (loopback) put("pairingCode", cleanCode)
            put("deviceId", deviceId)
            put("deviceName", cleanName)
            put("clientPublicKeyB64", clientPublicKeyB64)
            put(
                "pairingProof",
                CompanionCrypto.pairingClientProof(cleanId, cleanCode, deviceId, cleanName, clientPublicKeyB64)
            )
        }.toString()

        executeHttp("POST", "$cleanUrl/pair/complete", body = payloadJson) { _, responseBody ->
            val parsed = json.decodeFromString(PairingCompleteResponse.serializer(), responseBody)
            if (!parsed.paired || parsed.serverPublicKeyB64.isBlank()) {
                return@executeHttp CompanionResult.Failure(
                    "invalid_pairing_response",
                    "Companion returned an incomplete pairing response."
                )
            }
            // The PC must prove it knows the code, over exactly the keys we exchanged, before anything is saved.
            // Over USB an older Companion has no proof yet; over Wi-Fi a proof is required.
            val proofFailure = when {
                parsed.serverProof.isBlank() && loopback -> null
                parsed.serverProof.isBlank() -> "pc_proof_missing"
                parsed.deviceId.isNotBlank() && parsed.deviceId != deviceId -> "pc_proof_failed"
                !CompanionCrypto.verifyPairingServerProof(
                    proofB64 = parsed.serverProof,
                    pairingId = cleanId,
                    pairingCode = cleanCode,
                    deviceId = deviceId,
                    clientPublicKeyB64 = clientPublicKeyB64,
                    serverPublicKeyB64 = parsed.serverPublicKeyB64,
                    keyId = parsed.keyId
                ) -> "pc_proof_failed"
                else -> null
            }
            if (proofFailure != null) {
                return@executeHttp CompanionResult.Failure(
                    proofFailure,
                    "This PC couldn't prove it showed you that code. Nothing was saved. Check the address and " +
                        "pair again with a new code."
                )
            }
            val serverPub = runCatching { CompanionCrypto.decodeB64(parsed.serverPublicKeyB64) }.getOrNull()
                ?: return@executeHttp CompanionResult.Failure(
                    "invalid_server_key",
                    "Companion returned a malformed server public key."
                )
            val sharedSecret = runCatching {
                CompanionCrypto.computeSharedSecret(keyPair.privateKey, serverPub)
            }.getOrElse {
                return@executeHttp CompanionResult.Failure(
                    "key_agreement_failed",
                    "Failed X25519 key agreement with companion."
                )
            }
            val sessionKey = CompanionCrypto.deriveSessionKey(sharedSecret, cleanId, cleanCode)
            CompanionResult.Success(
                CompanionSession(
                    baseUrl = cleanUrl,
                    deviceId = parsed.deviceId.ifBlank { deviceId },
                    deviceName = cleanName,
                    keyId = parsed.keyId,
                    sessionKeyBytes = sessionKey,
                    encryptedTransport = parsed.encryptedTransport.ifBlank { CompanionDefaults.ENCRYPTED_TRANSPORT_ID },
                    pairedAtEpochSec = parsed.pairedAt
                )
            )
        }
    }

    override suspend fun revokePairing(session: CompanionSession): CompanionResult<RevokeResponse> =
        postEncrypted(
            session = session,
            path = "/pair/revoke",
            plaintextJson = "{}",
            decode = { jsonText -> json.decodeFromString(RevokeResponse.serializer(), jsonText) }
        )

    override suspend fun requestApproval(
        session: CompanionSession,
        actionType: WorkActionType,
        targetPath: String,
        summary: String,
        idempotencyKey: String
    ): CompanionResult<CompanionApproval> {
        val cleanPath = targetPath.trim()
        if (cleanPath.isEmpty()) {
            return CompanionResult.Failure("missing_target_path", "Target path on Windows PC is required.")
        }
        val cleanKey = idempotencyKey.trim()
        if (cleanKey.length < 8) {
            return CompanionResult.Failure("invalid_idempotency_key", "Idempotency key must be at least 8 characters.")
        }
        val body = ApprovalRequestBody(
            actionType = actionType.wireName,
            targetPath = cleanPath,
            summary = takeCodePoints(summary.trim(), AskPcProposals.MAX_REASON_CODE_POINTS).trimEnd(),
            idempotencyKey = cleanKey
        )
        return postEncrypted(
            session = session,
            path = "/approvals/request",
            plaintextJson = json.encodeToString(body),
            decode = { jsonText -> json.decodeFromString(CompanionApproval.serializer(), jsonText) }
        )
    }

    override suspend fun proposeApproval(
        session: CompanionSession,
        actionType: WorkActionType,
        targetPath: String,
        summary: String,
        idempotencyKey: String,
        reason: String?
    ): CompanionResult<CompanionApproval> {
        val cleanPath = targetPath.trim()
        if (cleanPath.isEmpty()) {
            return CompanionResult.Failure("missing_target_path", "Target path on Windows PC is required.")
        }
        val cleanKey = idempotencyKey.trim()
        if (cleanKey.length < 8) {
            return CompanionResult.Failure("invalid_idempotency_key", "Idempotency key must be at least 8 characters.")
        }
        val payload = buildJsonObject {
            put("actionType", actionType.wireName)
            put("targetPath", cleanPath)
            put("summary", takeCodePoints(summary.trim(), AskPcProposals.MAX_REASON_CODE_POINTS).trimEnd())
            put("idempotencyKey", cleanKey)
            // Always the exact string: the companion rejects null and treats a missing origin as "user".
            put("origin", CompanionApproval.ORIGIN_ASSISTANT_PROPOSED)
            AskPcProposals.cleanReason(reason)?.let { put("reason", it) }
        }
        return postEncrypted(
            session = session,
            path = "/approvals/request",
            plaintextJson = payload.toString(),
            decode = { jsonText -> json.decodeFromString(CompanionApproval.serializer(), jsonText) }
        )
    }

    override suspend fun getApprovalStatus(
        session: CompanionSession,
        approvalId: String
    ): CompanionResult<CompanionApproval> {
        val cleanId = approvalId.trim()
        if (cleanId.isEmpty()) {
            return CompanionResult.Failure("missing_approval_id", "Approval ID is required.")
        }
        return postEncrypted(
            session = session,
            path = "/approvals/status",
            plaintextJson = json.encodeToString(ApprovalStatusBody(cleanId)),
            decode = { jsonText -> json.decodeFromString(CompanionApproval.serializer(), jsonText) }
        )
    }

    override suspend fun startWorkTask(
        session: CompanionSession,
        approvalId: String,
        actionType: WorkActionType,
        targetPath: String,
        idempotencyKey: String,
        maxBytes: Int?,
        offsetBytes: Int?,
        maxEntries: Int?
    ): CompanionResult<WorkTaskEnvelope> {
        val cleanApprovalId = approvalId.trim()
        val cleanPath = targetPath.trim()
        val cleanKey = idempotencyKey.trim()
        if (cleanApprovalId.isEmpty() || cleanPath.isEmpty()) {
            return CompanionResult.Failure("missing_work_fields", "Approval ID and target path are required.")
        }
        if (cleanKey.length !in 8..128) {
            return CompanionResult.Failure("invalid_idempotency_key", "Idempotency key must be 8..128 characters.")
        }
        val payload = buildJsonObject {
            put("approvalId", cleanApprovalId)
            put("actionType", actionType.wireName)
            put("targetPath", cleanPath)
            put("idempotencyKey", cleanKey)
            if (actionType == WorkActionType.READ_FILE_PREVIEW) {
                if (maxBytes != null) put("maxBytes", maxBytes)
                if (offsetBytes != null) put("offsetBytes", offsetBytes)
            } else if (actionType == WorkActionType.LIST_DIRECTORY) {
                if (maxEntries != null) put("maxEntries", maxEntries)
            }
        }
        return postEncrypted(
            session = session,
            path = "/work/tasks/start",
            plaintextJson = payload.toString(),
            decode = { jsonText -> json.decodeFromString(WorkTaskEnvelope.serializer(), jsonText) }
        )
    }

    override suspend fun getWorkTaskStatus(
        session: CompanionSession,
        taskId: String
    ): CompanionResult<WorkTaskEnvelope> {
        val cleanId = taskId.trim()
        if (cleanId.isEmpty()) {
            return CompanionResult.Failure("missing_task_id", "Work task ID is required.")
        }
        return postEncrypted(
            session = session,
            path = "/work/tasks/status",
            plaintextJson = json.encodeToString(WorkTaskIdBody(cleanId)),
            decode = { jsonText -> json.decodeFromString(WorkTaskEnvelope.serializer(), jsonText) }
        )
    }

    override suspend fun cancelWorkTask(
        session: CompanionSession,
        taskId: String
    ): CompanionResult<WorkTaskEnvelope> {
        val cleanId = taskId.trim()
        if (cleanId.isEmpty()) {
            return CompanionResult.Failure("missing_task_id", "Work task ID is required.")
        }
        return postEncrypted(
            session = session,
            path = "/work/tasks/cancel",
            plaintextJson = json.encodeToString(WorkTaskIdBody(cleanId)),
            decode = { jsonText -> json.decodeFromString(WorkTaskEnvelope.serializer(), jsonText) }
        )
    }

    private suspend fun <T> postEncrypted(
        session: CompanionSession,
        path: String,
        plaintextJson: String,
        decode: (String) -> T
    ): CompanionResult<T> = withContext(Dispatchers.IO) {
        val cleanUrl = session.baseUrl.trim().trimEnd('/')
        CompanionUrlSafety.validate(cleanUrl)?.let { reason ->
            return@withContext CompanionResult.Failure("unsafe_url", reason)
        }
        val ts = clockSeconds().toString()
        val nonce = CompanionCrypto.generateNonce()
        val reqAad = CompanionCrypto.buildRequestAad("POST", path, session.deviceId, ts, nonce)
        val envelope = CompanionCrypto.encryptBytes(
            sessionKey = session.sessionKeyBytes,
            plaintext = plaintextJson.toByteArray(Charsets.UTF_8),
            aad = reqAad
        )
        val requestBody = json.encodeToString(envelope)
        val headers = mapOf(
            "X-Hydrogen-Device-Id" to session.deviceId,
            "X-Hydrogen-Timestamp" to ts,
            "X-Hydrogen-Nonce" to nonce
        )

        executeHttp("POST", "$cleanUrl$path", body = requestBody, headers = headers) { _, responseBody ->
            val respEnvelope = runCatching {
                json.decodeFromString(EncryptedEnvelope.serializer(), responseBody)
            }.getOrElse {
                return@executeHttp CompanionResult.Failure(
                    "invalid_encrypted_response",
                    "Companion returned a malformed encrypted envelope."
                )
            }
            val respAad = CompanionCrypto.buildResponseAad("POST", path, session.deviceId, ts, nonce)
            val decrypted = runCatching {
                CompanionCrypto.decryptBytes(session.sessionKeyBytes, respEnvelope, respAad)
            }.getOrElse {
                return@executeHttp CompanionResult.Failure(
                    "response_decryption_failed",
                    "Failed to authenticate encrypted response from companion."
                )
            }
            val decoded = runCatching {
                decode(decrypted.toString(Charsets.UTF_8))
            }.getOrElse { err ->
                return@executeHttp CompanionResult.Failure(
                    "invalid_payload",
                    "Unable to parse companion response: ${err.message ?: "malformed JSON"}"
                )
            }
            CompanionResult.Success(decoded)
        }
    }

    private fun <T> executeHttp(
        method: String,
        urlString: String,
        body: String?,
        headers: Map<String, String> = emptyMap(),
        onSuccess: (Int, String) -> CompanionResult<T>
    ): CompanionResult<T> {
        val lanHost = CompanionUrlSafety.lanIpv4Host(urlString)
        val (statusCode, responseText) = try {
            if (lanHost != null) {
                exchangeLan(method, urlString, lanHost, body, headers)
            } else {
                exchangePlatform(method, urlString, body, headers)
            }
        } catch (e: LanConnectException) {
            return CompanionResult.Failure(
                code = "network_error",
                userMessage = "Can't reach your PC at $lanHost. Check that Wi-Fi mode is on in the PC Companion, " +
                    "that both are on the same Wi-Fi, and that Windows allowed Private networks."
            )
        } catch (e: Exception) {
            return CompanionResult.Failure(
                code = "network_error",
                userMessage = e.message ?: "Unable to reach Windows companion."
            )
        }

        return if (statusCode in 200..299) {
            runCatching { onSuccess(statusCode, responseText) }.getOrElse { err ->
                CompanionResult.Failure(
                    code = "invalid_response",
                    userMessage = "Unexpected companion response: ${err.message ?: "parse error"}",
                    httpStatus = statusCode
                )
            }
        } else {
            val parsedErr = runCatching {
                json.decodeFromString(CompanionErrorPayload.serializer(), responseText)
            }.getOrNull()
            val code = parsedErr?.code?.takeIf { it.isNotBlank() } ?: "http_$statusCode"
            val message = parsedErr?.error?.takeIf { it.isNotBlank() }
                ?: "Companion returned HTTP $statusCode."
            CompanionResult.Failure(code = code, userMessage = message, httpStatus = statusCode)
        }
    }

    /** Wi-Fi mode: plain HTTP to a private IPv4 literal over [LanHttpTransport] (docs/WIFI_MODE_SPEC.md). */
    private fun exchangeLan(
        method: String,
        urlString: String,
        host: String,
        body: String?,
        headers: Map<String, String>
    ): Pair<Int, String> {
        val uri = URI(urlString)
        val address = CompanionUrlSafety.privateIpv4(host) ?: throw IllegalArgumentException("Not a Wi-Fi address.")
        val pathAndQuery = uri.rawPath.orEmpty().ifEmpty { "/" } + (uri.rawQuery?.let { "?$it" } ?: "")
        val allHeaders = LinkedHashMap<String, String>()
        allHeaders["Accept"] = "application/json"
        allHeaders["Cache-Control"] = "no-store"
        allHeaders.putAll(headers)
        if (body != null) allHeaders["Content-Type"] = "application/json; charset=utf-8"
        val response = LanHttpTransport.exchange(
            address = lanDialAddress(address),
            port = if (uri.port == -1) 80 else uri.port,
            method = method,
            pathAndQuery = pathAndQuery,
            headers = allHeaders,
            body = body?.toByteArray(Charsets.UTF_8),
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs
        )
        return response.status to response.body
    }

    private fun exchangePlatform(
        method: String,
        urlString: String,
        body: String?,
        headers: Map<String, String>
    ): Pair<Int, String> {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                useCaches = false
                // Never follow redirects: a redirect could bypass CompanionUrlSafety validation.
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-store")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                if (body != null) {
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    // Intentionally buffered (no fixed-length streaming mode): in streaming mode the
                    // JDK HttpURLConnection discards the connection on HTTP 401 (HttpRetryException),
                    // leaving errorStream null and losing structured error codes like replay_detected.
                    outputStream.use { it.write(bytes) }
                }
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return statusCode to responseText
        } finally {
            connection?.disconnect()
        }
    }
}

