package com.hydrogen.ai.model

/** Searches the currently displayed conversation only, never hidden versions or reasoning text. */
object ChatFind {
    const val MAX_QUERY_LENGTH = 160
    private const val MAX_PREVIEW_LENGTH = 240

    fun matches(messages: List<ChatMessage>, query: String): List<Int> {
        val cleanQuery = query.trim()
        if (cleanQuery.length > MAX_QUERY_LENGTH) return emptyList()
        val terms = cleanQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        return messages.mapIndexedNotNull { index, message ->
            if (message.role == MessageRole.SYSTEM) return@mapIndexedNotNull null
            val text = message.content + "\n" + message.attachments.joinToString("\n") { it.displayName }
            index.takeIf { terms.all { text.contains(it, ignoreCase = true) } }
        }
    }

    /** Show the matching passage even when the message itself is taller than the screen. */
    fun preview(message: ChatMessage, query: String, maxChars: Int = 150): String {
        val terms = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty() || maxChars <= 0 || query.length > MAX_QUERY_LENGTH) return ""
        val limit = maxChars.coerceAtMost(MAX_PREVIEW_LENGTH)
        val text = (message.content + " " + message.attachments.joinToString(" ") { it.displayName })
            .replace(Regex("\\s+"), " ").trim()
        val phrase = terms.joinToString(" ")
        val position = text.indexOf(phrase, ignoreCase = true).takeIf { it >= 0 }
            ?: terms.map { text.indexOf(it, ignoreCase = true) }.filter { it >= 0 }.minOrNull()
            ?: return ""
        if (text.length <= limit) return text
        val start = (position - limit / 3).coerceIn(0, text.length - limit)
        val end = start + limit
        return (if (start > 0) "..." else "") + text.substring(start, end) +
            (if (end < text.length) "..." else "")
    }

    fun step(index: Int, delta: Int, count: Int): Int =
        if (count <= 0) 0 else Math.floorMod(index.toLong() + delta, count.toLong()).toInt()
}
