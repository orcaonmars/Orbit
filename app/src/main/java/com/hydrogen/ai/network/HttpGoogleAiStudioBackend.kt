package com.hydrogen.ai.network

import com.hydrogen.ai.data.ImageLoadResult
import com.hydrogen.ai.data.ImagePayloadLoader
import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.GoogleModelCatalog
import com.hydrogen.ai.model.GroundingSources
import com.hydrogen.ai.model.WebSource
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.ProviderError
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class HttpGoogleAiStudioBackend(
    private val imageLoader: ImagePayloadLoader? = null,
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 60_000
) : ChatBackend {

    private val json = Json { ignoreUnknownKeys = true }

    internal companion object {
        const val SERVICE_NAME = "Google"

        /** "gemma-3-27b-it" and "models/gemma-3-27b-it" are Gemma; Gemini models are not. */
        fun isGemmaModel(model: String): Boolean = model.trim().lowercase().substringAfterLast('/').startsWith("gemma")
    }

    override suspend fun checkHealth(config: RelayConfig, bearerToken: String?): RelayHealthResult =
        withContext(Dispatchers.IO) {
            val apiKey = bearerToken?.trim().orEmpty()
            if (apiKey.isEmpty()) {
                return@withContext RelayHealthResult.Failure(
                    ProviderError(
                        category = ProviderErrorCategory.UNCONFIGURED,
                        userMessage = "Enter a Google AI Studio API key before verifying.",
                        sourceLayer = "client"
                    )
                )
            }
            val baseUrl = config.effectiveBaseUrl
            val model = config.modelId.trim()
            var conn: HttpURLConnection? = null
            try {
                conn = (URL("$baseUrl/models/$model").openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("x-goog-api-key", apiKey)
                }
                val code = conn.responseCode
                val body = readStream(conn, code >= 400)
                if (code in 200..299) {
                    RelayHealthResult.Reachable(
                        authenticated = true,
                        detail = "Google AI Studio model '$model' metadata returned HTTP $code (auth verified; inference untested)."
                    )
                } else {
                    RelayHealthResult.Failure(
                        RelayErrorClassifier.classifyHttpError(
                            code, body, conn.getHeaderField("Retry-After"), ProviderRoute.GOOGLE_AI_STUDIO_DIRECT
                        )
                    )
                }
            } catch (e: IOException) {
                RelayHealthResult.Failure(NetworkErrorClassifier.classify(e, baseUrl, SERVICE_NAME))
            } finally {
                conn?.disconnect()
            }
        }

    /** Lists the chat models this key can use (a free metadata call; no tokens are spent). */
    override suspend fun listModels(config: RelayConfig, bearerToken: String?): ModelListResult =
        withContext(Dispatchers.IO) {
            val apiKey = bearerToken?.trim().orEmpty()
            if (apiKey.isEmpty()) {
                return@withContext ModelListResult.Failure("Enter your Gemini API key and tap Save first.")
            }
            val baseUrl = config.effectiveBaseUrl
            var conn: HttpURLConnection? = null
            try {
                conn = (URL("$baseUrl/models?pageSize=1000").openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("x-goog-api-key", apiKey)
                }
                val code = conn.responseCode
                val body = readStream(conn, code >= 400)
                if (code !in 200..299) {
                    return@withContext ModelListResult.Failure(
                        RelayErrorClassifier.classifyHttpError(
                            code, body, conn.getHeaderField("Retry-After"), ProviderRoute.GOOGLE_AI_STUDIO_DIRECT
                        ).userMessage
                    )
                }
                val ids = GoogleModelCatalog.parse(body)
                if (ids.isEmpty()) {
                    ModelListResult.Failure("Google returned no chat models for this key. Type the model name instead.")
                } else {
                    ModelListResult.Success(ids)
                }
            } catch (e: IOException) {
                ModelListResult.Failure(NetworkErrorClassifier.classify(e, baseUrl, SERVICE_NAME).userMessage)
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
        val apiKey = bearerToken?.trim().orEmpty()
        if (apiKey.isEmpty()) {
            return@withContext ChatDispatchResult.Failure(
                ProviderError(
                    category = ProviderErrorCategory.UNCONFIGURED,
                    userMessage = "Add your Google AI Studio API key in Set up, then tap Retry. Your message is saved.",
                    sourceLayer = "client"
                )
            )
        }
        val model = request.modelId.ifBlank { config.modelId }.trim()
        val isGemma = isGemmaModel(model)
        // Gemma models on AI Studio reject the Google Search tool; only ask Gemini models to search.
        val webSearch = config.webSearchEnabled && !isGemma
        val payloadBuild = buildPayload(request, webSearch, instructionsInFirstUserTurn = isGemma,
            thinkingLevel = com.hydrogen.ai.model.ReasoningPolicy.googleLevel(model, config.reasoningMode))
        if (payloadBuild.error != null) {
            return@withContext ChatDispatchResult.Failure(payloadBuild.error)
        }
        val baseUrl = config.effectiveBaseUrl
        val useStream = request.stream && config.streamingEnabled
        val endpoint = if (useStream) {
            "$baseUrl/models/$model:streamGenerateContent?alt=sse"
        } else {
            "$baseUrl/models/$model:generateContent"
        }
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "POST"
                doOutput = true
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", if (useStream) "text/event-stream, application/json" else "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
                setRequestProperty("X-Correlation-Id", request.correlationId)
            }
            conn.outputStream.use { it.write(payloadBuild.body!!.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = readStream(conn, true)
                return@withContext ChatDispatchResult.Failure(
                    RelayErrorClassifier.classifyHttpError(
                        code, errBody, conn.getHeaderField("Retry-After"), ProviderRoute.GOOGLE_AI_STUDIO_DIRECT
                    )
                )
            }
            var sources = emptyList<WebSource>()
            val collectSources: (String) -> Unit = { payload -> sources = GroundingSources.collect(payload, sources) }
            val result = SseStreamReader.handleSuccessResponse(
                conn = conn,
                useStream = useStream,
                model = model,
                usedVision = payloadBuild.usedVision,
                sourceLayer = "upstream_google",
                onStreamDelta = onStreamDelta,
                route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT,
                onRawEvent = if (webSearch) collectSources else null,
                serviceName = SERVICE_NAME
            )
            if (result is ChatDispatchResult.Success && sources.isNotEmpty()) {
                val footer = GroundingSources.toMarkdownFooter(sources)
                if (result.wasStreamed) onStreamDelta(StreamDelta(textDelta = footer))
                result.copy(assistantText = result.assistantText + footer)
            } else {
                result
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: IOException) {
            ChatDispatchResult.Failure(NetworkErrorClassifier.classify(e, baseUrl, SERVICE_NAME, forChat = true))
        } finally {
            conn?.disconnect()
        }
    }

    internal data class PayloadBuildResult(
        val body: JsonObject? = null,
        val usedVision: Boolean = false,
        val error: ProviderError? = null
    )

    /**
     * [instructionsInFirstUserTurn]: Gemma on AI Studio answers 400 "Developer instruction is not enabled" to a
     * systemInstruction, so for Gemma the instructions ride at the top of the first user turn instead.
     */
    internal suspend fun buildPayload(
        request: ChatDispatchRequest,
        webSearch: Boolean = false,
        instructionsInFirstUserTurn: Boolean = false,
        thinkingLevel: String? = null
    ): PayloadBuildResult {
        var usedVision = false
        val systemTexts = mutableListOf<String>()
        val contents = mutableListOf<JsonObject>()
        var instructionsPlaced = false
        val currentIndex = HistoryImages.newestUserIndex(request.messages)

        for ((msgIndex, msg) in request.messages.withIndex()) {
            if (msg.role == MessageRole.SYSTEM) {
                if (msg.content.isNotBlank()) systemTexts += msg.content
                continue
            }
            val role = if (msg.role == MessageRole.USER) "user" else "model"
            val parts = mutableListOf<JsonObject>()
            if (instructionsInFirstUserTurn && !instructionsPlaced && msg.role == MessageRole.USER && systemTexts.isNotEmpty()) {
                parts += buildJsonObject { put("text", systemTexts.joinToString("\n\n")) }
                instructionsPlaced = true
            }

            if (msg.role == MessageRole.ASSISTANT) {
                val meta = msg.turnMetadata
                if (msg.content.isNotBlank()) {
                    parts += buildJsonObject {
                        put("text", msg.content)
                        if (meta != null && !meta.thoughtSignature.isNullOrBlank() && meta.toolCalls.isEmpty()) {
                            put("thoughtSignature", meta.thoughtSignature)
                        }
                    }
                }
                meta?.toolCalls?.forEach { call ->
                    parts += buildJsonObject {
                        put(
                            "functionCall",
                            buildJsonObject {
                                put("name", call.name)
                                put("args", json.parseToJsonElement(call.argumentsJson))
                            }
                        )
                        val sig = call.thoughtSignature ?: meta.thoughtSignature
                        if (!sig.isNullOrBlank()) {
                            put("thoughtSignature", sig)
                        }
                    }
                }
            } else {
                if (msg.content.isNotBlank()) {
                    parts += buildJsonObject { put("text", msg.content) }
                }
                val includedAtts = msg.attachments.filterNot { it.id in request.omittedAttachmentIds }
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
                            parts += buildJsonObject {
                                put(
                                    "inlineData",
                                    buildJsonObject {
                                        put("mimeType", loaded.payload.mimeType)
                                        put("data", loaded.payload.base64Data)
                                    }
                                )
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
                if (leftOutPhoto) parts += buildJsonObject { put("text", HistoryImages.LEFT_OUT_NOTE) }
            }

            if (parts.isNotEmpty()) {
                contents += buildJsonObject {
                    put("role", role)
                    put("parts", JsonArray(parts))
                }
            }
        }

        val root = buildJsonObject {
            if (systemTexts.isNotEmpty() && !instructionsInFirstUserTurn) {
                put(
                    "systemInstruction",
                    buildJsonObject {
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", systemTexts.joinToString("\n\n")) }) })
                    }
                )
            }
            put("contents", JsonArray(contents))
            if (thinkingLevel != null) {
                put("generationConfig", buildJsonObject {
                    put("thinkingConfig", buildJsonObject { put("thinkingLevel", thinkingLevel) })
                })
            }
            if (webSearch) {
                put("tools", buildJsonArray { add(buildJsonObject { put("google_search", buildJsonObject {}) }) })
            }
        }
        return PayloadBuildResult(body = root, usedVision = usedVision)
    }

    private fun readStream(conn: HttpURLConnection, isError: Boolean): String {
        val stream = if (isError) conn.errorStream else conn.inputStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }
}