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
        if (!hasDocument(content)) return listOf(ReplyPart.Text(content))
        val parts = mutableListOf<ReplyPart>()
        var rest = content
        while (true) {
            val open = OPEN_TAG.find(rest)
            if (open == null) {
                if (rest.isNotBlank()) parts += ReplyPart.Text(rest.trim())
                break
            }
            val before = rest.substring(0, open.range.first)
            if (before.isNotBlank()) parts += ReplyPart.Text(before.trim())
            val afterOpen = rest.substring(open.range.last + 1)
            val close = CLOSE_TAG.find(afterOpen)
            val rawBody = if (close == null) afterOpen else afterOpen.substring(0, close.range.first)
            val body = OUTER_FENCE.find(rawBody)?.groupValues?.get(1) ?: rawBody
            val title = open.groupValues[1].trim().ifEmpty { titleOf(body) }
            parts += ReplyPart.Document(title, body.trim('\n', '\r', ' '), complete = close != null)
            if (close == null) break
            rest = afterOpen.substring(close.range.last + 1)
        }
        return parts
    }

    /** True when the reply holds at least one document. */
    fun hasDocument(content: String): Boolean = OPEN_TAG.containsMatchIn(content)

    /** The reply with document tags removed, for copying, reading aloud and sharing as text. */
    fun plainText(content: String): String =
        if (!hasDocument(content)) content else content.replace(OPEN_TAG, "").replace(CLOSE_TAG, "").trim()

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
        val question = (index - 1 downTo 0).map { messages[it] }.firstOrNull { it.role == MessageRole.USER }
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
