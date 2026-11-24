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
     * then other Gemini models newest first with a stable alias ahead of its previews and dated builds, then
     * everything else. Malformed input yields an empty list.
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
                // A stable alias is the one to pick by default; named previews and dated builds follow it.
                .thenBy { if (isPreviewBuild(it)) 1 else 0 }
                .thenByDescending { it }
        )
    }

    /** Preview aliases ("-preview-05-06", "-exp") and dated builds ("-001", "-20241022") are not the stable one. */
    private fun isPreviewBuild(id: String): Boolean = PREVIEW_BUILD.containsMatchIn(id)

    private val PREVIEW_BUILD = Regex("""-preview(?:-|$)|-exp(?:-|$)|-(?:\d{3,4}|\d{8})$""")
}
