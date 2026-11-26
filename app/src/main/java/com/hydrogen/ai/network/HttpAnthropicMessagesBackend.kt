package com.hydrogen.ai.network

import com.hydrogen.ai.data.ImageLoadResult
import com.hydrogen.ai.data.ImagePayloadLoader
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.ProviderError
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.ProviderPresets
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Chat in the Anthropic Messages format: Claude models on api.anthropic.com with the user's own key, and any other
 * service that offers the same format ("Another service", Anthropic format).
 *
 * Written from Anthropic's public API reference (POST /v1/messages, GET /v1/models, server-sent events). No SDK code.
 * The key only ever goes to the address the user saved, and only over https (plain http is for this device only).
 */
class HttpAnthropicMessagesBackend(
    private val imageLoader: ImagePayloadLoader? = null,
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 120_000
) : ChatBackend {

    override suspend fun checkHealth(config: RelayConfig, bearerToken: String?): RelayHealthResult =
        withContext(Dispatchers.IO) {
            val baseUrl = baseUrlOf(config)
            val key = bearerToken?.trim().orEmpty()
            val service = serviceNameOf(baseUrl)
            preflight(baseUrl, key, service)?.let { return@withContext RelayHealthResult.Failure(it) }
            val model = config.modelId.trim()
            var conn: HttpURLConnection? = null
            try {
                conn = open("$baseUrl/v1/models?limit=1000", "GET", key, baseUrl)
                val code = conn.responseCode
                val body = readBody(conn, code >= 400)
                if (code !in 200..299) {
                    return@withContext RelayHealthResult.Failure(
                        RelayErrorClassifier.classifyHttpError(
                            code, body, conn.getHeaderField("Retry-After"), ProviderRoute.ANTHROPIC_MESSAGES, service
                        )
                    )
                }
                val ids = HttpGeminiHydrogenRelayBackend.parseModelIds(body).orEmpty()
                val who = service ?: "The service"
                val detail = when {
                    model.isEmpty() -> "$who accepted the key. Pick a model to finish setting up."
                    ids.isEmpty() || ids.any { it.equals(model, ignoreCase = true) } ->
                        "$who accepted the key. '$model' is ready to use."
                    else -> "$who accepted the key, but '$model' isn't in the list this key can use. Pick a model from the list."
                }
                RelayHealthResult.Reachable(authenticated = key.isNotEmpty(), detail = detail)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: IOException) {
                RelayHealthResult.Failure(NetworkErrorClassifier.classify(e, baseUrl, service))
            } finally {
                conn?.disconnect()
            }
        }

    override suspend fun listModels(config: RelayConfig, bearerToken: String?): ModelListResult =
        withContext(Dispatchers.IO) {
            val baseUrl = baseUrlOf(config)
            val key = bearerToken?.trim().orEmpty()
            val service = serviceNameOf(baseUrl)
            preflight(baseUrl, key, service)?.let { return@withContext ModelListResult.Failure(it.userMessage) }
            val who = service ?: "The server at ${HttpGeminiHydrogenRelayBackend.hostAndPort(baseUrl)}"
            val collected = mutableListOf<String>()
            var afterId: String? = null
            try {
                // Anthropic pages its model list; ten pages of 1000 is far more than any service offers.
                for (page in 0 until 10) {
                    val query = "limit=1000" + (afterId?.let { "&after_id=" + URLEncoder.encode(it, "UTF-8") } ?: "")
                    val conn = open("$baseUrl/v1/models?$query", "GET", key, baseUrl)
                    try {
                        val code = conn.responseCode
                        val body = readBody(conn, code >= 400)
                        if (code !in 200..299) {
                            return@withContext ModelListResult.Failure(
                                RelayErrorClassifier.classifyHttpError(
                                    code, body, conn.getHeaderField("Retry-After"), ProviderRoute.ANTHROPIC_MESSAGES, service
                                ).userMessage
                            )
                        }
                        val ids = HttpGeminiHydrogenRelayBackend.parseModelIds(body)
                            ?: return@withContext ModelListResult.Failure(
                                "$who answered, but not with a model list Orbit can read. Type the model name instead."
                            )
                        collected += ids
                        val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                        val hasMore = (root?.get("has_more") as? JsonPrimitive)?.booleanOrNull == true
                        val lastId = (root?.get("last_id") as? JsonPrimitive)?.contentOrNull
                        if (!hasMore || lastId.isNullOrBlank() || lastId == afterId) break
                        afterId = lastId
                    } finally {
                        conn.disconnect()
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: IOException) {
                return@withContext ModelListResult.Failure(NetworkErrorClassifier.classify(e, baseUrl, service).userMessage)
            }
            // Keep the service's order (newest first for Anthropic) rather than sorting by name.
            val ids = collected.map { it.trim() }
                .filter { it.isNotEmpty() && HttpGeminiHydrogenRelayBackend.isChatModelId(it) }
                .distinct()
                .take(MAX_LISTED_MODELS)
            if (ids.isEmpty()) {
                ModelListResult.Failure("$who lists no chat models for this key. Type the model name instead.")
            } else {
                ModelListResult.Success(ids)
            }
        }

    override suspend fun sendChat(
        request: ChatDispatchRequest,
        config: RelayConfig,
        bearerToken: String?,
        onStreamDelta: suspend (StreamDelta) -> Unit
    ): ChatDispatchResult = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlOf(config)
        val key = bearerToken?.trim().orEmpty()
        val service = serviceNameOf(baseUrl)
        preflight(baseUrl, key, service)?.let { return@withContext ChatDispatchResult.Failure(it) }
        val model = request.modelId.trim().ifEmpty { config.modelId.trim() }
        if (model.isEmpty()) {
            return@withContext ChatDispatchResult.Failure(
                ProviderError(
                    category = ProviderErrorCategory.UNCONFIGURED,
                    userMessage = "Pick a model in Models and providers first.",
                    sourceLayer = "client"
                )
            )
        }
        val effort = com.hydrogen.ai.model.ReasoningPolicy.anthropicEffort(baseUrl, model, config.reasoningMode)
        val built = buildPayload(request, model, safe = false, effort = effort)
        built.error?.let { return@withContext ChatDispatchResult.Failure(it) }
        val first = dispatchOnce(built, model, request.stream, baseUrl, key, service, onStreamDelta, allowRetry = effort == null)
        if (first is Attempt.RetrySafer) {
            // The service turned down thinking or the reply length (an older model, or another service using the
            // format). Ask once more without them rather than showing an error the person can't fix.
            val safer = buildPayload(request, model, safe = true)
            safer.error?.let { return@withContext ChatDispatchResult.Failure(it) }
            when (val second = dispatchOnce(safer, model, request.stream, baseUrl, key, service, onStreamDelta, allowRetry = false)) {
                is Attempt.Done -> second.result
                is Attempt.RetrySafer -> second.fallback
            }
        } else {
            (first as Attempt.Done).result
        }
    }

    private sealed interface Attempt {
        data class Done(val result: ChatDispatchResult) : Attempt
        data class RetrySafer(val fallback: ChatDispatchResult) : Attempt
    }

    private suspend fun dispatchOnce(
        built: PayloadBuildResult,
        model: String,
        stream: Boolean,
        baseUrl: String,
        key: String,
        service: String?,
        onStreamDelta: suspend (StreamDelta) -> Unit,
        allowRetry: Boolean = true
    ): Attempt {
        var conn: HttpURLConnection? = null
        return try {
            conn = open("$baseUrl/v1/messages", "POST", key, baseUrl).apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", if (stream) "text/event-stream" else "application/json")
            }
            val bodyJson = built.body!!.let { body ->
                if (stream) JsonObject(body + ("stream" to JsonPrimitive(true))) else body
            }
            conn.outputStream.use { it.write(bodyJson.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val errorBody = readBody(conn, true)
                val error = RelayErrorClassifier.classifyHttpError(
                    code, errorBody, conn.getHeaderField("Retry-After"), ProviderRoute.ANTHROPIC_MESSAGES, service
                )
                val failure = ChatDispatchResult.Failure(error.copy(technicalDetail = SecretRedactor.redact(error.technicalDetail, listOf(key))))
                return if (allowRetry && shouldRetrySafer(code, errorBody, built)) Attempt.RetrySafer(failure) else Attempt.Done(failure)
            }
            val type = conn.contentType?.lowercase().orEmpty()
            val outcome = if (stream && type.contains("text/event-stream")) {
                readEventStream(conn.inputStream, service, onStreamDelta)
            } else {
                parseCompleteReply(readBody(conn, false), service)
            }
            Attempt.Done(outcome.toResult(code, model, built.usedVision, wasStreamed = stream && type.contains("text/event-stream"), service))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: IOException) {
            Attempt.Done(ChatDispatchResult.Failure(NetworkErrorClassifier.classify(e, baseUrl, service, forChat = true)))
        } finally {
            conn?.disconnect()
        }
    }

    private fun shouldRetrySafer(code: Int, body: String, built: PayloadBuildResult): Boolean {
        if (code != 400 || !(built.sentThinking || built.maxTokens > SAFE_MAX_TOKENS)) return false
        val lower = body.lowercase()
        return lower.contains("thinking") || lower.contains("max_tokens") || lower.contains("display") || lower.contains("output_config") || lower.contains("effort")
    }

    internal data class PayloadBuildResult(
        val body: JsonObject? = null,
        val usedVision: Boolean = false,
        val error: ProviderError? = null,
        val sentThinking: Boolean = false,
        val maxTokens: Int = 0
    )

    /**
     * The request body without "stream". [safe] leaves out thinking and asks for a shorter reply, for services that
     * turn those down. Same-role turns are merged and the chat starts and ends with the person's turn, as the format
     * requires; earlier replies go back as plain text (no thinking blocks, which belong to one service only).
     */
    internal suspend fun buildPayload(request: ChatDispatchRequest, model: String, safe: Boolean, effort: String? = null): PayloadBuildResult {
        var usedVision = false
        val systemTexts = mutableListOf<String>()
        val turns = mutableListOf<Pair<String, MutableList<JsonObject>>>()

        for (msg in request.messages) {
            if (msg.role == MessageRole.SYSTEM) {
                if (msg.content.isNotBlank()) systemTexts += msg.content
                continue
            }
            val role = if (msg.role == MessageRole.USER) "user" else "assistant"
            val blocks = mutableListOf<JsonObject>()
            if (msg.role == MessageRole.USER) {
                for (att in msg.attachments.filterNot { it.id in request.omittedAttachmentIds }) {
                    val loader = imageLoader ?: return PayloadBuildResult(
                        error = attachmentError("\"${att.displayName}\" couldn't be attached. Remove it and try again.", "no image loader")
                    )
                    when (val loaded = loader.loadImage(att.localUri, att.mimeType)) {
                        is ImageLoadResult.Success -> {
                            val mime = normalizeImageType(loaded.payload.mimeType) ?: return PayloadBuildResult(
                                error = attachmentError(
                                    "\"${att.displayName}\" is a kind of image this model can't read. Use a JPEG, PNG, GIF or WebP image.",
                                    "unsupported image type ${loaded.payload.mimeType}"
                                )
                            )
                            usedVision = true
                            blocks += buildJsonObject {
                                put("type", "image")
                                put("source", buildJsonObject {
                                    put("type", "base64")
                                    put("media_type", mime)
                                    put("data", loaded.payload.base64Data)
                                })
                            }
                        }
                        is ImageLoadResult.MissingOrUnreadable -> return PayloadBuildResult(
                            error = attachmentError(
                                "\"${att.displayName}\" couldn't be read, so nothing was sent. Remove it or attach it again.",
                                loaded.reason
                            )
                        )
                    }
                }
            }
            if (msg.content.isNotBlank()) {
                blocks += buildJsonObject {
                    put("type", "text")
                    put("text", if (msg.role == MessageRole.ASSISTANT) msg.content.trimEnd() else msg.content)
                }
            }
            if (blocks.isEmpty()) continue
            val last = turns.lastOrNull()
            if (last != null && last.first == role) last.second += blocks else turns += role to blocks
        }
        while (turns.isNotEmpty() && turns.first().first == "assistant") turns.removeAt(0)
        while (turns.isNotEmpty() && turns.last().first == "assistant") turns.removeAt(turns.lastIndex)
        if (turns.isEmpty()) {
            return PayloadBuildResult(
                error = ProviderError(
                    category = ProviderErrorCategory.BAD_REQUEST_400,
                    userMessage = "There's nothing to send yet. Type a message first.",
                    technicalDetail = "no user turn",
                    sourceLayer = "client"
                )
            )
        }
        val thinking = !safe && supportsAdaptiveThinking(model)
        val maxTokens = when {
            safe -> SAFE_MAX_TOKENS
            thinking && effort == "max" -> 64000
            thinking && effort == "xhigh" -> 32000
            else -> maxTokensFor(model)
        }
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            if (systemTexts.isNotEmpty()) put("system", systemTexts.joinToString("\n\n"))
            put("messages", buildJsonArray {
                for ((role, blocks) in turns) {
                    add(buildJsonObject {
                        put("role", role)
                        put("content", JsonArray(blocks))
                    })
                }
            })
            if (thinking) {
                put("thinking", buildJsonObject {
                    put("type", "adaptive")
                    put("display", "summarized")
                })
                if (effort != null) put("output_config", buildJsonObject { put("effort", effort) })
            }
        }
        return PayloadBuildResult(body = body, usedVision = usedVision, sentThinking = thinking, maxTokens = maxTokens)
    }

    private fun attachmentError(message: String, detail: String) = ProviderError(
        category = ProviderErrorCategory.BAD_REQUEST_400,
        userMessage = message,
        technicalDetail = detail,
        sourceLayer = "client"
    )

    /** What one reply came to, streamed or not. */
    internal data class ReplyOutcome(
        val text: String,
        val thinking: String,
        val stopReason: String?,
        val error: ProviderError? = null,
        val unreadable: String? = null,
        /** The stream closed before the service said the reply was finished. */
        val interrupted: Boolean = false
    ) {
        fun toResult(code: Int, model: String, usedVision: Boolean, wasStreamed: Boolean, service: String?): ChatDispatchResult {
            if (error != null) return ChatDispatchResult.Failure(error)
            if (interrupted && text.isNotBlank()) {
                // Never pass a cut-off reply off as complete. The chat keeps the text that arrived.
                return ChatDispatchResult.Failure(
                    ProviderError(
                        category = ProviderErrorCategory.INTERRUPTED,
                        httpStatus = code,
                        userMessage = "The reply from ${service ?: "the service"} was cut off before it finished. " +
                            "What arrived is kept above. Tap Retry for a full answer.",
                        technicalDetail = "stream ended without message_stop or stop_reason after ${text.length} characters",
                        sourceLayer = "provider"
                    )
                )
            }
            if (text.isBlank()) {
                val (category, message) = when {
                    stopReason == "refusal" ->
                        ProviderErrorCategory.CONTENT_FILTERED to SseStreamReader.filteredMessage(service)
                    stopReason == "max_tokens" -> ProviderErrorCategory.UNKNOWN_PROTOCOL to
                        "The model spent its whole reply on thinking and wrote no answer. Tap Retry, or ask for something shorter."
                    else -> ProviderErrorCategory.UNKNOWN_PROTOCOL to SseStreamReader.emptyReplyMessage(service)
                }
                return ChatDispatchResult.Failure(
                    ProviderError(
                        category = category,
                        httpStatus = code,
                        userMessage = message,
                        technicalDetail = unreadable ?: "stop_reason=${stopReason ?: "none"}",
                        sourceLayer = "provider"
                    )
                )
            }
            return ChatDispatchResult.Success(
                assistantText = text,
                modelId = model,
                // No thinking signature is kept: it is only valid for Anthropic, and the chat may move to another service.
                turnMetadata = RelayResponseParser.toMetadata(
                    thoughtSignature = null,
                    thinkingSummary = thinking,
                    toolCalls = emptyList(),
                    finishReason = stopReason,
                    isTruncated = stopReason == "max_tokens"
                ),
                usedVision = usedVision,
                wasStreamed = wasStreamed
            )
        }
    }

    private fun open(url: String, method: String, key: String, baseUrl: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("anthropic-version", API_VERSION)
            if (method == "GET") setRequestProperty("Accept", "application/json")
            if (key.isNotEmpty()) {
                setRequestProperty("x-api-key", key)
                // Services that copy the format often expect the key the OpenAI way too. Same saved address only.
                if (!isAnthropicHost(baseUrl)) setRequestProperty("Authorization", "Bearer $key")
            }
        }

    private fun readBody(conn: HttpURLConnection, isError: Boolean): String {
        val stream = if (isError) conn.errorStream else conn.inputStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    internal companion object {
        const val API_VERSION = "2023-06-01"
        const val DEFAULT_MAX_TOKENS = 16_000
        const val SAFE_MAX_TOKENS = 4_096
        private const val MAX_LISTED_MODELS = 2_000
        private val json = Json { ignoreUnknownKeys = true }
        private val CLAUDE_VERSION = Regex("""^claude-(opus|sonnet|haiku)-(\d+)(?:-(\d{1,2}))?(?:-|$)""")
        private val SUPPORTED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp")

        fun baseUrlOf(config: RelayConfig): String = ProviderPresets.normalizeBaseUrl(config.effectiveBaseUrl)

        fun serviceNameOf(baseUrl: String): String? =
            ProviderPresets.serviceNameFor(ProviderRoute.ANTHROPIC_MESSAGES, baseUrl)

        fun isAnthropicHost(baseUrl: String): Boolean =
            runCatching { URL(baseUrl).host }.getOrNull()?.lowercase() == "api.anthropic.com"

        /**
         * Claude models that take adaptive thinking: Opus and Sonnet 4.6 and later, Haiku 5.5 and later, and Fable.
         * A dated id such as "claude-opus-4-20250514" is version 4, not 4.20. Other models get no thinking setting.
         */
        fun supportsAdaptiveThinking(model: String): Boolean {
            val id = model.trim().lowercase().substringAfterLast('/')
            if (id.startsWith("claude-fable")) return true
            val match = CLAUDE_VERSION.find(id) ?: return false
            val family = match.groupValues[1]
            val major = match.groupValues[2].toIntOrNull() ?: return false
            val minor = match.groupValues[3].toIntOrNull() ?: 0
            return when (family) {
                "haiku" -> major > 5 || (major == 5 && minor >= 5)
                else -> major >= 5 || (major == 4 && minor >= 6)
            }
        }

        /** Claude 3 models (except 3.7) cap replies at 4,096 tokens; everything else gets room for thinking. */
        fun maxTokensFor(model: String): Int {
            val id = model.trim().lowercase().substringAfterLast('/')
            return if (id.startsWith("claude-3-") && !id.startsWith("claude-3-7")) SAFE_MAX_TOKENS else DEFAULT_MAX_TOKENS
        }

        /** The format's image types; "image/jpg" is accepted as JPEG. Null for anything else (HEIC, BMP...). */
        fun normalizeImageType(mime: String): String? {
            val lower = mime.trim().lowercase()
            val fixed = if (lower == "image/jpg") "image/jpeg" else lower
            return fixed.takeIf { it in SUPPORTED_IMAGE_TYPES }
        }

        /**
         * Checks that run before anything is sent: a usable address, https for anything off this device (the key
         * never travels in the clear), and a key for anything that isn't on this device.
         */
        fun preflight(baseUrl: String, key: String, service: String?): ProviderError? {
            val url = runCatching { URL(baseUrl) }.getOrNull()
            val scheme = url?.protocol?.lowercase()
            if (url == null || url.host.isNullOrBlank() || (scheme != "https" && scheme != "http")) {
                return ProviderError(
                    category = ProviderErrorCategory.UNCONFIGURED,
                    userMessage = "Enter the service's address, starting with https://, in Models and providers.",
                    technicalDetail = "unusable address",
                    sourceLayer = "client"
                )
            }
            val onThisDevice = HttpGeminiHydrogenRelayBackend.isLoopbackOrEmulatorHost(baseUrl)
            if (scheme == "http" && !onThisDevice) {
                return ProviderError(
                    category = ProviderErrorCategory.UNCONFIGURED,
                    userMessage = "Orbit only sends your key to a secure address. Change the address to start with https://.",
                    technicalDetail = "plain http to ${HttpGeminiHydrogenRelayBackend.hostAndPort(baseUrl)} refused",
                    sourceLayer = "client"
                )
            }
            if (key.isEmpty() && !onThisDevice) {
                return ProviderError(
                    category = ProviderErrorCategory.UNCONFIGURED,
                    userMessage = "Add your ${service ?: "service's"} API key in Models and providers.",
                    technicalDetail = "no key",
                    sourceLayer = "client"
                )
            }
            return null
        }

        /** A whole (not streamed) reply: {"content":[{"type":"text","text":...}], "stop_reason":...}. */
        fun parseCompleteReply(body: String, service: String?): ReplyOutcome {
            val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                ?: return ReplyOutcome("", "", null, unreadable = SecretRedactor.redact(body.take(400)).ifBlank { "(empty reply)" })
            if (root["type"].str() == "error" || root["error"] is JsonObject) {
                RelayErrorClassifier.classifySseErrorPayload(body, ProviderRoute.ANTHROPIC_MESSAGES, service)?.let {
                    return ReplyOutcome("", "", null, error = it)
                }
            }
            val text = StringBuilder()
            val thinking = StringBuilder()
            (root["content"] as? JsonArray)?.forEach { block ->
                val obj = block as? JsonObject ?: return@forEach
                when (obj["type"].str()) {
                    "text" -> text.append(obj["text"].str().orEmpty())
                    "thinking" -> thinking.append(obj["thinking"].str().orEmpty())
                }
            }
            return ReplyOutcome(
                text = text.toString(),
                thinking = thinking.toString(),
                stopReason = root["stop_reason"].str(),
                unreadable = if (root["content"] == null) SecretRedactor.redact(body.take(400)) else null
            )
        }

        /** Reads Anthropic's event stream, passing text and thinking on as they arrive. */
        suspend fun readEventStream(
            input: InputStream,
            service: String?,
            onDelta: suspend (StreamDelta) -> Unit
        ): ReplyOutcome {
            val text = StringBuilder()
            val thinking = StringBuilder()
            var stopReason: String? = null
            var error: ProviderError? = null
            var sawStop = false

            suspend fun handle(payload: String): Boolean {
                val event = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return true
                when (event["type"].str()) {
                    "content_block_start" -> {
                        val block = event["content_block"] as? JsonObject ?: return true
                        val piece = when (block["type"].str()) {
                            "text" -> block["text"].str().orEmpty()
                            else -> ""
                        }
                        val thought = if (block["type"].str() == "thinking") block["thinking"].str().orEmpty() else ""
                        if (piece.isNotEmpty() || thought.isNotEmpty()) {
                            text.append(piece)
                            thinking.append(thought)
                            onDelta(StreamDelta(textDelta = piece, thinkingDelta = thought))
                        }
                    }
                    "content_block_delta" -> {
                        val delta = event["delta"] as? JsonObject ?: return true
                        when (delta["type"].str()) {
                            "text_delta" -> delta["text"].str()?.takeIf { it.isNotEmpty() }?.let {
                                text.append(it)
                                onDelta(StreamDelta(textDelta = it))
                            }
                            "thinking_delta" -> delta["thinking"].str()?.takeIf { it.isNotEmpty() }?.let {
                                thinking.append(it)
                                onDelta(StreamDelta(thinkingDelta = it))
                            }
                        }
                    }
                    "message_delta" -> {
                        val reason = (event["delta"] as? JsonObject)?.get("stop_reason").str()
                        if (!reason.isNullOrBlank()) {
                            stopReason = reason
                            onDelta(StreamDelta(finishReason = reason))
                        }
                    }
                    "error" -> {
                        error = RelayErrorClassifier.classifySseErrorPayload(payload, ProviderRoute.ANTHROPIC_MESSAGES, service)
                        return false
                    }
                    "message_stop" -> {
                        sawStop = true
                        return false
                    }
                }
                return true
            }

            input.bufferedReader(Charsets.UTF_8).use { reader ->
                val dataLines = mutableListOf<String>()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine()
                    if (line == null || line.isBlank()) {
                        if (dataLines.isNotEmpty()) {
                            val payload = dataLines.joinToString("\n")
                            dataLines.clear()
                            if (!handle(payload)) break
                        }
                        if (line == null) break
                    } else if (line.startsWith("data:")) {
                        dataLines += line.removePrefix("data:").trimStart()
                    }
                }
            }
            return ReplyOutcome(
                text.toString(),
                thinking.toString(),
                stopReason,
                error,
                interrupted = error == null && !sawStop && stopReason == null
            )
        }

        private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
    }
}
