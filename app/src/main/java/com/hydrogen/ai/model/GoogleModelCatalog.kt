package com.hydrogen.ai.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Chat models for the Google Gemini provider: built-in suggestions and parsing of the key's own model list. */
object GoogleModelCatalog {
    /** Shown before the key's list is loaded; the first entry is the default. */
    val suggestions: List<String> = listOf(HydrogenDefaults.PRIMARY_MODEL_ID, "gemini-2.5-pro", "gemini-2.5-flash")

    /** Models that answer generateContent but cannot hold a text chat (speech, image, embedding, live audio). */
    private val NON_CHAT_MARKERS = listOf("embedding", "aqa", "-tts", "image-generation", "native-audio", "-live")

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses a `GET /v1beta/models` body into chat-capable ids ("models/x" -> "x"): Gemini 4 Hydrogen first,
     * then other Gemini models newest first, then everything else. Malformed input yields an empty list.
     */
    fun parse(body: String): List<String> {
        val models = runCatching { json.parseToJsonElement(body).jsonObject["models"]?.jsonArray }.getOrNull()
            ?: return emptyList()
        val ids = models.mapNotNull { element ->
            val model = element as? JsonObject ?: return@mapNotNull null
            val name = runCatching { model["name"]?.jsonPrimitive?.content }.getOrNull() ?: return@mapNotNull null
            val methods = (model["supportedGenerationMethods"] as? JsonArray)
                ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                .orEmpty()
            val id = name.removePrefix("models/").trim()
            id.takeIf {
                it.isNotEmpty() && "generateContent" in methods &&
                    NON_CHAT_MARKERS.none { marker -> it.lowercase().contains(marker) }
            }
        }.distinct()
        return ids.sortedWith(
            compareBy<String> { if (it == HydrogenDefaults.PRIMARY_MODEL_ID) 0 else if (it.startsWith("gemini")) 1 else 2 }
                .thenByDescending { it }
        )
    }
}
