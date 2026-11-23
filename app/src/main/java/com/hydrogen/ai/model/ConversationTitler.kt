package com.hydrogen.ai.model

/**
 * Pure Kotlin helper that derives a short, readable conversation title from the first user message.
 * Linear time, input-capped, never throws, and has zero Android dependencies.
 */
object ConversationTitler {

    const val MAX_CHARS = 40
    const val MAX_INPUT_CHARS = 10_000
    const val DEFAULT_TITLE = "New chat"

    private const val ELLIPSIS = "…"

    private val LABEL_PREFIXES = listOf(
        "file:",
        "attachment:",
        "attached:",
        "attached file:",
        "image:",
        "pdf:",
        "code:",
        "code block:",
        "(file truncated:",
        "[pc file:",
        "[pc folder:",
        "[attachment:",
        "[attached:",
        "[image:",
        "[pdf:",
        "[file:",
        "[code:"
    )

    private val EXACT_BRACKET_LABELS = setOf(
        "[image]",
        "[attachment]",
        "[file]",
        "[pdf]",
        "[code]",
        "[code block]"
    )

    fun titleFor(firstUserMessage: String): String {
        if (firstUserMessage.isEmpty()) return DEFAULT_TITLE

        val capped = capInput(firstUserMessage)
        val normalized = capped.replace("\r\n", "\n").replace('\r', '\n')

        val candidate = extractFirstMeaningfulLine(normalized) ?: return DEFAULT_TITLE
        val sentence = takeFirstSentence(candidate)
        val collapsed = collapseWhitespace(sentence)
        if (collapsed.isEmpty() || !hasMeaningfulContent(collapsed)) return DEFAULT_TITLE

        val capitalized = collapsed.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase() else ch.toString()
        }

        return truncateAtWordBoundary(capitalized)
    }

    private fun capInput(input: String): String {
        if (input.length <= MAX_INPUT_CHARS) return input
        var end = MAX_INPUT_CHARS
        if (end > 0 && input[end - 1].isHighSurrogate()) {
            end--
        }
        return input.substring(0, end)
    }

    private fun extractFirstMeaningfulLine(text: String): String? {
        var inFenceChar = '\u0000'
        var inFenceLen = 0
        var lineStart = 0

        while (lineStart <= text.length) {
            val nl = text.indexOf('\n', lineStart)
            val lineEnd = if (nl == -1) text.length else nl
            val line = text.substring(lineStart, lineEnd)
            lineStart = if (nl == -1) text.length + 1 else nl + 1

            val trimmed = line.trim()
            if (inFenceLen > 0) {
                if (isClosingFence(trimmed, inFenceChar, inFenceLen)) {
                    inFenceChar = '\u0000'
                    inFenceLen = 0
                }
                continue
            }

            val fence = detectOpeningFence(trimmed)
            if (fence != null) {
                val (fenceChar, fenceLen) = fence
                if (!isSingleLineFencedBlock(trimmed, fenceChar, fenceLen)) {
                    inFenceChar = fenceChar
                    inFenceLen = fenceLen
                }
                continue
            }

            if (trimmed.isEmpty() || isAttachmentOrCodeLabel(trimmed)) {
                continue
            }

            val withoutLinksAndUrls = stripUrls(stripMarkdownLinksAndImages(trimmed))
            val stripped = stripMarkdownMarkers(withoutLinksAndUrls)
            val cleaned = collapseWhitespace(stripped)
            if (cleaned.isNotEmpty() && hasMeaningfulContent(cleaned)) {
                return cleaned
            }
        }
        return null
    }

    private fun detectOpeningFence(trimmed: String): Pair<Char, Int>? {
        if (trimmed.length < 3) return null
        val ch = trimmed[0]
        if (ch != '`' && ch != '~') return null
        var count = 0
        while (count < trimmed.length && trimmed[count] == ch) {
            count++
        }
        return if (count >= 3) ch to count else null
    }

    private fun isSingleLineFencedBlock(trimmed: String, fenceChar: Char, fenceLen: Int): Boolean {
        if (trimmed.length < fenceLen * 2) return false
        var trailing = 0
        var idx = trimmed.length - 1
        while (idx >= fenceLen && trimmed[idx] == fenceChar) {
            trailing++
            idx--
        }
        return trailing >= fenceLen
    }

    private fun isClosingFence(trimmed: String, fenceChar: Char, minLen: Int): Boolean {
        if (trimmed.length < minLen) return false
        for (i in trimmed.indices) {
            if (trimmed[i] != fenceChar) return false
        }
        return true
    }

    private fun isAttachmentOrCodeLabel(trimmed: String): Boolean {
        val lower = trimmed.lowercase()
        if (lower in EXACT_BRACKET_LABELS) return true
        return LABEL_PREFIXES.any { lower.startsWith(it) }
    }
    /**
     * Linear-time converter:
     * - `![alt](url)` -> dropped
     * - `[label](url)` -> `label`
     */
    private fun stripMarkdownLinksAndImages(line: String): String {
        if (line.indexOf('[') == -1) return line
        val out = StringBuilder(line.length)
        var i = 0
        while (i < line.length) {
            val isImage = line[i] == '!' && i + 1 < line.length && line[i + 1] == '['
            val bracketStart = when {
                isImage -> i + 1
                line[i] == '[' -> i
                else -> -1
            }
            if (bracketStart != -1) {
                val closeBracket = findClosingChar(line, bracketStart + 1, ']', '[')
                if (closeBracket != -1 && closeBracket + 1 < line.length && line[closeBracket + 1] == '(') {
                    val closeParen = findClosingChar(line, closeBracket + 2, ')', '(')
                    if (closeParen != -1) {
                        if (!isImage) {
                            out.append(line, bracketStart + 1, closeBracket)
                        }
                        i = closeParen + 1
                        continue
                    }
                }
            }
            out.append(line[i])
            i++
        }
        return out.toString()
    }

    private fun findClosingChar(s: String, start: Int, target: Char, nestedOpen: Char): Int {
        var depth = 1
        var i = start
        while (i < s.length) {
            when (s[i]) {
                nestedOpen -> depth++
                target -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private fun stripUrls(line: String): String {
        val out = StringBuilder(line.length)
        var i = 0
        while (i < line.length) {
            val schemeLen = matchUrlStart(line, i)
            if (schemeLen > 0) {
                i += schemeLen
                while (i < line.length && !line[i].isWhitespace() && line[i] !in "<>\"'") {
                    if (line[i] == ')' && out.isNotEmpty() && out[out.length - 1] == '(') {
                        break
                    }
                    i++
                }
                continue
            }
            out.append(line[i])
            i++
        }
        return out.toString()
    }

    private fun matchUrlStart(s: String, index: Int): Int {
        if (index > 0 && isAsciiAlphaNum(s[index - 1])) return 0
        val schemes = arrayOf("https://", "http://", "ftp://", "file://", "content://")
        for (scheme in schemes) {
            if (s.regionMatches(index, scheme, 0, scheme.length, ignoreCase = true)) {
                return if (index + scheme.length < s.length && !s[index + scheme.length].isWhitespace()) {
                    scheme.length
                } else {
                    0
                }
            }
        }
        if (s.regionMatches(index, "www.", 0, 4, ignoreCase = true)) {
            if (index + 4 < s.length && !s[index + 4].isWhitespace()) {
                return 4
            }
        }
        return 0
    }
    private fun stripMarkdownMarkers(line: String): String {
        var s = stripLeadingLineMarkers(line)
        s = stripTrailingHeadingHashes(s)
        if (s.isEmpty()) return ""

        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when (ch) {
                '*', '`' -> {
                    // Drop inline bold/italic/code markers.
                }
                '~' -> {
                    if (i + 1 < s.length && s[i + 1] == '~') {
                        i++ // Drop '~~' strikethrough markers.
                    } else {
                        out.append(ch)
                    }
                }
                '#' -> {
                    // Keep e.g. "C#" or "F#", strip Markdown "#" markers.
                    if (i > 0 && isAsciiAlphaNum(s[i - 1])) {
                        out.append(ch)
                    }
                }
                '_' -> {
                    // Preserve snake_case underscores flanked by letters/digits; strip emphasis markers.
                    val prevAlphaNum = i > 0 && isAsciiAlphaNum(s[i - 1])
                    val nextAlphaNum = i + 1 < s.length && isAsciiAlphaNum(s[i + 1])
                    if (prevAlphaNum && nextAlphaNum) {
                        out.append(ch)
                    }
                }
                else -> out.append(ch)
            }
            i++
        }
        return out.toString()
    }

    private fun stripLeadingLineMarkers(line: String): String {
        var idx = 0
        val len = line.length
        while (idx < len) {
            while (idx < len && line[idx].isWhitespace()) idx++
            if (idx >= len) break

            val ch = line[idx]
            if (ch == '>') {
                idx++
                continue
            }
            if (ch == '#') {
                while (idx < len && line[idx] == '#') idx++
                continue
            }
            if ((ch == '-' || ch == '*' || ch == '+' || ch == '•') &&
                (idx + 1 == len || line[idx + 1].isWhitespace())
            ) {
                idx++
                while (idx < len && line[idx].isWhitespace()) idx++
                idx = skipTaskCheckbox(line, idx)
                continue
            }
            val orderedEnd = matchOrderedListPrefix(line, idx)
            if (orderedEnd > idx) {
                idx = orderedEnd
                while (idx < len && line[idx].isWhitespace()) idx++
                idx = skipTaskCheckbox(line, idx)
                continue
            }
            break
        }
        return if (idx >= len) "" else line.substring(idx)
    }

    private fun skipTaskCheckbox(line: String, start: Int): Int {
        if (start + 2 < line.length && line[start] == '[' && line[start + 2] == ']') {
            val mid = line[start + 1]
            if (mid == ' ' || mid == 'x' || mid == 'X') {
                val after = start + 3
                if (after == line.length || line[after].isWhitespace()) {
                    return after
                }
            }
        }
        return start
    }

    private fun matchOrderedListPrefix(line: String, start: Int): Int {
        var i = start
        var digits = 0
        while (i < line.length && digits < 3 && line[i] in '0'..'9') {
            i++
            digits++
        }
        if (digits == 0 || i >= line.length) return start
        if (line[i] != '.' && line[i] != ')') return start
        val after = i + 1
        return if (after < line.length && line[after].isWhitespace()) after else start
    }
    private fun stripTrailingHeadingHashes(s: String): String {
        var end = s.length
        while (end > 0 && s[end - 1].isWhitespace()) end--
        var hashStart = end
        while (hashStart > 0 && s[hashStart - 1] == '#') hashStart--
        return if (hashStart < end && (hashStart == 0 || s[hashStart - 1].isWhitespace())) {
            s.substring(0, hashStart)
        } else {
            s
        }
    }

    private fun takeFirstSentence(line: String): String {
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '。' || ch == '！' || ch == '？') {
                var end = i + 1
                while (end < line.length && line[end] in charArrayOf('"', '\'', ')', ']', '”', '’', '」')) {
                    end++
                }
                return line.substring(0, end)
            }
            if (ch == '.' || ch == '!' || ch == '?') {
                var end = i + 1
                while (end < line.length && (line[end] == '.' || line[end] == '!' || line[end] == '?')) {
                    end++
                }
                while (end < line.length && line[end] in charArrayOf('"', '\'', ')', ']', '”', '’')) {
                    end++
                }
                if (end >= line.length || line[end].isWhitespace()) {
                    return line.substring(0, end)
                }
                i = end
                continue
            }
            i++
        }
        return line
    }

    private fun collapseWhitespace(text: String): String {
        val out = StringBuilder(text.length)
        var pendingSpace = false
        for (ch in text) {
            if (ch.isWhitespace()) {
                if (out.isNotEmpty()) pendingSpace = true
            } else {
                if (pendingSpace) {
                    out.append(' ')
                    pendingSpace = false
                }
                out.append(ch)
            }
        }
        return out.toString()
    }

    private fun hasMeaningfulContent(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            if (Character.isLetterOrDigit(cp) || isEmojiCodePoint(cp)) {
                return true
            }
            i += Character.charCount(cp)
        }
        return false
    }

    private fun isEmojiCodePoint(cp: Int): Boolean =
        cp in 0x1F300..0x1FAFF || cp in 0x2600..0x27BF

    private fun isAsciiAlphaNum(ch: Char): Boolean =
        ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9'

    private fun truncateAtWordBoundary(text: String): String {
        if (text.length <= MAX_CHARS) return text

        val budget = MAX_CHARS - ELLIPSIS.length
        val spaceIdx = text.lastIndexOf(' ', startIndex = budget)
        if (spaceIdx > 0) {
            val wordBoundaryPrefix = text.substring(0, spaceIdx).trimEnd()
            if (wordBoundaryPrefix.isNotEmpty() && hasMeaningfulContent(wordBoundaryPrefix)) {
                return wordBoundaryPrefix + ELLIPSIS
            }
        }

        var cutEnd = budget
        if (cutEnd > 0 && text[cutEnd - 1].isHighSurrogate()) {
            cutEnd--
        }
        val hardPrefix = text.substring(0, cutEnd).trimEnd()
        return if (hardPrefix.isEmpty()) DEFAULT_TITLE else hardPrefix + ELLIPSIS
    }
}
