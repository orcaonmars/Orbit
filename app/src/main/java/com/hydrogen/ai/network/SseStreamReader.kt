package com.hydrogen.ai.network

import com.hydrogen.ai.model.GeminiTurnMetadata
import com.hydrogen.ai.model.ProviderError
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.ThinkingSplitter
import com.hydrogen.ai.model.ToolCallRecord
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal data class AccumulatedStreamResult(
    val text: String,
    val metadata: GeminiTurnMetadata?,
    val isContentFiltered: Boolean,
    val streamError: ProviderError? = null
)

internal object SseStreamReader {

    const val FILTERED_MESSAGE =
        "The AI service's safety filter blocked this reply. Try rewording your message."
    const val EMPTY_REPLY_MESSAGE =
        "The AI sent back an empty reply. Tap Retry, or try another model."

    fun filteredMessage(serviceName: String?): String =
        if (serviceName == null) FILTERED_MESSAGE
        else "$serviceName's safety filter blocked this reply. Try rewording your message."

    fun emptyReplyMessage(serviceName: String?): String =
        if (serviceName == null) EMPTY_REPLY_MESSAGE
        else "$serviceName sent back an empty reply. Tap Retry, or try another model."

    suspend fun handleSuccessResponse(
        conn: HttpURLConnection,
        useStream: Boolean,
        model: String,
        usedVision: Boolean,
        sourceLayer: String,
        onStreamDelta: suspend (StreamDelta) -> Unit,
        route: ProviderRoute = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        onRawEvent: ((String) -> Unit)? = null,
        serviceName: String? = null
    ): ChatDispatchResult = handleSuccessBody(
        code = conn.responseCode,
        contentType = conn.contentType,
        openBody = { conn.inputStream },
        useStream = useStream,
        model = model,
        usedVision = usedVision,
        sourceLayer = sourceLayer,
        onStreamDelta = onStreamDelta,
        route = route,
        onRawEvent = onRawEvent,
        serviceName = serviceName
    )

    /** Same as [handleSuccessResponse] for any transport (e.g. the Wi-Fi socket transport); [openBody] is read once. */
    suspend fun handleSuccessBody(
        code: Int,
        contentType: String?,
        openBody: () -> InputStream?,
        useStream: Boolean,
        model: String,
        usedVision: Boolean,
        sourceLayer: String,
        onStreamDelta: suspend (StreamDelta) -> Unit,
        route: ProviderRoute = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        onRawEvent: ((String) -> Unit)? = null,
        serviceName: String? = null
    ): ChatDispatchResult {
        val type = contentType?.lowercase().orEmpty()
        if (useStream && type.contains("text/event-stream")) {
            val streamed = readSseStream(openBody() ?: ByteArrayInputStream(ByteArray(0)), onStreamDelta, route, onRawEvent, serviceName)
            if (streamed.streamError != null) {
                return ChatDispatchResult.Failure(streamed.streamError)
            }
            if (streamed.isContentFiltered && streamed.text.isBlank()) {
                return ChatDispatchResult.Failure(
                    ProviderError(
                        category = ProviderErrorCategory.CONTENT_FILTERED,
                        httpStatus = code,
                        userMessage = filteredMessage(serviceName),
                        technicalDetail = "finishReason=${streamed.metadata?.finishReason ?: "filtered"}",
                        sourceLayer = sourceLayer
                    )
                )
            }
            if (streamed.text.isBlank() && streamed.metadata?.toolCalls.isNullOrEmpty()) {
                return ChatDispatchResult.Failure(
                    ProviderError(
                        category = ProviderErrorCategory.UNKNOWN_PROTOCOL,
                        httpStatus = code,
                        userMessage = emptyReplyMessage(serviceName),
                        technicalDetail = "stream ended without assistant text",
                        sourceLayer = sourceLayer
                    )
                )
            }
            return ChatDispatchResult.Success(
                assistantText = streamed.text,
                modelId = model,
                turnMetadata = streamed.metadata,
                usedVision = usedVision,
                wasStreamed = true
            )
        }
        val body = openBody()?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        onRawEvent?.invoke(body)
        // Some gateways return a JSON error envelope with HTTP 200.
        RelayErrorClassifier.classifySseErrorPayload(body, route, serviceName)?.let {
            return ChatDispatchResult.Failure(it)
        }
        val parsed = RelayResponseParser.parseCompleteResponse(body)?.let { whole ->
            // Inline <think> blocks from local reasoning models go to the thought line, not the answer.
            val split = ThinkingSplitter.split(whole.text)
            if (split.thinking.isEmpty()) {
                whole
            } else {
                whole.copy(
                    text = split.answer,
                    thinkingText = listOf(whole.thinkingText, split.thinking).filter { it.isNotBlank() }.joinToString("\n")
                )
            }
        }
        if (parsed?.isContentFiltered == true && parsed.text.isBlank()) {
            return ChatDispatchResult.Failure(
                ProviderError(
                    category = ProviderErrorCategory.CONTENT_FILTERED,
                    httpStatus = code,
                    userMessage = filteredMessage(serviceName),
                    technicalDetail = SecretRedactor.redact("finishReason=${parsed.finishReason ?: "filtered"}; " + body.take(400)),
                    sourceLayer = sourceLayer
                )
            )
        }
        if (parsed == null || (parsed.text.isBlank() && parsed.toolCalls.isEmpty())) {
            return ChatDispatchResult.Failure(
                ProviderError(
                    category = ProviderErrorCategory.UNKNOWN_PROTOCOL,
                    httpStatus = code,
                    userMessage = emptyReplyMessage(serviceName),
                    technicalDetail = SecretRedactor.redact(body.take(400)).ifBlank { "(empty reply)" },
                    sourceLayer = sourceLayer
                )
            )
        }
        return ChatDispatchResult.Success(
            assistantText = parsed.text,
            modelId = model,
            turnMetadata = RelayResponseParser.toMetadata(
                parsed.thoughtSignature, parsed.thinkingText, parsed.toolCalls, parsed.finishReason, parsed.isTruncated
            ),
            usedVision = usedVision,
            wasStreamed = false
        )
    }

    suspend fun readSseStream(
        inputStream: InputStream,
        onDelta: suspend (StreamDelta) -> Unit,
        route: ProviderRoute = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        /** Sees every event's raw JSON (e.g. to collect web-search sources); must not throw. */
        onRawEvent: ((String) -> Unit)? = null,
        /** Names the service in a mid-stream error ("Anthropic is busy..."); null for your own server. */
        serviceName: String? = null
    ): AccumulatedStreamResult {
        val textBuilder = StringBuilder()
        val thinkingBuilder = StringBuilder()
        var latestSignature: String? = null
        var finishReason: String? = null
        var isTruncated = false
        var receivedDone = false
        var isFiltered = false
        var streamError: ProviderError? = null
        val toolCalls = mutableListOf<ToolCallRecord>()
        // Index is stable across OpenAI delta events even when subsequent events omit the id and name.
        data class PendingTool(var id: String? = null, val name: StringBuilder = StringBuilder(),
            val arguments: StringBuilder = StringBuilder())
        val pendingTools = sortedMapOf<Int, PendingTool>()
        // Inline <think> blocks from local reasoning models stream to the thought line, not the answer.
        val splitter = ThinkingSplitter()

        suspend fun accept(chunk: ParsedAssistantChunk) {
            val part = splitter.feed(chunk.text)
            val thinkingDelta = chunk.thinkingText + part.thinking
            if (part.answer.isNotEmpty()) textBuilder.append(part.answer)
            if (thinkingDelta.isNotEmpty()) thinkingBuilder.append(thinkingDelta)
            if (!chunk.thoughtSignature.isNullOrBlank()) latestSignature = chunk.thoughtSignature
            if (chunk.toolCalls.isNotEmpty()) toolCalls += chunk.toolCalls
            for (fragment in chunk.toolCallFragments) {
                val pending = pendingTools.getOrPut(fragment.index) { PendingTool() }
                if (!fragment.id.isNullOrBlank()) pending.id = fragment.id
                fragment.name?.let { pending.name.append(it) }
                fragment.arguments?.let { pending.arguments.append(it) }
            }
            if (!chunk.finishReason.isNullOrBlank()) finishReason = chunk.finishReason
            if (chunk.isTruncated) isTruncated = true
            if (chunk.isContentFiltered) isFiltered = true
            onDelta(
                StreamDelta(
                    textDelta = part.answer,
                    thinkingDelta = thinkingDelta,
                    thoughtSignature = chunk.thoughtSignature,
                    toolCalls = chunk.toolCalls,
                    finishReason = chunk.finishReason
                )
            )
        }

        inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val dataLines = mutableListOf<String>()
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = reader.readLine() ?: break
                if (line.isBlank()) {
                    if (dataLines.isNotEmpty()) {
                        val payload = dataLines.joinToString("\n")
                        dataLines.clear()
                        onRawEvent?.invoke(payload)
                        if (payload.trim() == "[DONE]") { receivedDone = true; break }
                        val chunk = RelayResponseParser.parseSseEventData(payload)
                        if (chunk != null) {
                            accept(chunk)
                        } else {
                            val err = RelayErrorClassifier.classifySseErrorPayload(payload, route, serviceName)
                            if (err != null) {
                                streamError = err
                                break
                            }
                        }
                    }
                } else if (line.startsWith("data:")) {
                    dataLines += line.removePrefix("data:").trimStart()
                }
            }
            if (streamError == null && dataLines.isNotEmpty()) {
                val payload = dataLines.joinToString("\n")
                onRawEvent?.invoke(payload)
                if (payload.trim() == "[DONE]") receivedDone = true
                val chunk = RelayResponseParser.parseSseEventData(payload)
                if (chunk != null) {
                    accept(chunk)
                } else {
                    streamError = RelayErrorClassifier.classifySseErrorPayload(payload, route, serviceName)
                }
            }
        }
        if (streamError == null) {
            // Emit assembled records once, not misleading half-JSON tool arguments during streaming.
            val terminal = receivedDone || finishReason.equals("tool_calls", ignoreCase = true) ||
                finishReason.equals("stop", ignoreCase = true)
            val assembled = if (!terminal || isTruncated || isFiltered) emptyList() else
                pendingTools.mapNotNull { (index, pending) ->
                    val name = pending.name.toString()
                    val arguments = pending.arguments.toString()
                    // Never surface a half-assembled request or fabricate missing arguments.
                    val valid = runCatching { Json.parseToJsonElement(arguments) is JsonObject }.getOrDefault(false)
                    if (name.isBlank() || !valid) null
                    else ToolCallRecord(pending.id ?: "call_stream_$index", name, arguments)
                }
            if (assembled.size < pendingTools.size && !isFiltered && !isTruncated) {
                isTruncated = true
                finishReason = "tool_call_incomplete"
            }
            if (assembled.isNotEmpty()) {
                toolCalls += assembled
                onDelta(StreamDelta(toolCalls = assembled))
            }
            // Release anything the splitter held back (a reply that is only "<", or an unclosed block).
            val tail = splitter.finish()
            if (!tail.isEmpty()) {
                textBuilder.append(tail.answer)
                thinkingBuilder.append(tail.thinking)
                onDelta(StreamDelta(textDelta = tail.answer, thinkingDelta = tail.thinking))
            }
        }
        var finalText = textBuilder.toString()
        var finalThinking = thinkingBuilder.toString()
        if (!splitter.sawThinkingBlock) {
            // The template opened the block in the prompt, so only "</think>" appeared: fix the saved reply.
            ThinkingSplitter.splitOrphanClose(finalText)?.let { orphan ->
                finalText = orphan.answer
                finalThinking = listOf(finalThinking, orphan.thinking).filter { it.isNotBlank() }.joinToString("\n")
            }
        }

        val interrupted = streamError == null && !receivedDone && finishReason.isNullOrBlank() &&
            finalText.isNotBlank() && route == ProviderRoute.GEMINI_HYDROGEN_RELAY
        return AccumulatedStreamResult(
            text = finalText,
            metadata = RelayResponseParser.toMetadata(
                thoughtSignature = latestSignature,
                thinkingSummary = finalThinking,
                toolCalls = toolCalls,
                finishReason = if (interrupted) "stream_interrupted" else finishReason,
                isTruncated = isTruncated || interrupted
            ),
            isContentFiltered = isFiltered,
            streamError = streamError
        )
    }
}
