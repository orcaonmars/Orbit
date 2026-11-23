package com.hydrogen.ai.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * Text from a Word (.docx) file, in pure Kotlin with no dependencies, so any model can read an attached
 * Word document. A .docx is a ZIP; the body is word/document.xml. Paragraphs become lines, headings get
 * "#" marks, list items get "- " and tables become Markdown tables. Headers, footers, comments, footnotes
 * and deleted (tracked) text are left out. Never throws; size limits stop ZIP bombs.
 */
object DocxTextExtractor {
    data class Result(val text: String, val truncated: Boolean, val reason: String?)

    const val REASON_NOT_DOCX = "not-docx"
    const val REASON_ENCRYPTED = "encrypted"
    const val REASON_NO_TEXT = "no-text"
    const val REASON_TOO_BIG = "too-big"
    const val REASON_ERROR = "error"

    /** word/document.xml is read up to this many uncompressed bytes. */
    const val MAX_XML_BYTES = 12 * 1024 * 1024
    private const val MAX_ENTRIES = 5_000
    private const val BODY = "word/document.xml"

    fun extract(bytes: ByteArray, maxChars: Int = FileAttachPolicy.MAX_TEXT_CHARS): Result = try {
        extractUnsafe(bytes, maxChars.coerceAtLeast(1))
    } catch (t: Throwable) {
        Result("", false, REASON_ERROR)
    }

    private fun extractUnsafe(bytes: ByteArray, maxChars: Int): Result {
        if (isOleCompound(bytes)) return Result("", false, REASON_ENCRYPTED)
        if (bytes.size < 4 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) {
            return Result("", false, REASON_NOT_DOCX)
        }
        val xml = readEntry(bytes, BODY) ?: return Result("", false, REASON_NOT_DOCX)
        if (xml.size > MAX_XML_BYTES) return Result("", false, REASON_TOO_BIG)
        val text = documentXmlToText(String(xml, Charsets.UTF_8))
        if (text.isBlank()) return Result("", false, REASON_NO_TEXT)
        return if (text.length > maxChars) Result(text.take(maxChars).trimEnd(), true, null)
        else Result(text, false, null)
    }

    /** Password-protected Word files are an OLE compound file (D0 CF 11 E0), not a ZIP. So are old .doc files. */
    private fun isOleCompound(b: ByteArray): Boolean = b.size >= 8 &&
        (b[0].toInt() and 0xFF) == 0xD0 && (b[1].toInt() and 0xFF) == 0xCF &&
        (b[2].toInt() and 0xFF) == 0x11 && (b[3].toInt() and 0xFF) == 0xE0

    /** Returns the entry's bytes, at most [MAX_XML_BYTES] + 1 of them (so the caller can tell it was cut). */
    private fun readEntry(zip: ByteArray, name: String): ByteArray? {
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            var seen = 0
            while (true) {
                val entry = input.nextEntry ?: return null
                if (++seen > MAX_ENTRIES) return null
                if (entry.isDirectory || entry.name != name) continue
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (out.size() <= MAX_XML_BYTES) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                }
                return out.toByteArray()
            }
        }
    }

    // ---- WordprocessingML to text -------------------------------------------------------------------------

    private class Paragraph {
        val text = StringBuilder()
        var headingLevel = 0
        var listLevel = -1
    }

    private class Table {
        val rows = mutableListOf<MutableList<String>>()
        var row: MutableList<String>? = null
        var cell: StringBuilder? = null
    }

    /**
     * Walks the tags of word/document.xml. Only text inside w:t counts; w:tab and w:br add a tab or a line
     * break. Text in mc:Fallback (a second copy of a text box for old Word versions) is skipped so it isn't
     * read twice. Internal for tests.
     */
    internal fun documentXmlToText(xml: String): String {
        val blocks = mutableListOf<String>()
        val tables = ArrayDeque<Table>()
        // A stack, because a text box inside a paragraph has paragraphs of its own.
        val paragraphs = ArrayDeque<Paragraph>()
        var inText = false
        var inTabStops = false
        var fallbackDepth = 0
        var i = 0
        val n = xml.length

        fun appendText(s: String) {
            if (fallbackDepth > 0) return
            val p = paragraphs.lastOrNull()
            if (p != null) p.text.append(s)
            else tables.lastOrNull()?.cell?.append(s)
        }

        fun finishParagraph(p: Paragraph) {
            val body = p.text.toString().replace('\u00A0', ' ').trimEnd()
            val table = tables.lastOrNull()
            if (table?.cell != null) {
                val cell = table.cell!!
                if (body.isNotBlank()) {
                    if (cell.isNotEmpty()) cell.append(' ')
                    cell.append(body.trim())
                }
                return
            }
            if (body.isBlank()) return
            val line = when {
                p.headingLevel > 0 -> "#".repeat(p.headingLevel) + " " + body.trim()
                p.listLevel >= 0 -> "  ".repeat(p.listLevel.coerceAtMost(8)) + "- " + body.trim()
                else -> body
            }
            blocks += line
        }

        while (i < n) {
            val lt = xml.indexOf('<', i)
            if (lt < 0) break
            if (inText && lt > i) appendText(decodeEntities(xml.substring(i, lt)))
            if (xml.startsWith("<!--", lt)) {
                val end = xml.indexOf("-->", lt + 4)
                i = if (end < 0) n else end + 3
                continue
            }
            if (xml.startsWith("<![CDATA[", lt)) {
                val end = xml.indexOf("]]>", lt + 9)
                if (inText) appendText(xml.substring(lt + 9, if (end < 0) n else end))
                i = if (end < 0) n else end + 3
                continue
            }
            val gt = xml.indexOf('>', lt + 1)
            if (gt < 0) break
            val tag = xml.substring(lt + 1, gt)
            i = gt + 1
            if (tag.startsWith("?") || tag.startsWith("!")) continue
            val closing = tag.startsWith("/")
            val selfClosing = tag.endsWith("/")
            val name = tag.removePrefix("/").removeSuffix("/").trim().substringBefore(' ').substringBefore('\t')
                .substringBefore('\n').substringBefore('\r')

            val paragraph = paragraphs.lastOrNull()
            when (name) {
                "mc:Fallback" -> if (!selfClosing) fallbackDepth = (fallbackDepth + if (closing) -1 else 1).coerceAtLeast(0)
                "w:t" -> inText = !closing && !selfClosing
                // w:tabs holds tab stop settings (w:tab elements too), not tab characters.
                "w:tabs" -> if (!selfClosing) inTabStops = !closing
                "w:tab" -> if (!closing && !inTabStops && inRun(paragraph, tables)) appendText("\t")
                "w:br", "w:cr" -> if (!closing && inRun(paragraph, tables)) appendText("\n")
                "w:p" -> when {
                    selfClosing -> Unit
                    closing -> paragraphs.removeLastOrNull()?.let { if (fallbackDepth == 0) finishParagraph(it) }
                    else -> paragraphs.addLast(Paragraph())
                }
                "w:pStyle" -> if (!closing) {
                    val level = headingLevel(attribute(tag, "w:val"))
                    if (level > 0) paragraph?.headingLevel = level
                }
                "w:ilvl" -> if (!closing) {
                    paragraph?.listLevel = attribute(tag, "w:val")?.toIntOrNull()?.coerceIn(0, 8) ?: 0
                }
                "w:numPr" -> if (!closing && !selfClosing) paragraph?.let { if (it.listLevel < 0) it.listLevel = 0 }
                "w:tbl" -> when {
                    selfClosing -> Unit
                    closing -> tables.removeLastOrNull()?.let { t ->
                        val md = tableToMarkdown(t.rows)
                        if (md.isNotBlank()) {
                            val outer = tables.lastOrNull()?.cell
                            if (outer != null) {
                                // A table inside a table cell: keep its words, drop the grid.
                                val flat = t.rows.joinToString(" ") { it.joinToString(" ") }.trim()
                                if (flat.isNotEmpty()) { if (outer.isNotEmpty()) outer.append(' '); outer.append(flat) }
                            } else blocks += md
                        }
                    }
                    else -> tables.addLast(Table())
                }
                "w:tr" -> tables.lastOrNull()?.let { t ->
                    if (closing) { t.row?.let { if (it.isNotEmpty()) t.rows += it }; t.row = null }
                    else if (!selfClosing) t.row = mutableListOf()
                }
                "w:tc" -> tables.lastOrNull()?.let { t ->
                    if (closing) { t.cell?.let { t.row?.add(it.toString().trim()) }; t.cell = null }
                    else if (!selfClosing) t.cell = StringBuilder()
                }
            }
        }
        return blocks.joinToString("\n\n").trim()
    }

    private fun inRun(paragraph: Paragraph?, tables: ArrayDeque<Table>): Boolean =
        paragraph != null || tables.lastOrNull()?.cell != null

    /** "Heading1".."Heading6" (and "Title") as Word writes them in English templates. */
    internal fun headingLevel(styleId: String?): Int {
        val id = styleId?.lowercase() ?: return 0
        if (id == "title") return 1
        val m = Regex("^heading([1-6])$").find(id) ?: return 0
        return m.groupValues[1].toInt()
    }

    internal fun tableToMarkdown(rows: List<List<String>>): String {
        val clean = rows.filter { r -> r.any { it.isNotBlank() } }
        if (clean.isEmpty()) return ""
        val width = clean.maxOf { it.size }
        if (width == 1) return clean.joinToString("\n") { it.first() }
        fun cell(s: String) = s.replace("\\", "\\\\").replace("|", "\\|").replace('\n', ' ').replace('\t', ' ').trim()
        fun line(r: List<String>) = "| " + (0 until width).joinToString(" | ") { cell(r.getOrElse(it) { "" }) } + " |"
        val header = line(clean.first())
        val rule = "|" + (0 until width).joinToString("|") { " --- " } + "|"
        return (listOf(header, rule) + clean.drop(1).map(::line)).joinToString("\n")
    }

    private fun attribute(tag: String, name: String): String? {
        val m = Regex("""(?:^|\s)""" + Regex.escape(name) + """\s*=\s*(["'])(.*?)\1""").find(tag) ?: return null
        return decodeEntities(m.groupValues[2])
    }

    internal fun decodeEntities(s: String): String {
        if ('&' !in s) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '&') { out.append(c); i++; continue }
            val semi = s.indexOf(';', i + 1)
            if (semi < 0 || semi - i > 10) { out.append(c); i++; continue }
            val entity = s.substring(i + 1, semi)
            val decoded: String? = when {
                entity == "lt" -> "<"
                entity == "gt" -> ">"
                entity == "amp" -> "&"
                entity == "quot" -> "\""
                entity == "apos" -> "'"
                entity.startsWith("#x") || entity.startsWith("#X") -> entity.substring(2).toIntOrNull(16)?.let(::codePoint)
                entity.startsWith("#") -> entity.substring(1).toIntOrNull()?.let(::codePoint)
                else -> null
            }
            if (decoded == null) { out.append(c); i++ } else { out.append(decoded); i = semi + 1 }
        }
        return out.toString()
    }

    private fun codePoint(cp: Int): String? =
        if (cp in 1..0x10FFFF && cp !in 0xD800..0xDFFF) String(Character.toChars(cp)) else null
}
