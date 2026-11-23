package com.hydrogen.ai.model

/**
 * Search helpers for finding text in chats: case-insensitive match ranges for highlighting, a plain-text
 * view of Markdown (fence markers and emphasis symbols removed, code kept) and a one-line snippet around
 * the first match. Pure Kotlin so it can be unit-tested without Android.
 */
object ChatSearch {
    const val SNIPPET_RADIUS = 40
    const val ELLIPSIS = "…"

    private val fenceLine = Regex("^\\s*```")
    private val markdownChars = Regex("[*_`#]")
    private val whitespace = Regex("\\s+")

    /** Case-insensitive, non-overlapping match ranges of query.trim() in text, left to right. Blank query -> empty list. */
    fun matchRanges(text: String, query: String): List<IntRange> {
        val needle = query.trim()
        if (needle.isEmpty() || text.length < needle.length) return emptyList()
        val ranges = mutableListOf<IntRange>()
        var from = 0
        while (from <= text.length - needle.length) {
            val at = text.indexOf(needle, startIndex = from, ignoreCase = true)
            if (at < 0) break
            ranges += at until at + needle.length
            from = at + needle.length
        }
        return ranges
    }

    /**
     * Text prepared for searching and previews: drop lines whose trimmed start is ``` (fence markers only; the
     * code inside is kept as written). Outside code, drop table rule rows and horizontal rules, show table cells
     * as "a · b" and remove list bullets and quote marks at the start of a line. Then remove '*', '_', '`' and
     * '#', collapse whitespace runs to one space and trim.
     */
    fun plain(markdown: String): String {
        val lines = mutableListOf<String>()
        var inCode = false
        for (line in markdown.lineSequence()) {
            if (fenceLine.containsMatchIn(line)) {
                inCode = !inCode
                continue
            }
            if (inCode) {
                lines += line
                continue
            }
            if (ruleLine.matches(line)) continue
            val trimmed = line.trim()
            lines += if (trimmed.startsWith("|")) {
                trimmed.removePrefix("|").removeSuffix("|").split('|').joinToString(" · ") { it.trim() }
            } else {
                line.replaceFirst(lineMarker, "")
            }
        }
        return lines.joinToString("\n")
            .replace(markdownChars, "")
            .replace(whitespace, " ")
            .trim()
    }

    /** A line of only dashes, colons, bars and spaces: a table rule row or a horizontal rule. */
    private val ruleLine = Regex("^[\\s|:\\-]*-[\\s|:\\-]*$")
    /** A list bullet or quote mark at the start of a line. */
    private val lineMarker = Regex("^\\s*(?:[-+*]\\s+|>+\\s?)")

    /**
     * A one-line preview around the first match in [plain]: up to [SNIPPET_RADIUS] characters either side,
     * widened to the nearest space so no word is cut. "…" marks text cut at either end. Null when the query
     * is blank or does not match. When the query itself contains Markdown symbols (e.g. "snake_case") and so only
     * matches the original text, the snippet comes from that text with its whitespace collapsed.
     */
    fun snippet(markdown: String, query: String): String? =
        snippetIn(plain(markdown), query) ?: snippetIn(markdown.replace(whitespace, " ").trim(), query)

    private fun snippetIn(text: String, query: String): String? {
        val first = matchRanges(text, query).firstOrNull() ?: return null
        var start = maxOf(0, first.first - SNIPPET_RADIUS)
        while (start > 0 && text[start - 1] != ' ') start--
        var end = minOf(text.length, first.last + 1 + SNIPPET_RADIUS)
        while (end < text.length && text[end] != ' ') end++
        val body = text.substring(start, end).trim()
        val prefix = if (start > 0) ELLIPSIS else ""
        val suffix = if (end < text.length) ELLIPSIS else ""
        return prefix + body + suffix
    }

    /** Ranges of query in the snippet string, for highlighting (same rules as [matchRanges]). */
    fun snippetRanges(snippet: String, query: String): List<IntRange> = matchRanges(snippet, query)
}
