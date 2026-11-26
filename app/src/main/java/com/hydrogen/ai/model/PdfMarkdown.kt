package com.hydrogen.ai.model

/** A reply split into ordinary text and the documents the model wrote between `<pdf>` tags. */
sealed class ReplyPart {
    data class Text(val markdown: String) : ReplyPart()

    /** [complete] is false while the closing tag has not arrived yet (still streaming, or the reply was cut off). */
    data class Document(val title: String, val markdown: String, val complete: Boolean) : ReplyPart()
}

/**
 * AI-made documents. The model puts a document in Markdown between `<pdf title="...">` and `</pdf>` (see
 * [OrbitInstructions.DEFAULT_STYLE]); the app shows it as a card and renders it to a PDF with [MarkdownParser], so
 * the file matches what the chat shows. Pure Kotlin, so it is unit tested without Android.
 */
object PdfMarkdown {

    private val OPEN_TAG = Regex(
        "<pdf(?:\\s+title\\s*=\\s*[\"“'](.{0,160}?)[\"”'])?\\s*>",
        RegexOption.IGNORE_CASE
    )
    private val CLOSE_TAG = Regex("</pdf\\s*>", RegexOption.IGNORE_CASE)

    /** A whole document wrapped in one code fence would print as code, so a lone outer fence is removed. */
    private val OUTER_FENCE = Regex("^\\s*```[a-zA-Z]*[ \\t]*\\n(.*)\\n\\s*```\\s*$", RegexOption.DOT_MATCHES_ALL)

    /**
     * Splits [content] into text and documents. A document whose closing tag never came keeps everything after its
     * opening tag and is marked incomplete. Text parts that are only blank space are left out.
     */
    fun splitReply(content: String): List<ReplyPart> {
        val tags = documentTags(content)
        if (tags.none { it.open }) return listOf(ReplyPart.Text(content))
        val parts = mutableListOf<ReplyPart>()
        var cursor = 0
        var i = 0
        while (i < tags.size) {
            val tag = tags[i++]
            if (!tag.open) continue
            val open = tag.match
            val before = content.substring(cursor, open.range.first)
            if (before.isNotBlank()) parts += ReplyPart.Text(before.trim())
            while (i < tags.size && tags[i].open) i++
            val close = tags.getOrNull(i)?.match
            val rawBody = content.substring(open.range.last + 1, close?.range?.first ?: content.length)
            val body = OUTER_FENCE.find(rawBody)?.groupValues?.get(1) ?: rawBody
            val title = open.groupValues[1].trim().ifEmpty { titleOf(body) }
            parts += ReplyPart.Document(title, body.trim('\n', '\r', ' '), complete = close != null)
            if (close == null) return parts
            cursor = close.range.last + 1
            i++
        }
        val after = content.substring(cursor)
        if (after.isNotBlank()) parts += ReplyPart.Text(after.trim())
        return parts
    }

    /** True only for actual document markup, not examples inside code. */
    fun hasDocument(content: String): Boolean = documentTags(content).any { it.open }

    /** Remove actual document wrappers while preserving literal code and unmatched closing tags. */
    fun plainText(content: String): String {
        val tags = documentTags(content)
        if (tags.none { it.open }) return content
        val out = StringBuilder(content.length)
        var cursor = 0
        var inside = false
        for (tag in tags) {
            if (tag.open == inside) continue
            out.append(content, cursor, tag.match.range.first)
            cursor = tag.match.range.last + 1
            inside = tag.open
        }
        out.append(content, cursor, content.length)
        return out.toString().trim()
    }

    private data class DocumentTag(val match: MatchResult, val open: Boolean)
    private val FENCE_LINE = Regex("(?m)^[ \t]{0,3}(`{3,}|~{3,})([^\r\n]*)")
    private val BACKTICK_RUN = Regex("`+")

    private fun documentTags(content: String): List<DocumentTag> {
        if (!OPEN_TAG.containsMatchIn(content)) return emptyList()
        val protectedRanges = codeRanges(content).sortedBy { it.first }
        val candidates = (OPEN_TAG.findAll(content).map { DocumentTag(it, true) } +
            CLOSE_TAG.findAll(content).map { DocumentTag(it, false) })
            .sortedBy { it.match.range.first }.toList()
        var rangeIndex = 0
        return candidates.filter { tag ->
            val position = tag.match.range.first
            while (rangeIndex < protectedRanges.size && protectedRanges[rangeIndex].last < position) rangeIndex++
            !escapedAt(content, position) && protectedRanges.getOrNull(rangeIndex)?.contains(position) != true
        }
    }

    /** Markdown fences can contain longer examples; only a matching fence closes them. */
    private fun codeRanges(content: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var opened: MatchResult? = null
        for (line in FENCE_LINE.findAll(content)) {
            val run = line.groupValues[1]
            val opening = opened
            if (opening == null) {
                if (run[0] != '`' || !line.groupValues[2].contains('`')) opened = line
            } else {
                val startRun = opening.groupValues[1]
                if (run[0] == startRun[0] && run.length >= startRun.length && line.groupValues[2].isBlank()) {
                    ranges += opening.range.first..line.range.last
                    opened = null
                }
            }
        }
        opened?.let { ranges += it.range.first until content.length }
        val fences = ranges.toList()
        var cursor = 0
        for (fence in fences) {
            inlineCodeRanges(content, cursor, fence.first, ranges)
            cursor = fence.last + 1
        }
        inlineCodeRanges(content, cursor, content.length, ranges)
        return ranges
    }

    private fun inlineCodeRanges(content: String, start: Int, end: Int, ranges: MutableList<IntRange>) {
        if (start >= end) return
        val runs = BACKTICK_RUN.findAll(content, start).takeWhile { it.range.first < end }.toList()
        val next = IntArray(runs.size) { -1 }
        val byLength = mutableMapOf<Int, Int>()
        for (i in runs.indices.reversed()) {
            next[i] = byLength.put(runs[i].value.length, i) ?: -1
        }
        var i = 0
        while (i < runs.size) {
            val close = next[i]
            if (close >= 0 && !escapedAt(content, runs[i].range.first)) {
                ranges += runs[i].range.first..runs[close].range.last
                i = close + 1
            } else i++
        }
    }

    private fun escapedAt(content: String, index: Int): Boolean {
        var i = index - 1
        while (i >= 0 && content[i] == '\\') i--
        return (index - 1 - i) % 2 != 0
    }
    /** True when the user's own words ask for a PDF, used to offer one for a reply that has no document. */
    fun asksForPdf(userText: String): Boolean = ASKS_FOR_PDF.containsMatchIn(userText)

    private val ASKS_FOR_PDF = Regex("\\bpdfs?\\b", RegexOption.IGNORE_CASE)

    /**
     * True for a finished reply at [index] with no document when the closest earlier user message asked for a PDF,
     * so the chat offers to make one from the reply.
     */
    fun offersPdf(messages: List<ChatMessage>, index: Int): Boolean {
        val reply = messages.getOrNull(index) ?: return false
        if (reply.role != MessageRole.ASSISTANT || reply.status != MessageStatus.COMPLETED) return false
        if (reply.content.isBlank() || hasDocument(reply.content)) return false
        val question = (index - 1 downTo 0).asSequence().map { messages[it] }.firstOrNull { it.role == MessageRole.USER }
        return question != null && asksForPdf(question.content)
    }

    private val LINE_MARKER = Regex("^(#{1,6}\\s+|>\\s?|[-*+]\\s+|\\d{1,3}[.)]\\s+)")

    /** A short title: the first heading, else the first line, else "Document". */
    fun titleOf(markdown: String): String {
        val lines = markdown.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("```") }
        val heading = lines.firstOrNull { it.startsWith("#") }
        val raw = heading ?: lines.firstOrNull().orEmpty()
        val spans = MarkdownParser.parseInline(raw.replace(LINE_MARKER, ""))
        val plain = spans.joinToString("") { it.text }.replace(Regex("\\s+"), " ").trim()
        return (if (plain.length > 60) plain.take(59).trimEnd() + "…" else plain).ifEmpty { "Document" }
    }

    /** A safe file name for [title], ending in ".pdf". */
    fun fileName(title: String): String {
        val clean = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}…]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.')
            .take(60)
            .trim(' ', '.')
        return clean.ifEmpty { "Document" } + ".pdf"
    }
}
