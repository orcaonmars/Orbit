package com.hydrogen.ai.network

import com.hydrogen.ai.model.GeminiTurnMetadata
import com.hydrogen.ai.model.ProviderError
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.ProviderRoute
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * OpenAI Responses API (POST {base}/v1/responses) for OpenAI-compatible servers that opt in. Written from OpenAI's
 * public Responses reference: input items, `output_text` deltas in server-sent events, and a terminal
 * `response.completed` / `response.incomplete` / `response.failed` event. No SDK code, no tools, no stored responses.
 *
 * Chat Completions stays the default; this file is only reached when a server's [com.hydrogen.ai.model.OpenAiProtocol]
 * is RESPONSES. Errors go through [RelayErrorClassifier] so auth, billing, 429 and model errors are the same as chat.
 */
internal object OpenAiResponsesProtocol {
    const val PATH = "responses"
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Chat-shaped messages (as built for Chat Completions: string content, or parts of "text" and "image_url") turned
     * into Responses input items. System messages keep their role; the Responses API accepts it in `input`.
     */
    fun inputItems(chatMessages: JsonArray): JsonArray = buildJsonArray {
        for (element in chatMessages) {
            val msg = element as? JsonObject ?: continue
            val role = (msg["role"] as? JsonPrimitive)?.contentOrNull ?: continue
            val content = msg["content"]
            add(buildJsonObject {
                put("role", role)
                if (content is JsonArray) {
                    put("content", JsonArray(content.mapNotNull { convertPart(it as? JsonObject) }))
                } else {
                    put("content", content ?: JsonPrimitive(""))
                }
            })
        }
    }

    private fun convertPart(part: JsonObject?): JsonObject? {
        if (part == null) return null
        return when ((part["type"] as? JsonPrimitive)?.contentOrNull) {
            "text" -> buildJsonObject {
                put("type", "input_text")
                put("text", part["text"] ?: JsonPrimitive(""))
            }
            "image_url" -> {
                val url = ((part["image_url"] as? JsonObject)?.get("url") as? JsonPrimitive)?.contentOrNull
                    ?: return null
                buildJsonObject {
                    put("type", "input_image")
                    put("image_url", url)
                    put("detail", "auto")
                }
            }
            else -> null
        }
    }

    /**
     * The POST body. `store` is false: Orbit resends the whole conversation each turn and does not want the provider
     * keeping a server-side copy. Reasoning is sent only when the caller already decided the model accepts it.
     */
    fun requestBody(
        model: String,
        stream: Boolean,
        input: JsonArray,
        maxOutputTokens: Int?,
        reasoningEffort: String?
    ): JsonObject = buildJsonObject {
        put("model", model)
        put("input", input)
        put("stream", stream)
        put("store", false)
        maxOutputTokens?.let { put("max_output_tokens", it) }
        reasoningEffort?.let { effort -> put("reasoning", buildJsonObject { put("effort", effort) }) }
    }

    /** One event from the stream, or the whole body of a non-streamed reply, reduced to what Orbit uses. */
    internal sealed interface ResponsesEvent {
        data class Text(val delta: String) : ResponsesEvent
        data class Thinking(val delta: String) : ResponsesEvent
        /** [incomplete] is true for `response.incomplete` (for example the output token limit was hit). */
        class Finished(val response: JsonObject?, val incomplete: Boolean) : ResponsesEvent
        class Failed(val error: ProviderError) : ResponsesEvent
        object Ignored : ResponsesEvent
    }

    /**
     * Reads one SSE data payload. The event type comes from the JSON "type" field, or from the SSE "event:" line
     * when the JSON has none. Unknown events (created, in_progress, output_item.*, content_part.*) are ignored.
     */
    internal fun parseEvent(payload: String, eventName: String?, serviceName: String?): ResponsesEvent {
        val trimmed = payload.trim()
        if (trimmed.isEmpty() || trimmed == "[DONE]") return ResponsesEvent.Ignored
        val obj = runCatching { json.parseToJsonElement(trimmed) as? JsonObject }.getOrNull()
            ?: return ResponsesEvent.Ignored
        val type = stringOf(obj["type"]) ?: eventName.orEmpty()
        return when (type) {
            "response.output_text.delta", "response.refusal.delta" ->
                stringOf(obj["delta"])?.takeIf { it.isNotEmpty() }?.let { ResponsesEvent.Text(it) } ?: ResponsesEvent.Ignored
            "response.reasoning_summary_text.delta", "response.reasoning_text.delta" ->
                stringOf(obj["delta"])?.takeIf { it.isNotEmpty() }?.let { ResponsesEvent.Thinking(it) } ?: ResponsesEvent.Ignored
            "response.completed" -> ResponsesEvent.Finished(obj["response"] as? JsonObject, incomplete = false)
            "response.incomplete" -> ResponsesEvent.Finished(obj["response"] as? JsonObject, incomplete = true)
            "response.failed" -> {
                val nested = (obj["response"] as? JsonObject)?.get("error")
                ResponsesEvent.Failed(errorFrom(nested ?: obj["error"], serviceName, FAILED_MESSAGE))
            }
            // A stream-level error event: {"type":"error","code":...,"message":...}.
            "error" -> ResponsesEvent.Failed(errorFrom(obj, serviceName, FAILED_MESSAGE))
            else -> ResponsesEvent.Ignored
        }
    }

    /** Maps an error object (or an "error" event) through the same classifier chat uses. Never null. */
    internal fun errorFrom(errorElement: JsonElement?, serviceName: String?, fallback: String): ProviderError {
        if (errorElement == null || errorElement == JsonNull) {
            return ProviderError(ProviderErrorCategory.UNKNOWN_PROTOCOL, userMessage = fallback, sourceLayer = "relay")
        }
        val payload = buildJsonObject { put("error", errorElement) }.toString()
        return RelayErrorClassifier.classifySseErrorPayload(payload, ProviderRoute.GEMINI_HYDROGEN_RELAY, serviceName)
            ?: ProviderError(ProviderErrorCategory.UNKNOWN_PROTOCOL, userMessage = fallback, sourceLayer = "relay")
    }

    /** A non-2xx answer. A 404/405/501 on the Responses path is an older or partial server, named as such. */
    fun httpError(code: Int, body: String, retryAfter: String?, serviceName: String?): ProviderError {
        if ((code == 404 || code == 405 || code == 501) && !body.lowercase().contains("model")) {
            return ProviderError(
                category = ProviderErrorCategory.UNSUPPORTED_CAPABILITY,
                httpStatus = code,
                userMessage = "${serviceName ?: "This server"} doesn't offer the Responses API at /v1/responses. " +
                    "Switch to Chat Completions in Advanced settings.",
                technicalDetail = "POST /v1/responses -> $code",
                sourceLayer = "relay"
            )
        }
        return RelayErrorClassifier.classifyHttpError(code, body, retryAfter, ProviderRoute.GEMINI_HYDROGEN_RELAY, serviceName)
    }

    /** Text of every assistant `output_text` part in a finished response object (empty when there is none). */
    internal fun outputText(response: JsonObject?): String = outputItems(response)
        .filter { stringOf(it["type"]) == "message" }
        .flatMap { (it["content"] as? JsonArray).orEmpty() }
        .mapNotNull { part ->
            val obj = part as? JsonObject ?: return@mapNotNull null
            when (stringOf(obj["type"])) {
                "output_text" -> stringOf(obj["text"])
                "refusal" -> stringOf(obj["refusal"])
                else -> null
            }
        }
        .joinToString("")

    /** Reasoning-summary text in a finished response object, when the model returned any. */
    internal fun reasoningText(response: JsonObject?): String = outputItems(response)
        .filter { stringOf(it["type"]) == "reasoning" }
        .flatMap { (it["summary"] as? JsonArray).orEmpty() }
        .mapNotNull { part -> (part as? JsonObject)?.let { stringOf(it["text"]) } }
        .joinToString("")

    private fun outputItems(response: JsonObject?): List<JsonObject> =
        (response?.get("output") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    /** Token counts from a finished response. Null when the server sent no usage block. */
    fun usageOf(response: JsonObject?): TokenUsage? {
        val usage = response?.get("usage") as? JsonObject ?: return null
        return TokenUsage(
            inputTokens = intOf(usage["input_tokens"]),
            outputTokens = intOf(usage["output_tokens"]),
            reasoningTokens = intOf((usage["output_tokens_details"] as? JsonObject)?.get("reasoning_tokens"))
        )
    }

    /** What a stream or whole reply has produced so far. Mutated only by [apply]. */
    internal class Accumulator {
        val text = StringBuilder()
        val thinking = StringBuilder()
        var finished: ResponsesEvent.Finished? = null
        var error: ProviderError? = null

        fun accept(event: ResponsesEvent) {
            when (event) {
                is ResponsesEvent.Text -> text.append(event.delta)
                is ResponsesEvent.Thinking -> thinking.append(event.delta)
                is ResponsesEvent.Finished -> finished = event
                is ResponsesEvent.Failed -> error = event.error
                ResponsesEvent.Ignored -> Unit
            }
        }
    }

    /**
     * Reads server-sent events until a terminal event. Text and reasoning deltas go to [onStreamDelta] as they arrive;
     * the frame handling is the standard one: "data:" lines join into one payload, a blank line ends the event, and
     * "event:" names the type when the JSON has none. Checks cancellation before each line.
     */
    suspend fun readEventStream(
        input: InputStream,
        serviceName: String?,
        acc: Accumulator,
        onStreamDelta: suspend (StreamDelta) -> Unit
    ) {
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            val data = mutableListOf<String>()
            var eventName: String? = null
            // Returns false once a terminal event has been applied, so the caller stops reading.
            suspend fun dispatch(): Boolean {
                if (data.isEmpty()) {
                    eventName = null
                    return true
                }
                val payload = data.joinToString("\n")
                data.clear()
                val event = parseEvent(payload, eventName, serviceName)
                eventName = null
                return when (event) {
                    is ResponsesEvent.Text -> {
                        acc.accept(event)
                        onStreamDelta(StreamDelta(textDelta = event.delta))
                        true
                    }
                    is ResponsesEvent.Thinking -> {
                        acc.accept(event)
                        onStreamDelta(StreamDelta(thinkingDelta = event.delta))
                        true
                    }
                    is ResponsesEvent.Finished, is ResponsesEvent.Failed -> {
                        acc.accept(event)
                        false
                    }
                    ResponsesEvent.Ignored -> true
                }
            }
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = reader.readLine() ?: break
                when {
                    line.isEmpty() -> if (!dispatch()) return
                    line.startsWith("data:") -> data += line.removePrefix("data:").trimStart()
                    line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                    else -> Unit // comments (":"), id: and retry: carry nothing Orbit uses
                }
            }
            if (data.isNotEmpty()) dispatch()
        }
    }

    /** Turns what was read into the chat result: text, reasoning, usage, or the honest failure. */
    fun finish(
        acc: Accumulator,
        model: String,
        usedVision: Boolean,
        streamed: Boolean,
        serviceName: String?
    ): ChatDispatchResult {
        acc.error?.let { return ChatDispatchResult.Failure(it) }
        val finished = acc.finished
        val response = finished?.response
        val text = acc.text.toString().ifEmpty { outputText(response) }
        val thinking = acc.thinking.toString().ifEmpty { reasoningText(response) }.ifBlank { null }
        if (finished == null) {
            // The stream stopped before a terminal event. Keep what arrived, marked as interrupted, as chat does.
            if (text.isBlank()) return emptyReply(serviceName, "stream ended without a completion event")
            return ChatDispatchResult.Success(
                assistantText = text,
                modelId = model,
                turnMetadata = GeminiTurnMetadata(thinkingSummary = thinking, finishReason = "stream_interrupted", isTruncated = true),
                usedVision = usedVision,
                wasStreamed = streamed
            )
        }
        val reason = (response?.get("incomplete_details") as? JsonObject)?.let { stringOf(it["reason"]) }
        if (text.isBlank()) {
            return if (reason == "content_filter") {
                ChatDispatchResult.Failure(
                    ProviderError(
                        category = ProviderErrorCategory.CONTENT_FILTERED,
                        userMessage = SseStreamReader.filteredMessage(serviceName),
                        technicalDetail = "status=incomplete; reason=content_filter",
                        sourceLayer = "provider_policy"
                    )
                )
            } else {
                emptyReply(serviceName, "status=${stringOf(response?.get("status")) ?: "completed"}; no output text")
            }
        }
        val truncated = finished.incomplete
        return ChatDispatchResult.Success(
            assistantText = text,
            modelId = model,
            turnMetadata = GeminiTurnMetadata(
                thinkingSummary = thinking,
                finishReason = when {
                    !truncated -> "stop"
                    reason == null || reason == "max_output_tokens" -> "length"
                    else -> reason ?: "incomplete"
                },
                isTruncated = truncated
            ),
            usedVision = usedVision,
            wasStreamed = streamed,
            usage = usageOf(response)
        )
    }

    /** A whole (non-streamed) reply body. A JSON error envelope or a failed status is an error, never empty text. */
    fun finishJsonBody(body: String, model: String, usedVision: Boolean, serviceName: String?): ChatDispatchResult {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: return emptyReply(serviceName, SecretRedactor.redact(body.take(400)).ifBlank { "(empty reply)" })
        val error = root["error"]
        if (error != null && error != JsonNull) {
            return ChatDispatchResult.Failure(errorFrom(error, serviceName, FAILED_MESSAGE))
        }
        val status = stringOf(root["status"])
        if (status == "failed") {
            return ChatDispatchResult.Failure(errorFrom(root["error"], serviceName, FAILED_MESSAGE))
        }
        val acc = Accumulator()
        acc.accept(ResponsesEvent.Finished(root, incomplete = status == "incomplete"))
        return finish(acc, model, usedVision, streamed = false, serviceName = serviceName)
    }

    /** Reads a successful answer: SSE when streaming was asked for and the server sent it, otherwise one JSON body. */
    suspend fun readSuccess(
        contentType: String?,
        useStream: Boolean,
        openBody: () -> InputStream?,
        model: String,
        usedVision: Boolean,
        serviceName: String?,
        onStreamDelta: suspend (StreamDelta) -> Unit
    ): ChatDispatchResult {
        if (useStream && contentType?.lowercase().orEmpty().contains("text/event-stream")) {
            val acc = Accumulator()
            readEventStream(openBody() ?: ByteArrayInputStream(ByteArray(0)), serviceName, acc, onStreamDelta)
            return finish(acc, model, usedVision, streamed = true, serviceName = serviceName)
        }
        val body = openBody()?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        return finishJsonBody(body, model, usedVision, serviceName)
    }

    private fun emptyReply(serviceName: String?, technical: String): ChatDispatchResult.Failure =
        ChatDispatchResult.Failure(
            ProviderError(
                category = ProviderErrorCategory.UNKNOWN_PROTOCOL,
                userMessage = SseStreamReader.emptyReplyMessage(serviceName),
                technicalDetail = technical,
                sourceLayer = "relay"
            )
        )

    private fun stringOf(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun intOf(element: JsonElement?): Int? = (element as? JsonPrimitive)?.intOrNull

    private const val FAILED_MESSAGE = "The AI service couldn't finish this reply. Tap Retry, or try another model."
}
