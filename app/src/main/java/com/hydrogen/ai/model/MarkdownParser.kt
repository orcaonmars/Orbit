package com.hydrogen.ai.model

/** One run of inline text with its formatting. [url] is only ever an http(s) link. */
data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val url: String? = null
)

/** Block-level structure of a chat reply, kept free of Compose so it can be unit tested. */
sealed interface MdBlock {
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class ListItem(val marker: String, val indent: Int, val spans: List<MdSpan>) : MdBlock
    data class Quote(val spans: List<MdSpan>) : MdBlock

    /** [closed] is false while a streaming reply has opened a fence but not yet closed it. */
    data class CodeBlock(val language: String, val code: String, val closed: Boolean) : MdBlock
    data object Rule : MdBlock

    /** GitHub-style table. Every row has exactly [align].size cells. */
    data class Table(
        val header: List<List<MdSpan>>,
        val align: List<MdAlign>,
        val rows: List<List<List<MdSpan>>>
    ) : MdBlock
}

enum class MdAlign { START, CENTER, END }

/**
 * Small, forgiving Markdown subset used by chat models: fenced code, headings, bullet/numbered
 * lists, block quotes, rules and inline bold/italic/code/links. Never throws; unknown syntax
 * falls through as plain text so no reply content is ever lost.
 */
object MarkdownParser {
    const val MAX_INPUT_CHARS = 200_000

    private val FENCE = Regex("""^(`{3,}|~{3,})\s*([^\s`]*).*$""")
    private val HEADING = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val RULE = Regex("""^(?:(?:\*\s*){3,}|(?:-\s*){3,}|(?:_\s*){3,})$""")
    private val LIST_ITEM = Regex("""^(\s*)([-*+]|\d{1,9}[.)])\s+(.*)$""")
    private const val ESCAPABLE = "\\`*_[]()#+-.!>~|"
    private val DELIMITER_CELL = Regex("""^:?-+:?$""")
    const val MAX_TABLE_COLUMNS = 20
    const val MAX_TABLE_ROWS = 500

    fun parse(markdown: String): List<MdBlock> {
        val lines = markdown.take(MAX_INPUT_CHARS).replace("\r\n", "\n").split('\n')
        val blocks = mutableListOf<MdBlock>()
        val paragraph = mutableListOf<String>()
        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MdBlock.Paragraph(parseInline(paragraph.joinToString("\n")))
                paragraph.clear()
            }
        }

        // Raw text of the last list item, so the lines under it ("   **Answer:** 3/4") stay part of that item
        // instead of starting a paragraph at the left edge. Null once anything else comes between.
        var itemText: String? = null
        var blankSinceItem = false

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            if (itemText != null && trimmed.isNotEmpty()) {
                val indented = line.takeWhile { it == ' ' || it == '\t' }.replace("\t", "    ").length >= 2
                val plainLine = LIST_ITEM.matchEntire(line) == null && HEADING.matchEntire(trimmed) == null &&
                    !RULE.matches(trimmed) && !trimmed.startsWith(">") && !trimmed.startsWith("|") &&
                    FENCE.matchEntire(trimmed) == null &&
                    !('|' in trimmed && i + 1 < lines.size && startTable(trimmed, lines[i + 1].trim()) != null)
                if (plainLine && (indented || !blankSinceItem)) {
                    itemText += (if (blankSinceItem) "\n\n" else "\n") + trimmed
                    blocks[blocks.lastIndex] = (blocks.last() as MdBlock.ListItem).copy(spans = parseInline(itemText))
                    blankSinceItem = false
                    i++
                    continue
                }
                itemText = null
            }
            val fence = FENCE.matchEntire(trimmed)?.takeUnless {
                // "```inline```" on one line is not an opening fence.
                trimmed.substring(it.groupValues[1].length).contains(it.groupValues[1])
            }
            if (fence != null) {
                flushParagraph()
                val marker = fence.groupValues[1]
                val codeLines = mutableListOf<String>()
                var closed = false
                i++
                while (i < lines.size) {
                    val candidate = lines[i].trim()
                    if (candidate.length >= marker.length && candidate.all { it == marker[0] }) {
                        closed = true
                        i++
                        break
                    }
                    codeLines += lines[i]
                    i++
                }
                blocks += MdBlock.CodeBlock(fence.groupValues[2], codeLines.joinToString("\n"), closed)
                continue
            }

            val table = if ('|' in trimmed && i + 1 < lines.size) startTable(trimmed, lines[i + 1].trim()) else null
            if (table != null) {
                flushParagraph()
                val (header, align) = table
                val rows = mutableListOf<List<List<MdSpan>>>()
                i += 2
                while (i < lines.size && rows.size < MAX_TABLE_ROWS) {
                    val row = lines[i].trim()
                    if (row.isEmpty() || '|' !in row) break
                    val cells = splitRow(row)
                    rows += List(align.size) { c -> parseInline(cells.getOrElse(c) { "" }) }
                    i++
                }
                blocks += MdBlock.Table(header.map(::parseInline), align, rows)
                continue
            }

            val heading = HEADING.matchEntire(trimmed)
            val listItem = LIST_ITEM.matchEntire(line)
            when {
                trimmed.isEmpty() -> {
                    flushParagraph()
                    blankSinceItem = true
                }
                heading != null -> {
                    flushParagraph()
                    blocks += MdBlock.Heading(heading.groupValues[1].length, parseInline(heading.groupValues[2]))
                }
                RULE.matches(trimmed) -> {
                    flushParagraph()
                    blocks += MdBlock.Rule
                }
                listItem != null -> {
                    flushParagraph()
                    val rawMarker = listItem.groupValues[2]
                    val marker = if (rawMarker[0].isDigit()) rawMarker.dropLast(1) + "." else "•"
                    val indent = (listItem.groupValues[1].replace("\t", "    ").length / 2).coerceAtMost(4)
                    blocks += MdBlock.ListItem(marker, indent, parseInline(listItem.groupValues[3]))
                    itemText = listItem.groupValues[3]
                    blankSinceItem = false
                }
                trimmed.startsWith(">") -> {
                    flushParagraph()
                    blocks += MdBlock.Quote(parseInline(trimmed.removePrefix(">").trimStart()))
                }
                else -> paragraph += trimmed
            }
            i++
        }
        flushParagraph()
        return blocks
    }

    /** Header line + delimiter line (`|---|:--:|`) with the same column count, or null. */
    private fun startTable(headerLine: String, delimiterLine: String): Pair<List<String>, List<MdAlign>>? {
        if ('-' !in delimiterLine || '|' !in delimiterLine) return null
        val specs = splitRow(delimiterLine)
        if (specs.isEmpty() || specs.size > MAX_TABLE_COLUMNS || specs.any { !DELIMITER_CELL.matches(it) }) return null
        val header = splitRow(headerLine)
        if (header.size != specs.size) return null
        val align = specs.map {
            when {
                it.startsWith(':') && it.endsWith(':') -> MdAlign.CENTER
                it.endsWith(':') -> MdAlign.END
                else -> MdAlign.START
            }
        }
        return header to align
    }

    /** Splits `| a | b |` into trimmed cells; `\|` and pipes inside `code` stay in the cell. */
    internal fun splitRow(row: String): List<String> {
        var body = row.trim()
        if (body.startsWith('|')) body = body.substring(1)
        if (body.endsWith('|') && !body.endsWith("\\|")) body = body.dropLast(1)
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var inCode = false
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length && body[i + 1] == '|' -> {
                    // parseInline unescapes \| outside code; inside a code span keep a bare pipe.
                    cell.append(if (inCode) "|" else "\\|")
                    i += 2
                    continue
                }
                c == '`' -> inCode = !inCode
                c == '|' && !inCode -> {
                    cells += cell.toString().trim()
                    cell.clear()
                    i++
                    continue
                }
            }
            cell.append(c)
            i++
        }
        cells += cell.toString().trim()
        return cells
    }

    fun parseInline(text: String): List<MdSpan> {
        val out = mutableListOf<MdSpan>()
        val buf = StringBuilder()
        var bold = false
        var italic = false
        fun emit() {
            if (buf.isNotEmpty()) {
                out += MdSpan(buf.toString(), bold = bold, italic = italic)
                buf.clear()
            }
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            val prev = if (i > 0) text[i - 1] else ' '
            when {
                c == '\\' && i + 1 < text.length && text[i + 1] in ESCAPABLE -> {
                    buf.append(text[i + 1])
                    i += 2
                }
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        emit()
                        out += MdSpan(text.substring(i + 1, end), bold = bold, italic = italic, code = true)
                        i = end + 1
                    } else {
                        buf.append(c)
                        i++
                    }
                }
                c == '[' -> {
                    val link = parseLink(text, i)
                    if (link != null) {
                        emit()
                        val (label, url, next) = link
                        out += MdSpan(label, bold = bold, italic = italic, url = url.takeIf(::isSafeUrl))
                        i = next
                    } else {
                        buf.append(c)
                        i++
                    }
                }
                (c == '*' || c == '_') && i + 1 < text.length && text[i + 1] == c -> {
                    val delimiter = "$c$c"
                    val opens = !bold && canOpen(text, i + 2, prev, c) && text.indexOf(delimiter, i + 2) > i + 2
                    if (bold || opens) {
                        emit()
                        bold = !bold
                    } else {
                        buf.append(delimiter)
                    }
                    i += 2
                }
                c == '*' || c == '_' -> {
                    val closes = italic && prev != ' ' &&
                        (c == '*' || i + 1 >= text.length || !text[i + 1].isLetterOrDigit())
                    val opens = !italic && canOpen(text, i + 1, prev, c) && hasCloser(text, i + 1, c)
                    if (closes || opens) {
                        emit()
                        italic = !italic
                    } else {
                        buf.append(c)
                    }
                    i++
                }
                else -> {
                    buf.append(c)
                    i++
                }
            }
        }
        emit()
        return out
    }

    /** Opening delimiter must be followed by non-space; `_` must not sit inside a word (snake_case). */
    private fun canOpen(text: String, after: Int, prev: Char, c: Char): Boolean =
        after < text.length && !text[after].isWhitespace() && !(c == '_' && prev.isLetterOrDigit())

    private fun hasCloser(text: String, from: Int, c: Char): Boolean {
        var j = text.indexOf(c, from)
        while (j > from) {
            val before = text[j - 1]
            val after = if (j + 1 < text.length) text[j + 1] else ' '
            if (!before.isWhitespace() && after != c && (c == '*' || !after.isLetterOrDigit())) return true
            j = text.indexOf(c, j + 1)
        }
        return false
    }

    private fun parseLink(text: String, start: Int): Triple<String, String, Int>? {
        val labelEnd = text.indexOf(']', start + 1)
        if (labelEnd <= start + 1 || labelEnd + 1 >= text.length || text[labelEnd + 1] != '(') return null
        // Wikipedia-style URLs contain balanced parentheses: https://en.wikipedia.org/wiki/Foo_(bar).
        // Stop only at the ')' that closes the destination, so the link is not cut short mid-URL.
        val urlEnd = closingParen(text, labelEnd + 2) ?: return null
        if (urlEnd <= labelEnd + 2) return null
        val url = unescapeParens(text.substring(labelEnd + 2, urlEnd).trim())
        if (url.any { it.isWhitespace() }) return null
        return Triple(text.substring(start + 1, labelEnd), url, urlEnd + 1)
    }

    /** `\(` / `\)` in a destination are Markdown escapes for the literal characters. */
    private fun unescapeParens(url: String): String {
        if ('\\' !in url) return url
        val out = StringBuilder(url.length)
        var i = 0
        while (i < url.length) {
            val c = url[i]
            if (c == '\\' && i + 1 < url.length && (url[i + 1] == '(' || url[i + 1] == ')')) {
                out.append(url[i + 1])
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    /** Index of the `)` that closes the destination opened at [from] - 1, honouring nesting and `\)`. */
    private fun closingParen(text: String, from: Int): Int? {
        var depth = 1
        var i = from
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i += 2
                '(' -> {
                    depth++
                    i++
                }
                ')' -> {
                    depth--
                    if (depth == 0) return i
                    i++
                }
                else -> i++
            }
        }
        return null
    }

    /** Only plain web links are made clickable; never javascript:, intent:, file: or content:. */
    fun isSafeUrl(url: String): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true) && !uri.scheme.equals("http", ignoreCase = true)) return false
        // A clickable destination needs an actual host, not a bare scheme or misleading user-info prefix.
        return !uri.host.isNullOrBlank() && uri.rawUserInfo == null && (uri.port == -1 || uri.port in 1..65535)
    }
}
