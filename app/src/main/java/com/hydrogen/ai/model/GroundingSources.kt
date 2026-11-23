package com.hydrogen.ai.model

import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class WebSource(
    val title: String,
    val url: String,
    val domain: String
)

/**
 * Pure Kotlin parser for Gemini `groundingMetadata` search results.
 * Never throws on malformed or unexpected JSON shapes.
 */
object GroundingSources {

    const val MAX_SOURCES = 8
    const val MAX_TITLE = 80
    private const val MAX_SEARCH_QUERIES = 5
    private const val ELLIPSIS = "…"

    fun parse(candidate: JsonObject): List<WebSource> {
        val metadata = candidate["groundingMetadata"] as? JsonObject ?: return emptyList()
        val chunks = metadata["groundingChunks"] as? JsonArray ?: return emptyList()

        val seenUrls = HashSet<String>()
        val sources = ArrayList<WebSource>(minOf(chunks.size, MAX_SOURCES))

        for (chunk in chunks) {
            if (sources.size >= MAX_SOURCES) break

            val web = (chunk as? JsonObject)?.get("web") as? JsonObject ?: continue
            val rawUri = (web["uri"] as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: continue

            val domain = extractValidDomain(rawUri) ?: continue
            if (!seenUrls.add(rawUri)) continue

            val rawTitle = (web["title"] as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                .orEmpty()
            val cleanTitle = cleanAndTruncateTitle(rawTitle, fallbackDomain = domain)

            sources.add(
                WebSource(
                    title = cleanTitle,
                    url = rawUri,
                    domain = domain
                )
            )
        }

        return sources
    }

    /**
     * Adds the sources in one raw response or stream event ([payload] JSON with `candidates`) to
     * [previous], keeping order, dropping repeated URLs and capping at [MAX_SOURCES]. Never throws.
     */
    fun collect(payload: String, previous: List<WebSource> = emptyList()): List<WebSource> {
        if (!payload.contains("groundingMetadata")) return previous
        val root = runCatching { Json.parseToJsonElement(payload) }.getOrNull() ?: return previous
        val responses = when (root) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(root)
            else -> emptyList()
        }
        val found = responses.flatMap { response ->
            val first = (response["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
            if (first == null) emptyList() else parse(first)
        }
        if (found.isEmpty()) return previous
        return (previous + found).distinctBy { it.url }.take(MAX_SOURCES)
    }

    fun searchQueries(candidate: JsonObject): List<String> {
        val metadata = candidate["groundingMetadata"] as? JsonObject ?: return emptyList()
        val queries = metadata["webSearchQueries"] as? JsonArray ?: return emptyList()

        val result = ArrayList<String>(minOf(queries.size, MAX_SEARCH_QUERIES))
        for (item in queries) {
            if (result.size >= MAX_SEARCH_QUERIES) break
            val text = (item as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?.let(::stripControlChars)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: continue
            result.add(text)
        }
        return result
    }

    fun toMarkdownFooter(sources: List<WebSource>): String {
        if (sources.isEmpty()) return ""
        return buildString {
            append("\n\n**Sources**\n")
            sources.forEachIndexed { index, source ->
                if (index > 0) append('\n')
                val displayTitle = source.title.ifBlank { source.domain }
                append(index + 1)
                append(". [")
                append(escapeMarkdownTitle(displayTitle))
                append("](")
                append(encodeMarkdownUrl(source.url))
                append(')')
            }
        }
    }

    private fun extractValidDomain(rawUri: String): String? {
        val uri = try {
            URI(rawUri)
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.isOpaque) return null

        val host = uri.host?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return host.removePrefix("www.").ifBlank { host }
    }

    private fun cleanAndTruncateTitle(rawTitle: String, fallbackDomain: String): String {
        val cleaned = stripControlChars(rawTitle).trim()
        val base = cleaned.ifBlank { fallbackDomain }
        if (base.length <= MAX_TITLE) return base

        var end = MAX_TITLE - ELLIPSIS.length
        if (end > 0 && base[end - 1].isHighSurrogate()) {
            end--
        }
        val prefix = base.substring(0, end).trimEnd()
        return if (prefix.isEmpty()) fallbackDomain.take(MAX_TITLE) else prefix + ELLIPSIS
    }

    private fun stripControlChars(input: String): String {
        if (input.isEmpty()) return ""
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (!ch.isISOControl()) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun escapeMarkdownTitle(title: String): String {
        val sb = StringBuilder(title.length)
        for (ch in title) {
            when (ch) {
                '[', ']', '(', ')' -> {
                    sb.append('\\')
                    sb.append(ch)
                }
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun encodeMarkdownUrl(url: String): String {
        val sb = StringBuilder(url.length)
        for (ch in url) {
            when (ch) {
                ' ' -> sb.append("%20")
                '(' -> sb.append("%28")
                ')' -> sb.append("%29")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
