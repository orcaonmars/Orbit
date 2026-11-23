package com.hydrogen.ai.model

/**
 * What TalkBack says for one chat message: who said it, when, and what it contains, e.g.
 * "Orbit, 2 minutes ago, 1 code block: Here is the fix…". Markdown symbols are stripped by
 * [SpeakableText] so nobody hears "asterisk asterisk".
 */
object MessageA11y {
    const val MAX_BODY_CHARS = 400
    const val EMPTY_BODY = "(empty message)"

    /**
     * Marks where a code block sits in the spoken body. The header already says "1 code block", so the
     * longer read-aloud note ([SpeakableText.CODE_BLOCK_NOTE]) would make TalkBack say it twice.
     */
    const val CODE_MARK = "(code)"

    fun speaker(role: MessageRole): String = when (role) {
        MessageRole.USER -> "You"
        MessageRole.ASSISTANT -> "Orbit"
        MessageRole.SYSTEM -> "Instructions"
    }

    /**
     * Code blocks (``` or ~~~; an unclosed fence still counts) and tables, counted from the same
     * [MarkdownParser] blocks the screen draws, so TalkBack names exactly what a sighted user sees:
     * one-column tables count, and a table written inside a code block does not.
     */
    fun blockCounts(markdown: String): Pair<Int, Int> {
        if ('`' !in markdown && '~' !in markdown && '|' !in markdown) return 0 to 0
        var code = 0
        var tables = 0
        for (block in MarkdownParser.parse(markdown)) {
            when (block) {
                is MdBlock.CodeBlock -> code++
                is MdBlock.Table -> tables++
                else -> Unit
            }
        }
        return code to tables
    }

    /** Number of fenced code blocks, as drawn on screen. */
    fun codeBlockCount(markdown: String): Int = blockCounts(markdown).first

    /** Number of Markdown tables, as drawn on screen (including one-column tables). */
    fun tableCount(markdown: String): Int = blockCounts(markdown).second

    fun relativeTime(createdAtMs: Long, nowMs: Long): String {
        val seconds = (nowMs - createdAtMs) / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            seconds < 60 -> "just now" // includes a clock that runs slightly behind
            minutes < 60 -> if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
            hours < 24 -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
            days < 2 -> "yesterday"
            else -> "$days days ago"
        }
    }

    /** "Orbit, 2 minutes ago, 1 code block, 2 attachments" — everything except the message text. */
    fun header(
        role: MessageRole,
        content: String,
        createdAtMs: Long,
        nowMs: Long,
        attachmentCount: Int = 0,
        isError: Boolean = false
    ): String {
        val parts = mutableListOf(speaker(role))
        if (isError) parts += "error"
        parts += relativeTime(createdAtMs, nowMs)
        val (codeBlocks, tables) = blockCounts(content)
        codeBlocks.takeIf { it > 0 }?.let { parts += plural(it, "code block") }
        tables.takeIf { it > 0 }?.let { parts += plural(it, "table") }
        attachmentCount.takeIf { it > 0 }?.let { parts += plural(it, "attachment") }
        return parts.joinToString(", ")
    }

    /**
     * Header plus the spoken body. The body is cut at [maxBodyChars] (on a word boundary, with "…")
     * so a long reply doesn't become one endless announcement; pass Int.MAX_VALUE to keep it whole.
     */
    fun label(
        role: MessageRole,
        content: String,
        createdAtMs: Long,
        nowMs: Long,
        attachmentCount: Int = 0,
        isError: Boolean = false,
        maxBodyChars: Int = MAX_BODY_CHARS
    ): String {
        val spoken = SpeakableText.fromMarkdown(content).replace(SpeakableText.CODE_BLOCK_NOTE, CODE_MARK)
        val body = when {
            spoken.isBlank() -> EMPTY_BODY
            spoken.length <= maxBodyChars -> spoken
            else -> {
                val cut = spoken.lastIndexOf(' ', maxBodyChars).takeIf { it > maxBodyChars / 2 } ?: maxBodyChars
                spoken.substring(0, cut).trimEnd() + "…"
            }
        }
        return header(role, content, createdAtMs, nowMs, attachmentCount, isError) + ": " + body
    }

    private fun plural(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
}
