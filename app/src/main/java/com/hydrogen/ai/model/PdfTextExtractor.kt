package com.hydrogen.ai.model

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import kotlin.math.abs

/**
 * Best-effort text extraction from a PDF, in pure Kotlin (no Android, no dependencies), so
 * text-only models can read attached PDFs. It reads content streams (raw or FlateDecode), the
 * text operators inside BT...ET and fonts' ToUnicode CMaps (how Chrome, Word and most modern
 * tools store text). Text it cannot map to Unicode is reported as "unreadable-encoding" so the
 * caller can fall back to page images. Never throws.
 */
object PdfTextExtractor {
    data class Result(val text: String, val pagesWithText: Int, val truncated: Boolean, val reason: String?)

    const val REASON_NOT_PDF = "not-pdf"
    const val REASON_ENCRYPTED = "encrypted"
    const val REASON_NO_TEXT = "no-text"
    const val REASON_UNREADABLE = "unreadable-encoding"
    const val REASON_ERROR = "error"

    private const val MAX_INFLATED_BYTES = 8 * 1024 * 1024
    private val FILTER = Regex("/Filter\\s*(\\[[^\\]]*\\]|/[A-Za-z0-9]+)")
    private val NON_CONTENT = Regex(
        "/Subtype\\s*/(Image|Type1C|CIDFontType0C|OpenType|XML)|/Length[123]\\b|/Type\\s*/(XRef|ObjStm|EmbeddedFile|Metadata)"
    )
    private val OBJ_HEADER = Regex("(\\d+)\\s+\\d+\\s+obj\\b")
    private val FONT_MAP = Regex("/Font\\s*(<<([^<>]*)>>|(\\d+)\\s+\\d+\\s+R)")
    private val NAME_REF = Regex("/([^\\s/<>\\[\\]()]+)\\s*(\\d+)\\s+\\d+\\s+R")

    fun extract(bytes: ByteArray, maxChars: Int = 20_000): Result = try {
        extractUnsafe(bytes, maxChars.coerceAtLeast(1))
    } catch (t: Throwable) {
        Result("", 0, false, REASON_ERROR)
    }

    private fun extractUnsafe(bytes: ByteArray, maxChars: Int): Result {
        val doc = Document(bytes)
        val s = doc.s
        if (s.indexOf("%PDF").let { it < 0 || it > 1024 }) return Result("", 0, false, REASON_NOT_PDF)
        if (s.contains("/Encrypt")) return Result("", 0, false, REASON_ENCRYPTED)

        val out = TextOut(maxChars)
        var pages = 0
        for (stream in doc.streams) {
            if (out.full) break
            if (NON_CONTENT.containsMatchIn(stream.dict)) continue
            val decoded = doc.decode(stream) ?: continue
            val content = String(decoded, Charsets.ISO_8859_1)
            if (!content.contains("BT")) continue
            out.pageBreak()
            val mark = out.length
            ContentParser(content, out, doc).run()
            if (out.length > mark) pages++
        }

        val raw = out.finish()
        if (raw.isBlank()) return Result("", 0, false, REASON_NO_TEXT)
        val printable = raw.count { isPrintable(it) }
        if (printable < raw.length * 0.6) return Result("", pages, false, REASON_UNREADABLE)
        val text = raw.filter { isPrintable(it) }.replace(Regex("[ \\t]+\n"), "\n").trim()
        if (text.isBlank()) return Result("", 0, false, REASON_NO_TEXT)
        return Result(text, pages, out.full, null)
    }

    private fun isPrintable(c: Char): Boolean = when {
        c == '\n' || c == '\t' -> true
        c.code < 0x20 || c.code == 0x7F || c.code in 0x80..0x9F -> false
        c == '\uFFFD' -> false // malformed UTF-16 also decodes to U+FFFD, so surrogates seen here are paired
        else -> true
    }

    private fun inflate(data: ByteArray): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (!inflater.finished() && out.size() < MAX_INFLATED_BYTES) {
                val n = inflater.inflate(buffer)
                if (n == 0) break
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        } catch (e: Exception) {
            null
        } finally {
            inflater.end()
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            .let { if (it.length % 2 == 1) it + "0" else it }
        return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private class Stream(val dict: String, val start: Int, val end: Int)

    /** One font's code -> Unicode mapping; [cmap] is null when the font has no ToUnicode. */
    private class FontInfo(val cmap: Map<Int, String>?, val codeBytes: Int, val composite: Boolean)

    private class Document(val bytes: ByteArray) {
        // ISO-8859-1 maps each byte to one char, so string indices are byte offsets.
        val s = String(bytes, Charsets.ISO_8859_1)
        val streams: List<Stream> = findStreams()
        private val objectStarts = HashMap<Int, Int>()
        private val packedObjects = HashMap<Int, String>()
        private val fontRefs = HashMap<String, Int>()
        private val fontCache = HashMap<String, FontInfo?>()

        init {
            OBJ_HEADER.findAll(s).forEach { objectStarts.putIfAbsent(it.groupValues[1].toInt(), it.range.last + 1) }
            for (stream in streams) if (Regex("/Type\\s*/ObjStm").containsMatchIn(stream.dict)) unpackObjectStream(stream)
            collectFontRefs(s)
            packedObjects.values.forEach { collectFontRefs(it) }
        }

        private fun findStreams(): List<Stream> {
            val list = ArrayList<Stream>()
            var from = 0
            var previousEnd = 0
            while (true) {
                val k = s.indexOf("stream", from)
                if (k < 0) break
                from = k + 6
                if (k >= 3 && s.regionMatches(k - 3, "end", 0, 3)) continue
                var start = k + 6
                if (start < s.length && s[start] == '\r') start++
                if (start < s.length && s[start] == '\n') start++
                if (start == k + 6) continue // "stream" must be followed by an end of line
                val endIndex = s.indexOf("endstream", start)
                val end = if (endIndex < 0) s.length else endIndex
                // The stream's own dictionary: from its "N 0 obj" header, never past the previous stream.
                val dict = s.substring(maxOf(previousEnd, k - 4096, s.lastIndexOf("obj", k), 0), k)
                list += Stream(dict, start, end)
                previousEnd = end
                from = end
            }
            return list
        }

        fun decode(stream: Stream): ByteArray? {
            val filters = FILTER.find(stream.dict)?.groupValues?.get(1)
                ?.let { Regex("/([A-Za-z0-9]+)").findAll(it).map { m -> m.groupValues[1] }.toList() }
                .orEmpty()
            val raw = bytes.copyOfRange(stream.start, stream.end)
            return when {
                filters.isEmpty() -> raw
                filters == listOf("FlateDecode") || filters == listOf("Fl") -> inflate(raw)
                else -> null // images (DCT/JPX/CCITT) and filters we do not decode
            }
        }

        private fun unpackObjectStream(stream: Stream) {
            val content = decode(stream)?.let { String(it, Charsets.ISO_8859_1) } ?: return
            val first = Regex("/First\\s+(\\d+)").find(stream.dict)?.groupValues?.get(1)?.toIntOrNull() ?: return
            if (first > content.length) return
            val numbers = Regex("\\d+").findAll(content.substring(0, first)).map { it.value.toInt() }.toList()
            val pairs = numbers.chunked(2).filter { it.size == 2 }
            pairs.forEachIndexed { index, (number, offset) ->
                val begin = first + offset
                val finish = pairs.getOrNull(index + 1)?.let { first + it[1] } ?: content.length
                if (begin in 0..finish && finish <= content.length) packedObjects.putIfAbsent(number, content.substring(begin, finish))
            }
        }

        private fun collectFontRefs(text: String) {
            FONT_MAP.findAll(text).forEach { m ->
                val inline = m.groupValues[2]
                val body = if (m.groupValues[3].isNotEmpty()) objectText(m.groupValues[3].toInt()) ?: "" else inline
                NAME_REF.findAll(body).forEach { ref -> fontRefs.putIfAbsent(ref.groupValues[1], ref.groupValues[2].toInt()) }
            }
        }

        /** The dictionary text of object [number] (stream data excluded). */
        fun objectText(number: Int): String? {
            packedObjects[number]?.let { return it }
            val start = objectStarts[number] ?: return null
            val endObj = s.indexOf("endobj", start).let { if (it < 0) s.length else it }
            val streamAt = s.indexOf("stream", start).let { if (it < 0) s.length else it }
            return s.substring(start, minOf(endObj, streamAt, start + 8192, s.length))
        }

        private fun streamOf(number: Int): ByteArray? {
            val start = objectStarts[number] ?: return null
            val stream = streams.firstOrNull { it.start > start } ?: return null
            val endObj = s.indexOf("endobj", start)
            if (endObj in 0 until stream.start) return null
            return decode(stream)
        }

        fun font(name: String): FontInfo? = fontCache.getOrPut(name) {
            val dict = fontRefs[name]?.let { objectText(it) } ?: return@getOrPut null
            val composite = Regex("/Subtype\\s*/Type0").containsMatchIn(dict) || dict.contains("/Identity-H") || dict.contains("/Identity-V")
            val cmapText = Regex("/ToUnicode\\s+(\\d+)\\s+\\d+\\s+R").find(dict)?.groupValues?.get(1)?.toIntOrNull()
                ?.let { streamOf(it) }?.let { String(it, Charsets.ISO_8859_1) }
            val codeBytes = cmapText?.let { Regex("begincodespacerange\\s*<([0-9A-Fa-f]+)>").find(it) }
                ?.groupValues?.get(1)?.length?.div(2)?.coerceIn(1, 4) ?: if (composite) 2 else 1
            FontInfo(cmapText?.let { parseCMap(it) }?.takeIf { it.isNotEmpty() }, codeBytes, composite)
        }

        private fun parseCMap(text: String): Map<Int, String> {
            val map = HashMap<Int, String>()
            fun unicode(hex: String) = String(hexToBytes(hex), Charsets.UTF_16BE)
            Regex("beginbfchar(.*?)endbfchar", RegexOption.DOT_MATCHES_ALL).findAll(text).forEach { section ->
                Regex("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]*)>").findAll(section.groupValues[1]).forEach {
                    map[it.groupValues[1].toInt(16)] = unicode(it.groupValues[2])
                }
            }
            Regex("beginbfrange(.*?)endbfrange", RegexOption.DOT_MATCHES_ALL).findAll(text).forEach { section ->
                Regex("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>\\s*(<[0-9A-Fa-f]*>|\\[[^\\]]*\\])")
                    .findAll(section.groupValues[1]).forEach { m ->
                        val lo = m.groupValues[1].toInt(16)
                        val hi = minOf(m.groupValues[2].toInt(16), lo + 65535)
                        val target = m.groupValues[3]
                        if (target.startsWith("[")) {
                            Regex("<([0-9A-Fa-f]*)>").findAll(target).forEachIndexed { i, d ->
                                if (lo + i <= hi) map[lo + i] = unicode(d.groupValues[1])
                            }
                        } else {
                            val base = unicode(target.trim('<', '>'))
                            if (base.isNotEmpty()) for (code in lo..hi) {
                                map[code] = base.dropLast(1) + (base.last() + (code - lo))
                            }
                        }
                    }
            }
            return map
        }
    }

    /** Turns the raw bytes of a shown string into text, using the current font when it is known. */
    private fun decodeString(bytes: ByteArray, font: FontInfo?): String {
        if (font?.cmap != null) {
            val sb = StringBuilder()
            var i = 0
            while (i + font.codeBytes <= bytes.size) {
                var code = 0
                repeat(font.codeBytes) { code = (code shl 8) or (bytes[i + it].toInt() and 0xFF) }
                sb.append(font.cmap[code] ?: "\uFFFD")
                i += font.codeBytes
            }
            return sb.toString()
        }
        if (font?.composite == true) return "\uFFFD".repeat(maxOf(1, bytes.size / 2)) // glyph ids, no map
        if (bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == 0xFE && (bytes[1].toInt() and 0xFF) == 0xFF) {
            return String(bytes, 2, (bytes.size - 2) and 1.inv(), Charsets.UTF_16BE)
        }
        return String(bytes, Charsets.ISO_8859_1)
    }

    private class TextOut(private val maxChars: Int) {
        private val sb = StringBuilder()
        var full = false
            private set
        val length get() = sb.length

        fun text(t: String) {
            for (c in t) {
                if (sb.length >= maxChars) { full = true; return }
                sb.append(if (c == '\r') '\n' else c)
            }
        }

        fun space() {
            if (sb.isNotEmpty() && !sb.last().isWhitespace()) text(" ")
        }

        fun newline() {
            while (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1)
            if (sb.isNotEmpty() && sb.last() != '\n') text("\n")
        }

        fun pageBreak() {
            newline()
            if (sb.isNotEmpty() && !sb.endsWith("\n\n")) text("\n")
        }

        fun finish(): String = sb.toString()
            .lines().joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private class Str(val bytes: ByteArray)
    private class Name(val value: String)
    private class Op(val value: String)

    /** A small tokenizer for PDF content streams that only acts on text operators. */
    private class ContentParser(private val c: String, private val out: TextOut, private val doc: Document) {
        private var i = 0
        private var inText = false
        private var font: FontInfo? = null
        private var y = 0.0
        private var lastY: Double? = null
        private var forceNewline = false
        private var fontSize = 10.0
        private var lineX = 0.0 // start of the current text line (text space)
        private var curX = 0.0 // estimated pen position
        private var endX: Double? = null // estimated end of the previous text on this line
        private var moved = false // Td/Tm since the previous text

        /**
         * Emits text, starting a new line when the baseline moved. On the same line, a horizontal move
         * adds a space only when it leaves a real gap: Chrome and Word split words into kerned runs and
         * write their own space glyphs. Glyph widths are estimated at 0.6 em.
         */
        private fun emit(bytes: ByteArray) {
            val text = decodeString(bytes, font)
            if (text.isEmpty()) return
            val previous = lastY
            val sameLine = previous != null && !forceNewline && abs(y - previous) <= 0.01
            if (previous != null && !sameLine) {
                out.newline()
            } else if (sameLine && moved) {
                val gap = curX - (endX ?: curX)
                if (gap > fontSize * 0.25 || gap < -fontSize) out.space()
            }
            forceNewline = false
            moved = false
            lastY = y
            out.text(text)
            curX += text.length * fontSize * 0.6
            endX = curX
        }

        private fun moveTo(x: Double) {
            lineX = x
            curX = x
            moved = true
        }
        fun run() {
            val operands = ArrayList<Any>()
            while (!out.full) {
                val token = next() ?: break
                if (token !is Op) {
                    operands.add(token)
                    if (operands.size > 64) operands.removeAt(0)
                    continue
                }
                val last = operands.lastOrNull()
                when (token.value) {
                    "BT" -> { inText = true; y = 0.0; lineX = 0.0; curX = 0.0 }
                    "ET" -> inText = false
                    "Tf" -> {
                        (operands.getOrNull(operands.size - 2) as? Name)?.let { font = doc.font(it.value) }
                        (last as? Double)?.let { if (abs(it) > 0.01) fontSize = abs(it) }
                    }
                    "Tj" -> if (inText) (last as? Str)?.let { emit(it.bytes) }
                    "'", "\"" -> if (inText) {
                        forceNewline = true
                        moveTo(lineX)
                        (last as? Str)?.let { emit(it.bytes) }
                    }
                    "TJ" -> if (inText) (last as? List<*>)?.forEach { e ->
                        when (e) {
                            is Str -> emit(e.bytes)
                            is Double -> {
                                if (e < -200) out.space()
                                curX -= e / 1000.0 * fontSize
                            }
                        }
                    }
                    "Td", "TD" -> if (operands.size >= 2) {
                        val tx = operands[operands.size - 2] as? Double ?: 0.0
                        y += last as? Double ?: 0.0
                        moveTo(lineX + tx)
                    }
                    "Tm" -> if (operands.size >= 6) {
                        val a = abs(operands[operands.size - 6] as? Double ?: 1.0).let { if (it < 1e-6) 1.0 else it }
                        y = last as? Double ?: y
                        moveTo((operands[operands.size - 2] as? Double ?: 0.0) / a)
                    }
                    "T*" -> { forceNewline = true; moveTo(lineX) }
                    "ID" -> skipInlineImage()
                }
                operands.clear()
            }
        }

        private fun isWhite(ch: Char) = ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' || ch == '\u000C' || ch == '\u0000'
        private fun isDelimiter(ch: Char) = ch in "()<>[]{}/%"

        private fun skipWhiteAndComments() {
            while (i < c.length) {
                val ch = c[i]
                if (ch == '%') {
                    while (i < c.length && c[i] != '\n' && c[i] != '\r') i++
                } else if (isWhite(ch)) i++ else break
            }
        }

        private fun next(): Any? {
            skipWhiteAndComments()
            if (i >= c.length) return null
            val ch = c[i]
            return when {
                ch == '(' -> Str(readLiteral())
                ch == '<' && i + 1 < c.length && c[i + 1] == '<' -> { i += 2; Op("<<") }
                ch == '>' && i + 1 < c.length && c[i + 1] == '>' -> { i += 2; Op(">>") }
                ch == '<' -> Str(readHex())
                ch == '[' -> { i++; readArray() }
                ch == '/' -> {
                    i++
                    val s = i
                    while (i < c.length && !isWhite(c[i]) && !isDelimiter(c[i])) i++
                    Name(c.substring(s, i))
                }
                ch.isDigit() || ch == '-' || ch == '+' || ch == '.' -> {
                    val s = i
                    i++
                    while (i < c.length && (c[i].isDigit() || c[i] == '.')) i++
                    c.substring(s, i).toDoubleOrNull() ?: 0.0
                }
                isDelimiter(ch) -> { i++; Op(ch.toString()) }
                else -> {
                    val s = i
                    while (i < c.length && !isWhite(c[i]) && !isDelimiter(c[i])) i++
                    Op(c.substring(s, i))
                }
            }
        }

        private fun readArray(): List<Any> {
            val items = ArrayList<Any>()
            while (true) {
                skipWhiteAndComments()
                if (i >= c.length) break
                if (c[i] == ']') { i++; break }
                val t = next() ?: break
                if (t !is Op) items.add(t)
            }
            return items
        }

        private fun readLiteral(): ByteArray {
            i++ // (
            val bytes = ByteArrayOutputStream()
            var depth = 1
            while (i < c.length) {
                val ch = c[i++]
                when (ch) {
                    '(' -> { depth++; bytes.write(ch.code) }
                    ')' -> { depth--; if (depth == 0) break; bytes.write(ch.code) }
                    '\\' -> {
                        if (i >= c.length) break
                        val e = c[i++]
                        when (e) {
                            'n' -> bytes.write('\n'.code)
                            'r' -> bytes.write('\r'.code)
                            't' -> bytes.write('\t'.code)
                            'b' -> bytes.write(8)
                            'f' -> bytes.write(12)
                            '\r' -> if (i < c.length && c[i] == '\n') i++ // line continuation
                            '\n' -> Unit
                            in '0'..'7' -> {
                                var value = e - '0'
                                var digits = 1
                                while (digits < 3 && i < c.length && c[i] in '0'..'7') {
                                    value = value * 8 + (c[i] - '0'); i++; digits++
                                }
                                bytes.write(value and 0xFF)
                            }
                            else -> bytes.write(e.code and 0xFF) // \( \) \\ and unknown escapes
                        }
                    }
                    else -> bytes.write(ch.code and 0xFF)
                }
            }
            return bytes.toByteArray()
        }

        private fun readHex(): ByteArray {
            i++ // <
            val start = i
            while (i < c.length && c[i] != '>') i++
            val hex = c.substring(start, i)
            if (i < c.length) i++ // >
            return hexToBytes(hex)
        }

        /** Inline image data (BI ... ID <binary> EI) must not be read as text. */
        private fun skipInlineImage() {
            var j = i
            while (true) {
                val k = c.indexOf("EI", j)
                if (k < 0) { i = c.length; return }
                val before = k == 0 || isWhite(c[k - 1])
                val after = k + 2 >= c.length || isWhite(c[k + 2])
                if (before && after) { i = k + 2; return }
                j = k + 2
            }
        }
    }
}
