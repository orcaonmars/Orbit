package com.hydrogen.ai.network

import com.hydrogen.ai.model.GeminiTurnMetadata
import com.hydrogen.ai.model.ToolCallRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.intOrNull


/** An OpenAI-compatible function call may be split across arbitrary streaming events. */
data class ToolCallFragment(
    val index: Int,
    val id: String?,
    val name: String?,
    val arguments: String?
)

data class ParsedAssistantChunk(
    val text: String = "",
    val thinkingText: String = "",
    val thoughtSignature: String? = null,
    val toolCalls: List<ToolCallRecord> = emptyList(),
    val finishReason: String? = null,
    val isContentFiltered: Boolean = false,
    val isTruncated: Boolean = false,
    val toolCallFragments: List<ToolCallFragment> = emptyList()
)

object RelayResponseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun extractAssistantText(rawJson: String): String? =
        parseCompleteResponse(rawJson)?.text?.ifBlank { null }

    fun parseCompleteResponse(rawJson: String): ParsedAssistantChunk? = try {
        val root = json.parseToJsonElement(rawJson).jsonObject
        parseOpenAiObject(root, isStreamDelta = false) ?: parseGeminiObject(root)
    } catch (_: Exception) {
        null
    }

    fun parseSseEventData(dataPayload: String): ParsedAssistantChunk? {
        val trimmed = dataPayload.trim()
        if (trimmed.isEmpty() || trimmed == "[DONE]") return null
        return try {
            val root = json.parseToJsonElement(trimmed).jsonObject
            // An error envelope can also contain choices: do not swallow it as an empty delta.
            if (root["error"] != null && root["error"] != JsonNull) null
            else parseOpenAiObject(root, isStreamDelta = true) ?: parseGeminiObject(root)
        } catch (_: Exception) {
            null
        }
    }

    // Optional metadata from compatible servers is not guaranteed to have the documented shape.
    // Never let an unexpected object, number or boolean swallow an otherwise valid answer.
    private fun JsonElement?.stringValue(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun contentText(value: JsonElement?): String = when (value) {
        is JsonPrimitive -> value.stringValue().orEmpty()
        is JsonArray -> value.joinToString("") { element ->
            val part = element as? JsonObject
            if (part == null) element.stringValue().orEmpty()
            else when (part["type"].stringValue()) {
                "text", "output_text", null -> part["text"].stringValue().orEmpty()
                else -> ""
            }
        }
        else -> ""
    }

    private fun parseOpenAiObject(root: JsonObject, isStreamDelta: Boolean): ParsedAssistantChunk? {
        val choices = root["choices"] as? JsonArray ?: return null
        val first = choices.firstOrNull() as? JsonObject ?: return ParsedAssistantChunk()
        val finishReason = first["finish_reason"].stringValue()
        val container = if (isStreamDelta) {
            first["delta"] as? JsonObject ?: first["message"] as? JsonObject
        } else {
            first["message"] as? JsonObject ?: first["delta"] as? JsonObject
        }
        val text = contentText(container?.get("content")).ifEmpty { container?.get("refusal").stringValue().orEmpty() }
        // DeepSeek and LM Studio send "reasoning_content"; Ollama and newer LM Studio builds send "reasoning".
        val reasoning = sequenceOf(container?.get("reasoning_content"), container?.get("reasoning"))
            .map(::contentText).firstOrNull { it.isNotEmpty() }
            ?: (container?.get("reasoning_details") as? JsonArray)?.joinToString("") { element ->
                val detail = element as? JsonObject
                when (detail?.get("type").stringValue()) {
                    "reasoning.text" -> detail?.get("text").stringValue().orEmpty()
                    "reasoning.summary" -> detail?.get("summary").stringValue().orEmpty()
                    else -> "" // Encrypted reasoning and signatures are not readable text.
                }
            }.orEmpty()
        val rawCalls = container?.get("tool_calls") as? JsonArray
        val fragmented = isStreamDelta && first["delta"] is JsonObject
        val fragments = if (fragmented) rawCalls?.mapIndexedNotNull { position, el ->
            val obj = el as? JsonObject ?: return@mapIndexedNotNull null
            val fn = obj["function"] as? JsonObject
            val index = (obj["index"] as? JsonPrimitive)?.intOrNull ?: position
            if (index < 0) return@mapIndexedNotNull null
            ToolCallFragment(index, obj["id"].stringValue(), fn?.get("name").stringValue(),
                fn?.get("arguments").stringValue() ?: (fn?.get("arguments") as? JsonObject)?.toString())
        }.orEmpty() else emptyList()
        val toolCalls = if (fragmented) emptyList() else rawCalls?.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val fn = obj["function"] as? JsonObject ?: return@mapNotNull null
            val name = fn["name"].stringValue() ?: return@mapNotNull null
            ToolCallRecord(
                id = obj["id"].stringValue() ?: "call_$name",
                name = name,
                argumentsJson = (fn["arguments"].stringValue() ?: (fn["arguments"] as? JsonObject)?.toString() ?: "{}")
            )
        }.orEmpty()
        return ParsedAssistantChunk(
            text = text,
            thinkingText = reasoning,
            toolCalls = toolCalls,
            toolCallFragments = fragments,
            finishReason = finishReason,
            isContentFiltered = finishReason.equals("content_filter", ignoreCase = true),
            isTruncated = finishReason.equals("length", ignoreCase = true)
        )
    }

    private fun parseGeminiObject(root: JsonObject): ParsedAssistantChunk? {
        val blockReason = (root["promptFeedback"] as? JsonObject)?.get("blockReason").stringValue()
        if (!blockReason.isNullOrBlank()) {
            return ParsedAssistantChunk(finishReason = blockReason, isContentFiltered = true)
        }
        val candidates = root["candidates"] as? JsonArray ?: return null
        val first = candidates.firstOrNull() as? JsonObject ?: return ParsedAssistantChunk()
        val finishReason = first["finishReason"].stringValue()
        val parts = (first["content"] as? JsonObject)?.get("parts") as? JsonArray
        val textParts = mutableListOf<String>()
        val thoughtParts = mutableListOf<String>()
        var latestSignature: String? = null
        val toolCalls = mutableListOf<ToolCallRecord>()
        parts?.forEach { element ->
            val part = element as? JsonObject ?: return@forEach
            val sig = part["thoughtSignature"].stringValue()
                ?: part["thought_signature"].stringValue()
            if (!sig.isNullOrBlank()) latestSignature = sig
            val isThought = (part["thought"] as? JsonPrimitive)?.booleanOrNull == true
            val text = part["text"].stringValue()
            if (!text.isNullOrEmpty()) if (isThought) thoughtParts += text else textParts += text
            val fnCall = part["functionCall"] as? JsonObject
            val name = fnCall?.get("name").stringValue()
            if (!name.isNullOrBlank()) {
                toolCalls += ToolCallRecord(
                    id = "gemini_call_${name}_${toolCalls.size}",
                    name = name,
                    argumentsJson = fnCall?.get("args")?.toString() ?: "{}",
                    thoughtSignature = sig ?: latestSignature
                )
            }
        }
        return ParsedAssistantChunk(
            text = if (textParts.size <= 1) textParts.firstOrNull().orEmpty() else textParts.joinToString("\n"),
            thinkingText = thoughtParts.joinToString("\n"),
            thoughtSignature = latestSignature,
            toolCalls = toolCalls,
            finishReason = finishReason,
            isContentFiltered = finishReason in setOf("SAFETY", "RECITATION", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII"),
            isTruncated = finishReason == "MAX_TOKENS"
        )
    }

    fun toMetadata(
        thoughtSignature: String?,
        thinkingSummary: String?,
        toolCalls: List<ToolCallRecord>,
        finishReason: String?,
        isTruncated: Boolean
    ): GeminiTurnMetadata? {
        if (thoughtSignature.isNullOrBlank() && thinkingSummary.isNullOrBlank() &&
            toolCalls.isEmpty() && finishReason.isNullOrBlank() && !isTruncated
        ) return null
        return GeminiTurnMetadata(thoughtSignature, thinkingSummary?.ifBlank { null }, toolCalls, finishReason, isTruncated)
    }
}

