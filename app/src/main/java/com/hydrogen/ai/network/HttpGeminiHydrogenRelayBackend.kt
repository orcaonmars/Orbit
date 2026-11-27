package com.hydrogen.ai.network

import com.hydrogen.ai.companion.CompanionUrlSafety
import com.hydrogen.ai.companion.LanHttpStream
import com.hydrogen.ai.companion.LanHttpTransport
import com.hydrogen.ai.data.ImageLoadResult
import com.hydrogen.ai.data.ImagePayloadLoader
import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.ProviderError
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.ProviderPresets
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class HttpGeminiHydrogenRelayBackend(
    private val imageLoader: ImagePayloadLoader? = null,
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 60_000,
    /** Tests only: dial another address than the one in the URL (e.g. a fake server on 127.0.0.1). */
    private val lanDialAddress: (ByteArray) -> ByteArray = { it }
) : ChatBackend {

    override suspend fun checkHealth(config: RelayConfig, bearerToken: String?): RelayHealthResult =
        withContext(Dispatchers.IO) {
            val baseUrl = config.effectiveBaseUrl
            if (baseUrl.isEmpty()) {
                return@withContext RelayHealthResult.Failure(
                    ProviderError(
                        category = ProviderErrorCategory.UNCONFIGURED,
                        userMessage = "Enter the server URL before testing health.",
                        sourceLayer = "client"
                    )
                )
            }
            wifiModelTarget(baseUrl)?.let { wifi ->
                return@withContext checkWifiHealth(wifi, baseUrl, config.modelId.trim())
            }
            val token = bearerToken?.trim().orEmpty()
            val service = serviceNameOf(baseUrl)
            if (com.hydrogen.ai.model.ReasoningPolicy.isOpenRouter(baseUrl)) {
                return@withContext probeOpenRouterKey(token)
            }
            if (service != null && remoteModelProbeAllowed(baseUrl, token)) {
                // A known online service (OpenAI, Groq, Mistral...) never has the self-hosted status page: go straight to
                // its model list with the user's key, the same request chat makes anyway.
                return@withContext probeRemoteOpenAiModels(baseUrl, config.modelId.trim(), token)
            }
            var conn: HttpURLConnection? = null
            try {
                conn = (URL("$baseUrl/v1/gemini/status").openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    setRequestProperty("Accept", "application/json")
                    if (token.isNotEmpty()) setRequestProperty("Authorization", "Bearer $token")
                }
                val code = conn.responseCode
                val body = readStream(conn, code >= 400)
                val unsupported = statusEndpointUnsupported(code, body)
                if (unsupported && isLoopbackOrEmulatorHost(baseUrl)) {
                    // Guarded local-model fallback (e.g. LM Studio): the relay status route does not exist there, so
                    // probe the OpenAI-compatible model list instead of a fake relay/auth PASS or an opaque 404.
                    probeLocalOpenAiModels(baseUrl, config.modelId.trim(), token, code)
                } else if (unsupported && remoteModelProbeAllowed(baseUrl, token)) {
                    // Third-party OpenAI-compatible API (the "Custom" provider) over https with the user's own key: the
                    // key already goes to this exact host on every chat, so checking /v1/models adds no new exposure.
                    probeRemoteOpenAiModels(baseUrl, config.modelId.trim(), token)
                } else if (unsupported && code in 200..299) {
                    // Non-loopback host answering 2xx with a bare error body (e.g. LAN LM Studio "returning 200 anyway") or a
                    // non-JSON page (captive portal, web UI): not relay status and the token was never checked, so no
                    // reachable/auth PASS. Fallback stays loopback-only.
                    RelayHealthResult.Failure(
                        ProviderError(
                            category = ProviderErrorCategory.UNKNOWN_PROTOCOL,
                            httpStatus = code,
                            userMessage = "${service ?: "The server at ${hostAndPort(baseUrl)}"} answered, but not in a way Orbit understands, so the " +
                                "connection and key weren't checked. Check the address. For a server on another PC, use " +
                                "https and add its key.",
                            technicalDetail = SecretRedactor.redact(
                                "GET /v1/gemini/status -> $code; " + body.take(400), listOf(token)
                            ),
                            sourceLayer = "relay"
                        )
                    )
                } else if (code in 200..299) {
                    RelayHealthResult.Reachable(
                        authenticated = token.isNotEmpty(),
                        detail = "Relay /v1/gemini/status returned HTTP $code (health only; inference untested)."
                    )
                } else {
                    RelayHealthResult.Failure(
                        RelayErrorClassifier.classifyHttpError(
                            code, body, conn.getHeaderField("Retry-After"), ProviderRoute.GEMINI_HYDROGEN_RELAY, service
                        )
                    )
                }
            } catch (e: IOException) {
                RelayHealthResult.Failure(NetworkErrorClassifier.classify(e, baseUrl, service))
            } finally {
                conn?.disconnect()
            }
        }

    override suspend fun sendChat(
        request: ChatDispatchRequest,
        config: RelayConfig,
        bearerToken: String?,
        onStreamDelta: suspend (StreamDelta) -> Unit
    ): ChatDispatchResult = withContext(Dispatchers.IO) {
        val baseUrl = config.effectiveBaseUrl
        val token = bearerToken?.trim().orEmpty()
        val wifi = wifiModelTarget(baseUrl)
        // A token is optional for loopback/emulator-host local servers (e.g. LM Studio) and is never sent to a model
        // server on the home Wi-Fi (plain http); any other relay still requires a bearer token.
        if (baseUrl.isEmpty() || (token.isEmpty() && wifi == null && !isLoopbackOrEmulatorHost(baseUrl))) {
            return@withContext ChatDispatchResult.Failure(
                ProviderError(
                    category = ProviderErrorCategory.UNCONFIGURED,
                    userMessage = if (baseUrl.isEmpty()) NOT_CONNECTED_MESSAGE else NEEDS_TOKEN_MESSAGE,
                    technicalDetail = "baseUrl=${baseUrl.isNotEmpty()}, token=${token.isNotEmpty()}",
                    sourceLayer = "client"
                )
            )
        }
        val model = request.modelId.ifBlank { config.modelId }.trim()
        val useStream = request.stream && config.streamingEnabled
        val effectiveRequest = request.copy(
            reasoningEffort = com.hydrogen.ai.model.ReasoningPolicy.compatibleEffort(baseUrl, model, config.reasoningMode),
            openRouterReasoningEffort = com.hydrogen.ai.model.ReasoningPolicy.openRouterEffort(baseUrl, model, config.reasoningMode, config.openRouterReasoningModels),
            completionTokenBudget = com.hydrogen.ai.model.ReasoningPolicy.cleanApisBudget(baseUrl, config.reasoningMode)
        )
        val hasSystem = request.messages.any { it.role == MessageRole.SYSTEM }
        val foldKey = "$baseUrl|$model"
        // Some local chat templates have no system role (Gemma 2 in LM Studio, older Mistral builds) and refuse the
        // whole request. Gemma's own template puts instructions at the top of the first user turn, so do that for
        // Gemma up front, and for any other model after it has refused a system message once on this server.
        val foldUpFront = hasSystem &&
            (HttpGoogleAiStudioBackend.isGemmaModel(model) || foldKey in systemRoleRefused)
        val first = dispatchOnce(
            baseUrl, token, wifi, model, useStream,
            if (foldUpFront) withInstructionsInFirstUserTurn(effectiveRequest) else effectiveRequest,
            onStreamDelta
        )
        val failure = first.result as? ChatDispatchResult.Failure
        if (foldUpFront || !hasSystem || failure == null || first.streamed ||
            !rejectsSystemRole(failure.error.httpStatus, failure.error.technicalDetail)
        ) {
            return@withContext first.result
        }
        systemRoleRefused += foldKey
        dispatchOnce(baseUrl, token, wifi, model, useStream, withInstructionsInFirstUserTurn(effectiveRequest), onStreamDelta).result
    }

    /** Model servers (base URL and model) that refused a system message; later sends fold it into the user turn. */
    private val systemRoleRefused: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    /** One send. [streamed] is true once any reply text arrived, so a retry can never repeat visible output. */
    private class Dispatch(val result: ChatDispatchResult, val streamed: Boolean)

    private suspend fun dispatchOnce(
        baseUrl: String,
        token: String,
        wifi: WifiModelTarget?,
        model: String,
        useStream: Boolean,
        request: ChatDispatchRequest,
        onStreamDelta: suspend (StreamDelta) -> Unit
    ): Dispatch {
        var streamed = false
        val tracked: suspend (StreamDelta) -> Unit = { delta ->
            streamed = true
            onStreamDelta(delta)
        }
        val result = sendOnce(baseUrl, token, wifi, model, useStream, request, tracked)
        return Dispatch(result, streamed)
    }

    private suspend fun sendOnce(
        baseUrl: String,
        token: String,
        wifi: WifiModelTarget?,
        model: String,
        useStream: Boolean,
        request: ChatDispatchRequest,
        onStreamDelta: suspend (StreamDelta) -> Unit
    ): ChatDispatchResult {
        val payloadBuild = buildPayload(model, useStream, request)
        if (payloadBuild.error != null) {
            return ChatDispatchResult.Failure(payloadBuild.error)
        }
        if (wifi != null) {
            return sendChatOverWifi(
                wifi, request, payloadBuild.body!!.toString().toByteArray(Charsets.UTF_8), model, useStream,
                payloadBuild.usedVision, onStreamDelta
            )
        }
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(ProviderPresets.openAiEndpoint(baseUrl, "chat/completions")).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "POST"
                doOutput = true
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", if (useStream) "text/event-stream, application/json" else "application/json")
                if (token.isNotEmpty()) setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("X-Correlation-Id", request.correlationId)
                setRequestProperty("Idempotency-Key", request.correlationId)
            }
            conn.outputStream.use { it.write(payloadBuild.body!!.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val body = readStream(conn, true)
                return ChatDispatchResult.Failure(
                    RelayErrorClassifier.classifyHttpError(
                        code, body, conn.getHeaderField("Retry-After"), ProviderRoute.GEMINI_HYDROGEN_RELAY,
                        serviceNameOf(baseUrl)
                    )
                )
            }
            SseStreamReader.handleSuccessResponse(
                conn = conn,
                useStream = useStream,
                model = model,
                usedVision = payloadBuild.usedVision,
                sourceLayer = "relay",
                onStreamDelta = onStreamDelta,
                route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
                serviceName = serviceNameOf(baseUrl)
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: IOException) {
            ChatDispatchResult.Failure(NetworkErrorClassifier.classify(e, baseUrl, serviceNameOf(baseUrl), forChat = true))
        } finally {
            conn?.disconnect()
        }
    }

    internal data class PayloadBuildResult(
        val body: JsonObject? = null,
        val usedVision: Boolean = false,
        val error: ProviderError? = null
    )

    internal suspend fun buildPayload(
        model: String,
        stream: Boolean,
        request: ChatDispatchRequest
    ): PayloadBuildResult {
        var usedVision = false
        val messagesJson = mutableListOf<JsonObject>()
        val currentIndex = HistoryImages.newestUserIndex(request.messages)
        for ((msgIndex, msg) in request.messages.withIndex()) {
            val roleStr = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
            }
            val includedAtts = msg.attachments.filterNot { it.id in request.omittedAttachmentIds }
            if (includedAtts.isEmpty() || msg.role != MessageRole.USER) {
                messagesJson += buildJsonObject {
                    put("role", roleStr)
                    put("content", msg.content)
                }
            } else {
                val parts = mutableListOf<JsonObject>()
                if (msg.content.isNotBlank()) {
                    parts += buildJsonObject {
                        put("type", "text")
                        put("text", msg.content)
                    }
                }
                var leftOutPhoto = false
                for (att in includedAtts) {
                    val loader = imageLoader ?: return PayloadBuildResult(
                        error = ProviderError(
                            category = ProviderErrorCategory.BAD_REQUEST_400,
                            userMessage = "\"${att.displayName}\" couldn't be attached. Remove it and try again.",
                            technicalDetail = "no image loader",
                            sourceLayer = "client"
                        )
                    )
                    when (val loaded = loader.loadImage(att.localUri, att.mimeType)) {
                        is ImageLoadResult.Success -> {
                            usedVision = true
                            val dataUrl = "data:${loaded.payload.mimeType};base64,${loaded.payload.base64Data}"
                            parts += buildJsonObject {
                                put("type", "image_url")
                                put("image_url", buildJsonObject { put("url", dataUrl) })
                            }
                        }
                        is ImageLoadResult.MissingOrUnreadable -> {
                            if (msgIndex != currentIndex) {
                                leftOutPhoto = true
                                continue
                            }
                            return PayloadBuildResult(
                                error = ProviderError(
                                    category = ProviderErrorCategory.BAD_REQUEST_400,
                                    userMessage = "\"${att.displayName}\" couldn't be read, so nothing was sent. Remove it " +
                                        "or attach it again.",
                                    technicalDetail = loaded.reason,
                                    sourceLayer = "client"
                                )
                            )
                        }
                    }
                }
                if (leftOutPhoto) {
                    parts += buildJsonObject {
                        put("type", "text")
                        put("text", HistoryImages.LEFT_OUT_NOTE)
                    }
                }
                messagesJson += buildJsonObject {
                    put("role", roleStr)
                    put("content", JsonArray(parts))
                }
            }
        }
        val root = buildJsonObject {
            put("model", model)
            put("stream", stream)
            put("messages", JsonArray(messagesJson))
            request.completionTokenBudget?.let { put("max_tokens", it) }
            request.reasoningEffort?.let { put("reasoning_effort", it) }
            request.openRouterReasoningEffort?.let { effort ->
                put("reasoning", buildJsonObject { put("effort", effort) })
                put("provider", buildJsonObject { put("require_parameters", true) })
            }
        }
        return PayloadBuildResult(body = root, usedVision = usedVision)
    }


    /**
     * Local OpenAI-compatible servers (LM Studio) lack the relay's /v1/gemini/status: they answer 404/405/501,
     * or HTTP 200 with a bare {"error": "Unexpected endpoint or method..."} body. A real relay status has "status".
     * A 2xx body that is not a JSON object at all (a web UI's HTML catch-all page, a captive portal, an empty body)
     * is never relay status either, so it must not become relay-reachable or turn a placeholder token into auth PASS.
     */
    private fun statusEndpointUnsupported(code: Int, body: String): Boolean {
        if (code == 404 || code == 405 || code == 501) return true
        if (code !in 200..299) return false
        val obj = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return true
        return "error" in obj && "status" !in obj
    }

    /** Health only: proves an OpenAI-compatible model list is served. Never claims the bearer token was verified. */
    private fun probeLocalOpenAiModels(baseUrl: String, modelId: String, token: String, statusCode: Int): RelayHealthResult {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(ProviderPresets.openAiEndpoint(baseUrl, "models")).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
                if (token.isNotEmpty()) setRequestProperty("Authorization", "Bearer $token")
            }
            val code = conn.responseCode
            val body = readStream(conn, code >= 400)
            if (code !in 200..299) {
                return RelayHealthResult.Failure(
                    RelayErrorClassifier.classifyHttpError(
                        code, body, conn.getHeaderField("Retry-After"), ProviderRoute.GEMINI_HYDROGEN_RELAY
                    )
                )
            }
            val ids = parseOpenAiModelIds(body) ?: return RelayHealthResult.Failure(
                ProviderError(
                    category = ProviderErrorCategory.UNKNOWN_PROTOCOL,
                    httpStatus = code,
                    userMessage = "The server at ${hostAndPort(baseUrl)} answered, but it doesn't look like LM Studio, " +
                        "Ollama or another OpenAI-format server. Check the address and port.",
                    technicalDetail = SecretRedactor.redact("GET /v1/models -> $code; " + body.take(400), listOf(token)),
                    sourceLayer = "local_model"
                )
            )
            if (ids.isEmpty()) {
                // e.g. LM Studio running with no model loaded (JIT loading off) answers {"data":[]}: the server is up,
                // but no model can answer a chat, so this must not be reported as a reachable/healthy provider.
                // Not UNCONFIGURED: the app's URL/model are set, and that category marks setup incomplete and swaps this
                // diagnosis for the "hasn't been tested yet" text. Not SERVER_5XX: that would badge "Relay Ready".
                return RelayHealthResult.Failure(
                    ProviderError(
                        category = ProviderErrorCategory.UNKNOWN_PROTOCOL,
                        httpStatus = code,
                        userMessage = "The server at ${hostAndPort(baseUrl)} is running but lists no models, so nothing " +
                            "can answer yet. Load ${if (modelId.isEmpty()) "a model" else "'$modelId'"} in LM Studio or " +
                            "Ollama, then test again.",
                        technicalDetail = SecretRedactor.redact("GET /v1/models -> $code; " + body.take(400), listOf(token)),
                        sourceLayer = "local_model"
                    )
                )
            }
            val modelNote = when {
                modelId.isEmpty() -> "no model configured"
                modelId in ids -> "configured model '$modelId' is listed"
                else -> "configured model '$modelId' is NOT listed"
            }
            return RelayHealthResult.Reachable(
                authenticated = false,
                detail = "Local OpenAI-compatible server reachable via /v1/models (${ids.size} model(s); $modelNote). " +
                    "/v1/gemini/status is not supported there (HTTP $statusCode). Bearer token not verified; health only, inference untested."
            )
        } finally {
            conn?.disconnect()
        }
    }

    /** OpenRouter's public model catalog cannot verify credentials. Use its documented current-key endpoint. */
    private fun probeOpenRouterKey(token: String): RelayHealthResult {
        if (token.isBlank()) return RelayHealthResult.Failure(ProviderError(
            category = ProviderErrorCategory.UNCONFIGURED,
            userMessage = "Add an OpenRouter API key to test authentication. Browsing models does not require a key.",
            sourceLayer = "client"
        ))
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("https://openrouter.ai/api/v1/key").openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer "+token)
            }
            val code = conn.responseCode
            val body = readStream(conn, code >= 400)
            if (code !in 200..299) return RelayHealthResult.Failure(
                RelayErrorClassifier.classifyHttpError(code, body, conn.getHeaderField("Retry-After"),
                    ProviderRoute.GEMINI_HYDROGEN_RELAY, "OpenRouter")
            )
            val summary = openRouterKeyStatus(body) ?: return RelayHealthResult.Failure(ProviderError(
                category = ProviderErrorCategory.UNKNOWN_PROTOCOL,
                httpStatus = code,
                userMessage = "OpenRouter returned an unexpected key-check response. Your key was not verified.",
                technicalDetail = "Unexpected current-key response format",
                sourceLayer = "relay"
            ))
            return RelayHealthResult.Reachable(authenticated = true, detail = summary)
        } catch (e: IOException) {
            return RelayHealthResult.Failure(NetworkErrorClassifier.classify(e, "https://openrouter.ai", "OpenRouter"))
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Health for an https OpenAI-compatible provider. A 2xx model list proves the host speaks the API; a 401/403
     * proves the key was rejected. Still never claims the key is verified: some providers list models without auth.
     */
    private fun probeRemoteOpenAiModels(baseUrl: String, modelId: String, token: String): RelayHealthResult {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(ProviderPresets.openAiEndpoint(baseUrl, "models")).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
            }
            val code = conn.responseCode
            val body = readStream(conn, code >= 400)
            if (code !in 200..299) {
                return RelayHealthResult.Failure(
                    RelayErrorClassifier.classifyHttpError(
                        code, body, conn.getHeaderField("Retry-After"), ProviderRoute.GEMINI_HYDROGEN_RELAY,
                        serviceNameOf(baseUrl)
                    )
                )
            }
            val ids = parseOpenAiModelIds(body) ?: return RelayHealthResult.Failure(
                ProviderError(
                    category = ProviderErrorCategory.UNKNOWN_PROTOCOL,
                    httpStatus = code,
                    userMessage = "${serviceNameOf(baseUrl) ?: hostAndPort(baseUrl)} answered, but not like an " +
                        "OpenAI-format service. Check the address, or pick the Anthropic format if the service uses it.",
                    technicalDetail = SecretRedactor.redact("GET /v1/models -> $code; " + body.take(400), listOf(token)),
                    sourceLayer = "relay"
                )
            )
            val modelNote = when {
                modelId.isEmpty() -> "no model picked yet"
                ids.isEmpty() -> "model list is empty"
                modelId in ids -> "'$modelId' is available"
                else -> "'$modelId' is not in its list"
            }
            return RelayHealthResult.Reachable(
                authenticated = false,
                detail = "OpenAI-compatible API reachable via /v1/models (${ids.size} model(s); $modelNote). " +
                    "Key not rejected; it is confirmed by your first chat."
            )
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * GET {baseUrl}/v1/models. Same token policy as chat: loopback/emulator servers (LM Studio, Ollama) may omit the
     * bearer token; any other host requires it, and nothing is sent over the network without one.
     */
    override suspend fun listModels(config: RelayConfig, bearerToken: String?): ModelListResult =
        withContext(Dispatchers.IO) {
            val baseUrl = config.effectiveBaseUrl
            val token = bearerToken?.trim().orEmpty()
            if (baseUrl.isEmpty()) {
                return@withContext ModelListResult.Failure("Enter the server address first.")
            }
            wifiModelTarget(baseUrl)?.let { wifi ->
                return@withContext listWifiModels(wifi, baseUrl)
            }
            val service = serviceNameOf(baseUrl)
            val publicCatalog = com.hydrogen.ai.model.ReasoningPolicy.isOpenRouter(baseUrl)
            if (token.isEmpty() && !publicCatalog && !isLoopbackOrEmulatorHost(baseUrl)) {
                return@withContext ModelListResult.Failure(
                    "Add your ${service ?: "server's"} API key first. Only servers on this PC or your home Wi-Fi " +
                        "work without one."
                )
            }
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(ProviderPresets.openAiEndpoint(baseUrl, "models")).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    setRequestProperty("Accept", "application/json")
                    if (token.isNotEmpty() && !publicCatalog) setRequestProperty("Authorization", "Bearer $token")
                }
                val code = conn.responseCode
                val body = readStream(conn, code >= 400)
                if (code !in 200..299) {
                    return@withContext ModelListResult.Failure(
                        RelayErrorClassifier.classifyHttpError(
                            code, body, conn.getHeaderField("Retry-After"), ProviderRoute.GEMINI_HYDROGEN_RELAY, service
                        ).userMessage
                    )
                }
                val who = service ?: "The server at ${hostAndPort(baseUrl)}"
                val ids = parseOpenAiModelIds(body)
                    ?: return@withContext ModelListResult.Failure(
                        "$who answered, but not with a model list Orbit can read. Type the model name instead."
                    )
                val normalized = normalizeModelIds(ids)
                if (normalized.isEmpty()) {
                    ModelListResult.Failure(
                        if (service != null) {
                            "$service lists no chat models for this key. Type the model name instead."
                        } else {
                            "$who lists no models. Load a model there (in LM Studio or Ollama), then try again."
                        }
                    )
                } else {
                    ModelListResult.Success(normalized, if (publicCatalog) parseOpenRouterReasoningModels(body).intersect(normalized.map { it.lowercase() }.toSet()) else null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                ModelListResult.Failure(NetworkErrorClassifier.classify(e, baseUrl, service).userMessage)
            } finally {
                conn?.disconnect()
            }
        }

    /**
     * One plain-http request to the user's model server on the home Wi-Fi, over [LanHttpTransport] (Android blocks
     * cleartext HttpURLConnection to LAN hosts). It never carries an access key: anyone on the network could read it.
     */
    private fun openWifi(
        target: WifiModelTarget,
        method: String,
        endpoint: String,
        headers: Map<String, String>,
        body: ByteArray? = null
    ): LanHttpStream {
        require(headers.keys.none { it.equals("Authorization", ignoreCase = true) }) { "No keys over plain Wi-Fi." }
        return LanHttpTransport.open(
            address = lanDialAddress(target.address),
            port = target.port,
            method = method,
            pathAndQuery = target.basePath + endpoint,
            headers = headers,
            body = body,
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs
        )
    }

    private fun wifiHttpFailure(status: Int, body: String, retryAfter: String?): ProviderError =
        if (status == 401 || status == 403) {
            ProviderError(
                category = if (status == 401) {
                    ProviderErrorCategory.UNAUTHORIZED_RELAY_401
                } else {
                    ProviderErrorCategory.FORBIDDEN_403
                },
                httpStatus = status,
                userMessage = WIFI_KEY_REFUSED_MESSAGE,
                technicalDetail = body.take(400),
                sourceLayer = "local_model"
            )
        } else {
            RelayErrorClassifier.classifyHttpError(status, body, retryAfter, ProviderRoute.GEMINI_HYDROGEN_RELAY)
        }

    /** Health for a Wi-Fi model server: its OpenAI-compatible model list, fetched without a key. */
    private fun checkWifiHealth(target: WifiModelTarget, baseUrl: String, modelId: String): RelayHealthResult {
        try {
            openWifi(target, "GET", "/v1/models", mapOf("Accept" to "application/json")).use { reply ->
                val body = readCapped(reply.body)
                if (reply.status !in 200..299) {
                    return RelayHealthResult.Failure(wifiHttpFailure(reply.status, body, reply.headers["retry-after"]))
                }
                val ids = parseOpenAiModelIds(body) ?: return RelayHealthResult.Failure(
                    ProviderError(
                        category = ProviderErrorCategory.UNKNOWN_PROTOCOL,
                        httpStatus = reply.status,
                        userMessage = "$baseUrl answered, but /v1/models did not return a model list the way LM Studio " +
                            "or Ollama do. Check the address and port.",
                        technicalDetail = body.take(400),
                        sourceLayer = "local_model"
                    )
                )
                if (ids.isEmpty()) {
                    return RelayHealthResult.Failure(
                        ProviderError(
                            category = ProviderErrorCategory.UNKNOWN_PROTOCOL,
                            httpStatus = reply.status,
                            userMessage = "The server at $baseUrl is reachable over Wi-Fi, but lists no models, so nothing " +
                                "can answer chat. Load ${if (modelId.isEmpty()) "a model" else "'$modelId'"} there " +
                                "(e.g. in LM Studio), then re-test.",
                            technicalDetail = body.take(400),
                            sourceLayer = "local_model"
                        )
                    )
                }
                val modelNote = when {
                    modelId.isEmpty() -> "no model configured"
                    modelId in ids -> "configured model '$modelId' is listed"
                    else -> "configured model '$modelId' is NOT listed"
                }
                return RelayHealthResult.Reachable(
                    authenticated = false,
                    detail = "Model server on your Wi-Fi reachable via /v1/models (${ids.size} model(s); $modelNote). " +
                        "Plain Wi-Fi: no access key is sent. Health only, inference untested."
                )
            }
        } catch (e: IOException) {
            return RelayHealthResult.Failure(
                ProviderError(
                    category = ProviderErrorCategory.NETWORK_TIMEOUT,
                    userMessage = wifiUnreachableMessage(baseUrl),
                    technicalDetail = e.message ?: e::class.java.simpleName,
                    sourceLayer = "network"
                )
            )
        }
    }

    private fun listWifiModels(target: WifiModelTarget, baseUrl: String): ModelListResult {
        try {
            openWifi(target, "GET", "/v1/models", mapOf("Accept" to "application/json")).use { reply ->
                val body = readCapped(reply.body)
                if (reply.status == 401 || reply.status == 403) return ModelListResult.Failure(WIFI_KEY_REFUSED_MESSAGE)
                if (reply.status !in 200..299) {
                    return ModelListResult.Failure("Server at $baseUrl answered /v1/models with HTTP ${reply.status}.")
                }
                val ids = parseOpenAiModelIds(body)
                    ?: return ModelListResult.Failure("Server at $baseUrl did not return an OpenAI-compatible model list.")
                val normalized = normalizeModelIds(ids)
                return if (normalized.isEmpty()) {
                    ModelListResult.Failure("Server at $baseUrl lists no models. Load a model there (e.g. in LM Studio), then try again.")
                } else {
                    ModelListResult.Success(normalized)
                }
            }
        } catch (e: IOException) {
            return ModelListResult.Failure(wifiUnreachableMessage(baseUrl))
        }
    }

    private suspend fun sendChatOverWifi(
        target: WifiModelTarget,
        request: ChatDispatchRequest,
        payload: ByteArray,
        model: String,
        useStream: Boolean,
        usedVision: Boolean,
        onStreamDelta: suspend (StreamDelta) -> Unit
    ): ChatDispatchResult {
        val headers = linkedMapOf(
            "Content-Type" to "application/json; charset=utf-8",
            "Accept" to if (useStream) "text/event-stream, application/json" else "application/json",
            "X-Correlation-Id" to request.correlationId,
            "Idempotency-Key" to request.correlationId
        )
        try {
            openWifi(target, "POST", "/v1/chat/completions", headers, payload).use { reply ->
                if (reply.status !in 200..299) {
                    val body = readCapped(reply.body)
                    return ChatDispatchResult.Failure(wifiHttpFailure(reply.status, body, reply.headers["retry-after"]))
                }
                return SseStreamReader.handleSuccessBody(
                    code = reply.status,
                    contentType = reply.contentType,
                    openBody = { reply.body },
                    useStream = useStream,
                    model = model,
                    usedVision = usedVision,
                    sourceLayer = "relay",
                    onStreamDelta = onStreamDelta,
                    route = ProviderRoute.GEMINI_HYDROGEN_RELAY
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: IllegalArgumentException) {
            return ChatDispatchResult.Failure(
                ProviderError(
                    category = ProviderErrorCategory.BAD_REQUEST_400,
                    userMessage = "Orbit couldn't build a request for this server address. Check it in Set up.",
                    technicalDetail = e.message.orEmpty(),
                    sourceLayer = "client"
                )
            )
        } catch (e: IOException) {
            return ChatDispatchResult.Failure(
                ProviderError(
                    category = ProviderErrorCategory.NETWORK_TIMEOUT,
                    userMessage = "Couldn't reach your AI server over Wi-Fi. Check that it's running with \"Serve on " +
                        "Local Network\" on and that this phone is on the same Wi-Fi, then tap Retry. Your message is saved.",
                    technicalDetail = e.message ?: e::class.java.simpleName,
                    sourceLayer = "network"
                )
            )
        }
    }

    /** Reads at most [max] bytes (error bodies, model lists); anything longer is cut, never buffered whole. */
    private fun readCapped(input: InputStream, max: Int = 2 * 1024 * 1024): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < max) {
            val n = input.read(buffer, 0, minOf(buffer.size, max - out.size()))
            if (n == -1) break
            out.write(buffer, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun parseOpenAiModelIds(body: String): List<String>? = Companion.parseModelIds(body)

    private fun readStream(conn: HttpURLConnection, isError: Boolean): String {
        val stream = if (isError) conn.errorStream else conn.inputStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    companion object {
        private val LOCAL_MODEL_HOSTS = setOf("127.0.0.1", "localhost", "10.0.2.2", "::1")
        const val MAX_LISTED_MODELS = 2_000

        /**
         * True for the error a chat template gives when it has no system role, for example LM Studio's
         * "Error rendering prompt with jinja template: System role not supported" or a llama.cpp
         * "Conversation roles must alternate user/assistant". Needs a 4xx/5xx status and the word "system" or the
         * alternation complaint, so ordinary bad requests and server outages are not retried.
         */
        internal fun rejectsSystemRole(httpStatus: Int?, body: String): Boolean {
            if (httpStatus == null || httpStatus !in 400..599) return false
            val text = body.lowercase()
            val mentionsSystemRole = text.contains("system") &&
                (text.contains("role") || text.contains("template") || text.contains("jinja") || text.contains("prompt"))
            val alternation = text.contains("roles must alternate")
            return mentionsSystemRole || alternation
        }

        /**
         * Puts the system text at the top of the first user turn and drops the system messages, which is what
         * Gemma's own chat template does. Nothing changes when there is no system text or no user turn.
         */
        internal fun withInstructionsInFirstUserTurn(request: ChatDispatchRequest): ChatDispatchRequest {
            val instructions = request.messages
                .filter { it.role == MessageRole.SYSTEM }
                .map { it.content.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n\n")
            val firstUser = request.messages.indexOfFirst { it.role == MessageRole.USER }
            if (firstUser < 0) return request
            val folded = request.messages.mapIndexedNotNull { index, msg ->
                when {
                    msg.role == MessageRole.SYSTEM -> null
                    index == firstUser && instructions.isNotEmpty() ->
                        msg.copy(content = if (msg.content.isBlank()) instructions else "$instructions\n\n${msg.content}")
                    else -> msg
                }
            }
            return request.copy(messages = folded)
        }

        /** Shown on the failed message when no server address is saved yet. */
        const val NOT_CONNECTED_MESSAGE =
            "Orbit isn't connected to an AI yet. Your message is saved: tap Set up, then Retry."

        /** Shown when an internet server is saved without its access token (servers on this PC or Wi-Fi skip it). */
        const val NEEDS_TOKEN_MESSAGE =
            "This server needs an access token. Add it in Set up, then tap Retry. Your message is saved."

        /** A model server on the home Wi-Fi asked for a key (401/403); Orbit never sends one over plain http. */
        const val WIFI_KEY_REFUSED_MESSAGE =
            "This server wants an access key. Orbit doesn't send keys over plain Wi-Fi, where others on the network " +
                "could read them. Turn off the server's key requirement, or connect over USB instead."

        fun wifiUnreachableMessage(baseUrl: String): String =
            "Couldn't reach $baseUrl over Wi-Fi. Check that the phone and PC are on the same Wi-Fi and the server " +
                "is running. LM Studio: turn on \"Serve on Local Network\". Ollama: set OLLAMA_HOST=0.0.0.0. " +
                "Windows may ask to allow it: choose Private networks only."

        /** Where a Wi-Fi model server is dialled: four address bytes, the port and any path prefix (no trailing /). */
        internal class WifiModelTarget(val address: ByteArray, val port: Int, val basePath: String)

        /**
         * Plain http to a private IPv4 literal (10/8, 172.16/12, 192.168/16, 100.64/10) that is not loopback/emulator:
         * the user's LM Studio/Ollama on the PC over the home Wi-Fi. No DNS, no user info, no query. Loopback and
         * 10.0.2.2 keep the normal HttpURLConnection path, which Android's cleartext config allows for them only.
         */
        internal fun wifiModelTarget(baseUrl: String): WifiModelTarget? {
            if (isLoopbackOrEmulatorHost(baseUrl)) return null
            val uri = runCatching { URI(baseUrl.trim()) }.getOrNull() ?: return null
            if (!uri.scheme.equals("http", ignoreCase = true)) return null
            if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
            val address = CompanionUrlSafety.privateIpv4(uri.host ?: return null) ?: return null
            val port = if (uri.port == -1) 80 else uri.port
            if (port !in 1..65535) return null
            val path = uri.rawPath.orEmpty().trimEnd('/')
            if (path.any { it.code < 0x21 || it.code > 0x7e }) return null
            return WifiModelTarget(address, port, path)
        }

        /** True for a plain-http model server on the home Wi-Fi (see [wifiModelTarget]); it never gets the key. */
        fun isWifiModelHost(baseUrl: String): Boolean = wifiModelTarget(baseUrl) != null

        /**
         * Model ids from a /v1/models answer. Takes the OpenAI shape {"data":[{"id":...}]}, a bare array of models
         * (Together) and {"models":[{"id" or "name":...}]}. Null when the body is none of these.
         */
        internal fun openRouterKeyStatus(body: String): String? {
            val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
            val data = root["data"] as? JsonObject ?: return null
            val free = data["is_free_tier"] as? JsonPrimitive ?: return null
            if (free.isString) return null
            return when (free.content) {
                "true" -> "OpenRouter key accepted (free tier). Choose a free model; inference and limits are untested."
                "false" -> "OpenRouter key accepted. Model access and inference are untested; paid models may incur charges."
                else -> null
            }
        }

        internal fun parseOpenRouterReasoningModels(body: String): Set<String> {
            val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptySet()
            val entries = root["data"] as? JsonArray ?: return emptySet()
            return entries.mapNotNull { entry ->
                val obj = entry as? JsonObject ?: return@mapNotNull null
                val params = obj["supported_parameters"] as? JsonArray ?: return@mapNotNull null
                if (params.none { (it as? JsonPrimitive)?.contentOrNull == "reasoning" }) return@mapNotNull null
                (obj["id"] as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
            }.take(MAX_LISTED_MODELS).toSet()
        }

        internal fun parseModelIds(body: String): List<String>? {
            val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return null
            fun idsOf(items: JsonArray): List<String> = items.mapNotNull { item ->
                when (item) {
                    is JsonObject -> ((item["id"] ?: item["name"]) as? JsonPrimitive)?.contentOrNull
                    is JsonPrimitive -> item.contentOrNull
                    else -> null
                }
            }
            if (root is JsonArray) return idsOf(root)
            if (root !is JsonObject) return null
            return when (val raw = root["data"]) {
                is JsonArray -> idsOf(raw)
                // Ollama with no model pulled answers {"object":"list","data":null} (a nil Go slice encodes as null).
                // That is a valid but empty list: it gets the "lists no models" diagnosis, not "not OpenAI-compatible".
                is JsonNull -> if ((root["object"] as? JsonPrimitive)?.contentOrNull == "list") emptyList() else null
                else -> (root["models"] as? JsonArray)?.let { idsOf(it) }
            }
        }

        /**
         * Parts of a model id that mark a model chat can't use: embeddings, speech, transcription, image and video
         * makers, moderation and reranking, realtime-only and old completion-only models. Lists from OpenAI, Groq or
         * LM Studio mix these in, and picking one only ends in a confusing error.
         */
        private val NON_CHAT_MARKERS = listOf(
            "embed", "whisper", "tts", "dall-e", "moderation", "rerank", "transcribe", "realtime", "sora",
            "gpt-image", "babbage-002", "davinci-002", "llama-guard", "prompt-guard", "-audio-", "computer-use"
        )

        /** True when the id looks like a model that can answer a chat (see [NON_CHAT_MARKERS]). */
        fun isChatModelId(id: String): Boolean {
            val lower = id.lowercase()
            return NON_CHAT_MARKERS.none { lower.contains(it) }
        }

        /** Trimmed, non-blank, chat-capable, de-duplicated, case-insensitively sorted and capped model ids. */
        fun normalizeModelIds(ids: List<String>): List<String> =
            ids.map { it.trim() }
                .filter { it.isNotEmpty() && isChatModelId(it) }
                .distinct()
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
                .take(MAX_LISTED_MODELS)

        /** The online service at [baseUrl] by name ("OpenAI"), or null for the user's own server. */
        fun serviceNameOf(baseUrl: String): String? =
            com.hydrogen.ai.model.ProviderPresets.serviceNameFor(ProviderRoute.GEMINI_HYDROGEN_RELAY, baseUrl)

        /** "192.168.1.20:1234" or "api.example.com" for messages; never the path or anything after it. */
        internal fun hostAndPort(baseUrl: String): String {
            val uri = runCatching { URI(baseUrl.trim()) }.getOrNull()
            val host = uri?.host ?: return "that address"
            return if (uri.port > 0) "$host:${uri.port}" else host
        }

        /**
         * Loopback/emulator hosts only (same set as CompanionUrlSafety); LAN/remote hosts never qualify.
         * Only these may take the /v1/models health fallback or chat without a bearer token (e.g. LM Studio).
         */
        fun isLoopbackOrEmulatorHost(baseUrl: String): Boolean {
            val host = runCatching { URL(baseUrl).host }.getOrNull()?.lowercase()?.trim('[', ']') ?: return false
            return host in LOCAL_MODEL_HOSTS
        }

        /**
         * A non-local host may get the /v1/models health probe only over https and only with the user's key, i.e.
         * exactly the request chat would make anyway. Plain-http LAN hosts keep the stricter no-fallback rule.
         */
        fun remoteModelProbeAllowed(baseUrl: String, token: String): Boolean {
            if (token.isBlank() || isLoopbackOrEmulatorHost(baseUrl)) return false
            val url = runCatching { URL(baseUrl) }.getOrNull() ?: return false
            return url.protocol.equals("https", ignoreCase = true) && !url.host.isNullOrBlank()
        }
    }
}

