package com.hydrogen.ai.model

/**
 * Turns a Markdown reply into text that sounds natural when read aloud: no "asterisk asterisk", no URLs,
 * and code blocks replaced by a short spoken note. [chunk] then splits it at sentence boundaries into
 * pieces small enough for Android's TextToSpeech (whose per-utterance limit is about 4000 characters).
 */
object SpeakableText {
    /** Hard ceiling for one utterance, kept for callers that pass an explicit size. */
    const val MAX_CHUNK = 3900

    /** Default chunk size: short utterances make pause/stop responsive and limits engine glitches. */
    const val MAX_SENTENCE_CHUNK = 300

    const val CODE_BLOCK_NOTE = "(code block shown on screen)"

    private val image = Regex(
        "!\\[([^\\]\\n]*)]\\((?:<[^>\\n]*>|[^()\\s]*(?:\\([^()\\s]*\\)[^()\\s]*)*)" +
            "(?:\\s+(?:\"[^\"\\n]*\"|'[^'\\n]*'))?\\)"
    )
    private val imageRef = Regex("!\\[([^\\]\\n]*)]\\[[^\\]\\n]*]")
    private val link = Regex(
        "\\[([^\\]\\n]+)]\\((?:<[^>\\n]*>|[^()\\s]*(?:\\([^()\\s]*\\)[^()\\s]*)*)" +
            "(?:\\s+(?:\"[^\"\\n]*\"|'[^'\\n]*'))?\\)"
    )
    private val linkRef = Regex("\\[([^\\]\\n]+)]\\[[^\\]\\n]*]")
    private val referenceDefinition = Regex("(?m)^[ \\t]{0,3}\\[[^\\]\\n]+]:[ \\t]*\\S+.*$")
    private val autoLink = Regex("<(https?://[^>\\s]+)>")
    private val bareUrl = Regex("https?://[^\\s<>]+")
    private val bareWww = Regex("(?i)\\bwww\\.[a-z0-9][a-z0-9.-]*\\S*")
    private val inlineCode = Regex("`([^`\\n]*)`")
    private val heading = Regex("(?m)^[ \\t]{0,3}#{1,6}\\s+")
    private val setextHeading = Regex("(?m)^[ \\t]{0,3}={2,}[ \\t]*$")
    private val blockquote = Regex("(?m)^[ \\t]{0,3}>[ \\t]?")
    private val bullet = Regex("(?m)^[ \\t]*[-*+][ \\t]+")
    private val rule = Regex("(?m)^[ \\t]*([-*_])([ \\t]*\\1){2,}[ \\t]*$")
    private val tableDivider = Regex(
        "(?m)^[ \\t]*\\|?[ \\t]*:?-{2,}:?[ \\t]*(\\|[ \\t]*:?-{2,}:?[ \\t]*)*\\|?[ \\t]*$"
    )
    private val strongOrStrike = Regex("(\\*\\*|__|~~)(.+?)\\1")
    private val emphasis = Regex("(?<![\\w*])[*_](?!\\s)(.+?)(?<!\\s)[*_](?![\\w*])")
    private val escapedMarkdown = Regex("\\\\([\\\\`*_{}\\[\\]()#+\\-.!>|~])")
    private val extraBlankLines = Regex("\\n{3,}")
    private val spaces = Regex("[ \\t]{2,}")
    private val percent = Regex("%")
    private val rightArrow = Regex("(?:->|=>|\u2192|\u21D2)")
    private val leftArrow = Regex("(?:<-|\u2190|\u21D0)")
    private val times = Regex("\u00D7")
    private val dividedBy = Regex("\u00F7")
    private val ampersand = Regex("&")
    private val emoji = Regex(
        "[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{26FF}\\x{2700}-\\x{27BF}" +
            "\\x{FE0F}\\x{200D}\\x{20E3}]"
    )
    private val initialism = Regex("(?:[a-z]\\.)+[a-z]")

    private val abbreviations = setOf(
        "e.g", "i.e", "etc", "vs", "mr", "mrs", "ms", "dr", "prof", "sr", "jr",
        "st", "mt", "fig", "figs", "no", "nos", "vol", "approx", "dept", "est",
        "inc", "ltd", "co", "corp", "ph.d", "a.m", "p.m", "u.s", "u.k", "u.n",
        "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec"
    )

    fun fromMarkdown(markdown: String): String {
        var text = markdown.replace("\r\n", "\n").replace('\r', '\n')
        text = stripFencedCode(text)
        text = referenceDefinition.replace(text, "")
        text = imageRef.replace(text) { it.groupValues[1] }
        text = image.replace(text) { it.groupValues[1] }
        text = linkRef.replace(text) { it.groupValues[1] }
        text = link.replace(text) { it.groupValues[1] }
        text = autoLink.replace(text, "link")
        // Preserve sentence punctuation consumed by the bare URL match so TTS still pauses naturally.
        text = bareUrl.replace(text) { "link" + it.value.takeLastWhile { c -> c in ".,!?;:" } }
        text = bareWww.replace(text) { "link" + it.value.takeLastWhile { c -> c in ".,!?;:" } }
        text = inlineCode.replace(text) { it.groupValues[1] }
        text = speakTables(text)
        text = setextHeading.replace(text, "")
        text = rule.replace(text, "")
        text = heading.replace(text, "")
        text = blockquote.replace(text, "")
        text = bullet.replace(text, "")
        text = strongOrStrike.replace(text) { it.groupValues[2] }
        text = emphasis.replace(text) { it.groupValues[1] }
        text = escapedMarkdown.replace(text) { it.groupValues[1] }
        text = text
            .replace("&nbsp;", " ", ignoreCase = true)
            .replace("&amp;", " and ", ignoreCase = true)
            .replace("&lt;", " less than ", ignoreCase = true)
            .replace("&gt;", " greater than ", ignoreCase = true)
        text = percent.replace(text, " percent")
        text = rightArrow.replace(text, " to ")
        text = leftArrow.replace(text, " from ")
        text = times.replace(text, " times ")
        text = dividedBy.replace(text, " divided by ")
        text = ampersand.replace(text, " and ")
        text = emoji.replace(text, "")
        text = text.lines().joinToString("\n") { it.trim() }
        text = spaces.replace(text, " ")
        text = extraBlankLines.replace(text, "\n\n")
        return text.trim()
    }

    /** Splits [text] into pieces of at most [max] characters, preferring sentence and paragraph ends. */
    fun chunk(text: String, max: Int = MAX_SENTENCE_CHUNK): List<String> {
        require(max > 0)
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotBlank()) out += current.toString().trim()
            current.clear()
        }
        for (sentence in splitSentences(trimmed)) {
            val piece = sentence.trim()
            if (piece.isEmpty()) continue
            if (piece.length > max) {
                flush()
                out += hardSplit(piece, max)
                continue
            }
            if (current.isNotEmpty() && current.length + 1 + piece.length > max) flush()
            if (current.isNotEmpty()) current.append(' ')
            current.append(piece)
        }
        flush()
        return out
    }

    private class Fence(val start: Int, val marker: Char, val length: Int, val lineEnd: Int)

    /**
     * Replaces ``` and ~~~ fences (including an unclosed fence while a reply is still streaming) with
     * [CODE_BLOCK_NOTE]. Inline runs such as "```x```" are not fences and are left for [inlineCode].
     */
    private fun stripFencedCode(text: String): String {
        if (!text.contains("```") && !text.contains("~~~")) return text
        val out = StringBuilder()
        var index = 0
        while (index < text.length) {
            val open = findFenceOpen(text, index) ?: break
            out.append(text, index, open.start)
            val close = findFenceEnd(text, open)
            out.append(' ').append(CODE_BLOCK_NOTE).append(' ')
            index = close ?: text.length
        }
        if (index < text.length) out.append(text, index, text.length)
        return out.toString()
    }

    private fun findFenceOpen(text: String, from: Int): Fence? {
        var lineStart = from
        while (lineStart <= text.length) {
            val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
            val line = text.substring(lineStart, lineEnd)
            val indent = line.indexOfFirst { it != ' ' && it != '\t' }
            if (indent in 0..3) {
                val marker = line.getOrNull(indent)
                if (marker == '`' || marker == '~') {
                    var run = 0
                    while (indent + run < line.length && line[indent + run] == marker) run++
                    val info = line.substring(indent + run)
                    if (run >= 3 && (marker != '`' || '`' !in info)) {
                        return Fence(lineStart, marker, run, lineEnd)
                    }
                }
            }
            if (lineEnd >= text.length) break
            lineStart = lineEnd + 1
        }
        return null
    }

    private fun findFenceEnd(text: String, open: Fence): Int? {
        if (open.lineEnd >= text.length) return null
        var lineStart = open.lineEnd + 1
        while (lineStart <= text.length) {
            val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
            val line = text.substring(lineStart, lineEnd)
            val indent = line.indexOfFirst { it != ' ' && it != '\t' }
            if (indent in 0..3 && line.getOrNull(indent) == open.marker) {
                var run = 0
                while (indent + run < line.length && line[indent + run] == open.marker) run++
                if (run >= open.length && line.substring(indent + run).isBlank()) return lineEnd
            }
            if (lineEnd >= text.length) break
            lineStart = lineEnd + 1
        }
        return null
    }

    /** Turns Markdown table rows next to an alignment row into spoken comma lists. */
    private fun speakTables(text: String): String {
        if ('|' !in text) return text
        val lines = text.split('\n')
        val divider = BooleanArray(lines.size) { tableDivider.matches(lines[it].trim()) }
        if (!divider.any { it }) return text
        val out = lines.toMutableList()
        for (i in lines.indices) {
            if (!divider[i]) continue
            out[i] = ""
            var before = i - 1
            while (before >= 0 && !divider[before] && lines[before].isNotBlank() &&
                hasCellSeparator(lines[before])
            ) {
                out[before] = speakTableRow(lines[before])
                before--
            }
            var after = i + 1
            while (after < lines.size && !divider[after] && lines[after].isNotBlank() &&
                hasCellSeparator(lines[after])
            ) {
                out[after] = speakTableRow(lines[after])
                after++
            }
        }
        return out.joinToString("\n")
    }

    private fun hasCellSeparator(line: String): Boolean {
        var i = 0
        while (i < line.length) {
            if (line[i] == '\\' && i + 1 < line.length) {
                i += 2
                continue
            }
            if (line[i] == '|') return true
            i++
        }
        return false
    }

    private fun speakTableRow(line: String): String {
        val cells = splitTableCells(line.trim())
        val first = if (cells.firstOrNull()?.isBlank() == true) 1 else 0
        val last = if (cells.size > first && cells.last().isBlank()) cells.size - 1 else cells.size
        return cells.subList(first, last)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    }

    private fun splitTableCells(row: String): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var i = 0
        while (i < row.length) {
            val c = row[i]
            if (c == '\\' && i + 1 < row.length && row[i + 1] == '|') {
                cell.append('|')
                i += 2
                continue
            }
            if (c == '|') {
                cells += cell.toString()
                cell.clear()
            } else {
                cell.append(c)
            }
            i++
        }
        cells += cell.toString()
        return cells
    }

    /** Sentence boundaries that keep abbreviations, decimals, initials and URLs together. */
    private fun splitSentences(text: String): List<String> {
        val sentences = mutableListOf<String>()
        var start = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\n') {
                addSentence(text, start, i, sentences)
                var next = i
                while (next < text.length && text[next] == '\n') next++
                start = next
                i = next
                continue
            }
            if (c == '.' || c == '!' || c == '?') {
                var punctuationEnd = i
                while (punctuationEnd < text.length &&
                    (text[punctuationEnd] == '.' || text[punctuationEnd] == '!' || text[punctuationEnd] == '?')
                ) {
                    punctuationEnd++
                }
                var end = punctuationEnd
                while (end < text.length && text[end] in "\"')]}”’") end++
                if (end < text.length && text[end].isWhitespace() &&
                    isSentenceEnd(text, i, punctuationEnd)
                ) {
                    addSentence(text, start, end, sentences)
                    var next = end
                    while (next < text.length && text[next].isWhitespace() && text[next] != '\n') next++
                    start = next
                    i = next
                    continue
                }
                i = punctuationEnd
                continue
            }
            i++
        }
        addSentence(text, start, text.length, sentences)
        return sentences
    }

    private fun addSentence(text: String, start: Int, end: Int, into: MutableList<String>) {
        val sentence = text.substring(start, end).trim()
        if (sentence.isNotEmpty()) into += sentence
    }

    private fun isSentenceEnd(text: String, dot: Int, punctuationEnd: Int): Boolean {
        val run = text.substring(dot, punctuationEnd)
        if (run.any { it == '!' || it == '?' }) return true
        if (run.length > 1) return false // ellipsis
        if (dot > 0 && dot + 1 < text.length &&
            text[dot - 1].isDigit() && text[dot + 1].isDigit()
        ) {
            return false
        }
        val tokenStart = text.lastIndexOfAny(charArrayOf(' ', '\t', '\n', '(', '"', '\''), dot - 1) + 1
        val token = text.substring(tokenStart, dot)
        if (token.isEmpty()) return false
        if (token.contains("://") || token.startsWith("www.") || token.contains('@')) return false
        val lower = token.lowercase()
        if (lower in abbreviations) return false
        if (token.length == 1 && token[0] in 'A'..'Z') return false // J. K. Rowling
        return !initialism.matches(lower)
    }

    private fun hardSplit(piece: String, max: Int): List<String> {
        val parts = mutableListOf<String>()
        var remaining = piece.trim()
        while (remaining.length > max) {
            val spaceCut = remaining.lastIndexOf(' ', max)
            val head = remaining.substring(0, max)
            val punctuation = head.indexOfLast { it == ',' || it == ';' || it == ':' || it == '\u2014' }
            val punctuationCut = if (punctuation >= max / 3) punctuation + 1 else -1
            val cut = maxOf(spaceCut, punctuationCut).takeIf { it > 0 } ?: max
            val part = remaining.substring(0, cut).trim()
            if (part.isNotEmpty()) parts += part
            remaining = remaining.substring(cut).trim()
        }
        if (remaining.isNotEmpty()) parts += remaining
        return parts
    }
}
