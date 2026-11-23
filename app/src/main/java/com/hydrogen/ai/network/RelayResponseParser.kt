package com.hydrogen.ai.network

import com.hydrogen.ai.model.GeminiTurnMetadata
import com.hydrogen.ai.model.ToolCallRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ParsedAssistantChunk(
    val text: String = "",
    val thinkingText: String = "",
    val thoughtSignature: String? = null,
    val toolCalls: List<ToolCallRecord> = emptyList(),
    val finishReason: String? = null,
    val isContentFiltered: Boolean = false,
    val isTruncated: Boolean = false
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
            parseOpenAiObject(root, isStreamDelta = true) ?: parseGeminiObject(root)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseOpenAiObject(root: JsonObject, isStreamDelta: Boolean): ParsedAssistantChunk? {
        val choices = root["choices"] as? JsonArray ?: return null
        val first = choices.firstOrNull() as? JsonObject ?: return ParsedAssistantChunk()
        val finishReason = first["finish_reason"]?.jsonPrimitive?.contentOrNull
        val container = if (isStreamDelta) {
            first["delta"] as? JsonObject ?: first["message"] as? JsonObject
        } else {
            first["message"] as? JsonObject ?: first["delta"] as? JsonObject
        }
        val text = container?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
        // DeepSeek and LM Studio send "reasoning_content"; Ollama and newer LM Studio builds send "reasoning".
        val reasoning = (
            container?.get("reasoning_content")?.jsonPrimitive?.contentOrNull
                ?: (container?.get("reasoning") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            ).orEmpty()
        val toolCalls = (container?.get("tool_calls") as? JsonArray)?.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val fn = obj["function"] as? JsonObject ?: return@mapNotNull null
            val name = fn["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            ToolCallRecord(
                id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "call_$name",
                name = name,
                argumentsJson = fn["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
            )
        }.orEmpty()
        return ParsedAssistantChunk(
            text = text,
            thinkingText = reasoning,
            toolCalls = toolCalls,
            finishReason = finishReason,
            isContentFiltered = finishReason.equals("content_filter", ignoreCase = true),
            isTruncated = finishReason.equals("length", ignoreCase = true)
        )
    }

    private fun parseGeminiObject(root: JsonObject): ParsedAssistantChunk? {
        val blockReason = (root["promptFeedback"] as? JsonObject)?.get("blockReason")?.jsonPrimitive?.contentOrNull
        if (!blockReason.isNullOrBlank()) {
            return ParsedAssistantChunk(finishReason = blockReason, isContentFiltered = true)
        }
        val candidates = root["candidates"] as? JsonArray ?: return null
        val first = candidates.firstOrNull() as? JsonObject ?: return ParsedAssistantChunk()
        val finishReason = first["finishReason"]?.jsonPrimitive?.contentOrNull
        val parts = (first["content"] as? JsonObject)?.get("parts") as? JsonArray
        val textParts = mutableListOf<String>()
        val thoughtParts = mutableListOf<String>()
        var latestSignature: String? = null
        val toolCalls = mutableListOf<ToolCallRecord>()
        parts?.forEach { element ->
            val part = element as? JsonObject ?: return@forEach
            val sig = part["thoughtSignature"]?.jsonPrimitive?.contentOrNull
                ?: part["thought_signature"]?.jsonPrimitive?.contentOrNull
            if (!sig.isNullOrBlank()) latestSignature = sig
            val isThought = part["thought"]?.jsonPrimitive?.booleanOrNull == true
            val text = part["text"]?.jsonPrimitive?.contentOrNull
            if (!text.isNullOrEmpty()) if (isThought) thoughtParts += text else textParts += text
            val fnCall = part["functionCall"] as? JsonObject
            val name = fnCall?.get("name")?.jsonPrimitive?.contentOrNull
            if (!name.isNullOrBlank()) {
                toolCalls += ToolCallRecord(
                    id = "gemini_call_${name}_${toolCalls.size}",
                    name = name,
                    argumentsJson = fnCall["args"]?.toString() ?: "{}",
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

