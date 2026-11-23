package com.hydrogen.ai.model

import com.hydrogen.ai.network.SecretRedactor
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * One failed request, as kept in Settings → Error log. It holds what went wrong and where, never the chat text,
 * attachments or keys: [detail] and [message] pass through [SecretRedactor] before they are stored.
 */
@Serializable
data class ErrorLogEntry(
    val id: String,
    val atEpochMs: Long,
    val category: ProviderErrorCategory,
    val httpStatus: Int? = null,
    /** "Anthropic", "OpenAI", "Google"... or null for your own server. */
    val service: String? = null,
    /** The server address for your own server; blank for a named service. */
    val serverHost: String = "",
    val modelId: String = "",
    val message: String,
    val detail: String = "",
    val sourceLayer: String = "client",
    val retryAfterSeconds: Int? = null
) {
    val title: String get() = category.title
}

object ErrorLog {
    const val MAX_ENTRIES = 200

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val serializer = ListSerializer(ErrorLogEntry.serializer())

    /** Stopping a reply isn't a problem, so it never goes in the log. */
    fun shouldRecord(error: ProviderError): Boolean = error.category != ProviderErrorCategory.CANCELLED

    fun entryFor(
        id: String,
        error: ProviderError,
        atEpochMs: Long,
        service: String?,
        baseUrl: String,
        modelId: String,
        knownSecrets: Collection<String?> = emptyList()
    ): ErrorLogEntry = ErrorLogEntry(
        id = id,
        atEpochMs = atEpochMs,
        category = error.category,
        httpStatus = error.httpStatus,
        service = service,
        serverHost = if (service == null) hostOf(baseUrl) else "",
        modelId = modelId.trim().take(120),
        message = SecretRedactor.redact(error.userMessage, knownSecrets).take(600),
        detail = SecretRedactor.redact(error.technicalDetail, knownSecrets).take(1_200),
        sourceLayer = error.sourceLayer,
        retryAfterSeconds = error.retryAfterSeconds
    )

    /** Newest first, at most [MAX_ENTRIES]. */
    fun add(entries: List<ErrorLogEntry>, entry: ErrorLogEntry): List<ErrorLogEntry> =
        (listOf(entry) + entries.filterNot { it.id == entry.id }).take(MAX_ENTRIES)

    fun encode(entries: List<ErrorLogEntry>): String = json.encodeToString(serializer, entries.take(MAX_ENTRIES))

    fun decode(text: String): List<ErrorLogEntry> =
        runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyList())
            .sortedByDescending { it.atEpochMs }
            .take(MAX_ENTRIES)

    private fun hostOf(baseUrl: String): String {
        val uri = runCatching { java.net.URI(baseUrl.trim()) }.getOrNull() ?: return ""
        val host = uri.host ?: return ""
        return if (uri.port > 0) "$host:${uri.port}" else host
    }
}
