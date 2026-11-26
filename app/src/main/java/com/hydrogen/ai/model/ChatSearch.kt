package com.hydrogen.ai.model

import java.text.Normalizer
import java.util.Locale

/**
 * Search helpers for finding text in chats: case- and diacritic-insensitive match ranges for highlighting, a
 * plain-text view of Markdown (fence markers and emphasis symbols removed, code kept) and a one-line snippet
 * around the first match. Pure Kotlin so it can be unit-tested without Android.
 *
 * Matching compares folded text: lower-cased, compatibility-normalised and stripped of non-spacing marks, so
 * "resume" finds "Résumé", "café" finds "cafe" and "strasse" finds "Straße". Ranges still point into the
 * original string, so callers can highlight accents and ligatures correctly.
 */
object ChatSearch {
    const val SNIPPET_RADIUS = 40
    const val ELLIPSIS = "…"

    private val fenceLine = Regex("^\\s*```")
    private val markdownChars = Regex("[*_`#]")
    private val whitespace = Regex("\\s+")

    /** Case- and diacritic-insensitive, non-overlapping match ranges of query.trim() in text, left to right. */
    fun matchRanges(text: String, query: String): List<IntRange> = matchRanges(text, query, Int.MAX_VALUE)

    /**
     * True when [text] contains [query] ignoring case and diacritics. Cheaper than [matchRanges] for filtering
     * because it stops at the first match and skips folding entirely when the text already matches as written.
     * For Markdown messages pass [plain] text so matches agree with [snippet].
     */
    fun hasMatch(text: String, query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty() || text.isEmpty()) return false
        if (text.contains(needle, ignoreCase = true)) return true
        return matchRanges(text, needle, 1).isNotEmpty()
    }

    private fun matchRanges(text: String, query: String, limit: Int): List<IntRange> {
        val needle = query.trim()
        if (needle.isEmpty() || text.isEmpty()) return emptyList()
        // ASCII needs no folding; reuse the original, exact indexOf loop and allocate no maps.
        if (isAscii(text) && isAscii(needle)) return asciiMatchRanges(text, needle, limit)

        val foldedText = fold(text)
        val foldedNeedle = fold(needle).text
        if (foldedNeedle.isEmpty() || foldedNeedle.length > foldedText.text.length) return emptyList()

        val ranges = mutableListOf<IntRange>()
        var from = 0
        var lastEnd = -1
        val span = foldedNeedle.length
        while (ranges.size < limit && from <= foldedText.text.length - span) {
            val at = foldedText.text.indexOf(foldedNeedle, startIndex = from)
            if (at < 0) break
            val start = foldedText.sourceStart[at]
            val end = extendOverMarks(text, foldedText.sourceEnd[at + span - 1]) - 1
            // A folding expansion (for example "ß" -> "ss") can offer the same original character twice.
            if (start > lastEnd) {
                ranges += start..end
                lastEnd = end
            }
            from = at + span
        }
        return ranges
    }

    private fun asciiMatchRanges(text: String, needle: String, limit: Int): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var from = 0
        while (ranges.size < limit && from <= text.length - needle.length) {
            val at = text.indexOf(needle, startIndex = from, ignoreCase = true)
            if (at < 0) break
            ranges += at..at + needle.length - 1
            from = at + maxOf(1, needle.length)
        }
        return ranges
    }

    private fun isAscii(value: String): Boolean = value.all { it.code < 0x80 && it != '\'' && it != '"' && it != '`' && it != '-' && it != ' ' && it != '\n' && it != '\r' }

    /**
     * Text prepared for searching and previews: drop lines whose trimmed start is ``` (fence markers only; the
     * code inside is kept as written). Outside code, drop table rule rows and horizontal rules, show table cells
     * as "a, b" and remove list bullets and quote marks at the start of a line. Then remove '*', '_', '`' and
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
                trimmed.removePrefix("|").removeSuffix("|").split('|').joinToString(", ") { it.trim().replace(markdownChars, "") }
            } else {
                line.replaceFirst(lineMarker, "").replace(markdownChars, "")
            }
        }
        return lines.joinToString("\n")

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
        val first = matchRanges(text, query, 1).firstOrNull() ?: return null
        var start = maxOf(0, first.first - SNIPPET_RADIUS)
        val earliest = maxOf(0, start - 24)
        while (start > earliest && text[start - 1] != ' ') start--
        if (start > 0 && Character.isLowSurrogate(text[start])) start--
        var end = minOf(text.length, first.last + 1 + SNIPPET_RADIUS)
        val latest = minOf(text.length, end + 24)
        while (end < latest && text[end] != ' ') end++
        if (end < text.length && Character.isLowSurrogate(text[end])) end++
        val body = text.substring(start, end).trim()
        val prefix = if (start > 0) ELLIPSIS else ""
        val suffix = if (end < text.length) ELLIPSIS else ""
        return prefix + body + suffix
    }

    /** Ranges of query in the snippet string, for highlighting (same rules as [matchRanges]). */
    fun snippetRanges(snippet: String, query: String): List<IntRange> = matchRanges(snippet, query)

    /** Folded text plus, for each folded character, the original range it came from. */
    private class FoldedText(val text: String, val sourceStart: IntArray, val sourceEnd: IntArray)

    /**
     * Lower-case, compatibility-normalise and drop non-spacing marks (the accents of "é", the harakat of Arabic),
     * keeping a per-character map back to [value] for highlighting. Spacing marks such as Indic vowel signs are
     * real letters, not accents, so they stay. ASCII takes a fast path because most chats and queries are ASCII.
     */
    private fun fold(value: String): FoldedText {
        if (isAscii(value)) {
            val lower = value.lowercase(Locale.ROOT)
            return FoldedText(lower, IntArray(lower.length) { it }, IntArray(lower.length) { it + 1 })
        }
        val text = StringBuilder(value.length)
        val starts = ArrayList<Int>(value.length)
        val ends = ArrayList<Int>(value.length)
        var i = 0
        while (i < value.length) {
            val codePoint = value.codePointAt(i)
            val width = Character.charCount(codePoint)
            if (codePoint < 0x80) {
                // Keep mixed ASCII/non-ASCII text cheap: lower-case ASCII without Normalizer or extra pieces.
                val lower = if (codePoint >= 'A'.code && codePoint <= 'Z'.code) codePoint + 0x20 else codePoint
                text.append(lower.toChar())
                starts += i
                ends += i + width
            } else if (!isNonSpacingMark(codePoint)) {
                val piece = if (width == 1 && Character.isSurrogate(value[i])) {
                    value[i].toString()
                } else {
                    Normalizer.normalize(String(Character.toChars(codePoint)), Normalizer.Form.NFKD)
                        .lowercase(Locale.ROOT)
                }
                for (ch in piece) {
                    if (isNonSpacingMark(ch.code)) continue
                    val extra = expansion(ch)
                    if (extra == null) {
                        text.append(ch)
                        starts += i
                        ends += i + width
                    } else {
                        for (expanded in extra) {
                            text.append(expanded)
                            starts += i
                            ends += i + width
                        }
                    }
                }
            }
            i += width
        }
        return FoldedText(text.toString(), starts.toIntArray(), ends.toIntArray())
    }

    private fun isNonSpacingMark(codePoint: Int): Boolean =
        Character.getType(codePoint) == Character.NON_SPACING_MARK.toInt()

    /**
     * Letters with no Unicode decomposition that common European text expects to fold (search "strasse" should
     * find "Straße"). Expansions map every produced character back to the one source character.
     */
    private fun expansion(ch: Char): String? = when (ch) {
        'ß' -> "ss"
        'æ' -> "ae"
        'œ' -> "oe"
        'ø' -> "o"
        'đ', 'ð' -> "d"
        'ł' -> "l"
        'þ' -> "th"
        'ı' -> "i"
        'ſ' -> "s"
        '‘', '’', '‚', '‛', '`', '´' -> "'"
        '“', '”', '„', '‟', '«', '»' -> "\""
        '–', '—', '−', '‐', '‑', '⁃' -> "-"
        '\u00A0', '\u2007', '\u202F', '\uFEFF' -> " "
        else -> null
    }

    /** Widen a folded match end so a decomposed accent (base letter + mark) is highlighted with its letter. */
    private fun extendOverMarks(text: String, end: Int): Int {
        var at = end
        while (at < text.length) {
            val codePoint = text.codePointAt(at)
            if (!isNonSpacingMark(codePoint)) break
            at += Character.charCount(codePoint)
        }
        return at
    }
}
