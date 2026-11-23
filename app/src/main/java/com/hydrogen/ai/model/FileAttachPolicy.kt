package com.hydrogen.ai.model

/**
 * Pure rules for attaching a picked file to a chat. Text-like files are placed in the composer as a
 * labelled code block (so the user sees exactly what is sent); a PDF with extractable text goes to
 * the composer as text, otherwise its pages are rendered to images.
 */
object FileAttachPolicy {
    enum class Kind { TEXT, PDF, WORD, IMAGE, UNSUPPORTED }

    const val MAX_TEXT_BYTES = 256 * 1024
    const val MAX_TEXT_CHARS = 60_000
    const val MAX_PDF_PAGES = 8
    /** PDFs are read up to this size for text extraction (content streams usually come first). */
    const val MAX_PDF_BYTES_FOR_TEXT = 16 * 1024 * 1024
    /** Word files bigger than this are mostly pictures; Orbit only reads their words. */
    const val MAX_WORD_BYTES = 24 * 1024 * 1024

    const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    private val TEXT_MIME_TYPES = setOf(
        "application/json", "application/xml", "application/javascript", "application/x-javascript",
        "application/x-sh", "application/x-yaml", "application/yaml", "application/toml",
        "application/x-python", "application/sql", "application/x-kotlin", "application/csv"
    )

    /** Extension -> code fence language ("" for plain text). */
    private val TEXT_EXTENSIONS = mapOf(
        "txt" to "", "text" to "", "log" to "", "md" to "markdown", "markdown" to "markdown",
        "csv" to "csv", "tsv" to "", "json" to "json", "xml" to "xml", "html" to "html", "htm" to "html",
        "css" to "css", "js" to "javascript", "mjs" to "javascript", "ts" to "typescript", "tsx" to "tsx",
        "jsx" to "jsx", "kt" to "kotlin", "kts" to "kotlin", "java" to "java", "py" to "python",
        "rb" to "ruby", "go" to "go", "rs" to "rust", "c" to "c", "h" to "c", "cpp" to "cpp", "cc" to "cpp",
        "hpp" to "cpp", "cs" to "csharp", "swift" to "swift", "php" to "php", "sh" to "bash", "bash" to "bash",
        "ps1" to "powershell", "bat" to "bat", "sql" to "sql", "yaml" to "yaml", "yml" to "yaml",
        "toml" to "toml", "ini" to "ini", "cfg" to "ini", "conf" to "", "gradle" to "groovy", "properties" to "",
        "srt" to "", "vtt" to "", "tex" to "latex", "r" to "r", "lua" to "lua", "dart" to "dart"
    )

    fun extensionOf(displayName: String): String =
        displayName.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..12 }.orEmpty()

    fun classify(mimeType: String?, displayName: String): Kind {
        val mime = mimeType?.lowercase()?.substringBefore(';')?.trim().orEmpty()
        val ext = extensionOf(displayName)
        return when {
            mime == "application/pdf" || ext == "pdf" -> Kind.PDF
            mime == DOCX_MIME || ext == "docx" -> Kind.WORD
            mime.startsWith("image/") -> Kind.IMAGE
            mime.startsWith("text/") || mime in TEXT_MIME_TYPES -> Kind.TEXT
            ext in TEXT_EXTENSIONS -> Kind.TEXT
            else -> Kind.UNSUPPORTED
        }
    }

    /**
     * Decodes file bytes as text, or returns null if they look binary (NUL bytes).
     * Handles UTF-8 and UTF-16 byte-order marks; malformed UTF-8 becomes U+FFFD, never an exception.
     */
    fun decodeText(bytes: ByteArray): String? {
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) return String(bytes, 2, evenLength(bytes.size - 2), Charsets.UTF_16LE)
            if (b0 == 0xFE && b1 == 0xFF) return String(bytes, 2, evenLength(bytes.size - 2), Charsets.UTF_16BE)
        }
        val sniff = minOf(bytes.size, 8192)
        for (i in 0 until sniff) if (bytes[i].toInt() == 0) return null
        val start = if (bytes.size >= 3 &&
            (bytes[0].toInt() and 0xFF) == 0xEF && (bytes[1].toInt() and 0xFF) == 0xBB &&
            (bytes[2].toInt() and 0xFF) == 0xBF
        ) 3 else 0
        return String(bytes, start, bytes.size - start, Charsets.UTF_8)
    }

    private fun evenLength(n: Int) = n - (n % 2)

    /** The composer text for an attached text file, fenced so its own backticks cannot break out. */
    fun formatForDraft(displayName: String, text: String, truncatedBySize: Boolean): String {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trimEnd()
        val clipped = normalized.length > MAX_TEXT_CHARS
        val body = if (clipped) normalized.take(MAX_TEXT_CHARS) else normalized
        val longestRun = Regex("`+").findAll(body).maxOfOrNull { it.value.length } ?: 0
        val fence = "`".repeat(maxOf(3, longestRun + 1))
        val language = TEXT_EXTENSIONS[extensionOf(displayName)].orEmpty()
        val note = if (clipped || truncatedBySize) "\n(File truncated: only the beginning is included.)" else ""
        return "File: ${displayName.replace('\n', ' ').take(120)}\n$fence$language\n$body\n$fence$note"
    }

    /** What to say when a picked file can't be attached. */
    fun unsupportedMessage(displayName: String): String {
        val name = displayName.replace('\n', ' ').take(120)
        return when (extensionOf(displayName)) {
            "doc" -> "'$name' is an older Word file. Save it as .docx in Word and attach it again."
            else -> "'$name' is not supported. Attach a text or code file, a PDF, a Word file or an image."
        }
    }

    /** What to say when a Word file has no words Orbit can read. */
    fun wordFailureMessage(displayName: String, reason: String?): String {
        val name = displayName.replace('\n', ' ').take(120)
        return when (reason) {
            DocxTextExtractor.REASON_ENCRYPTED -> "'$name' is password-protected or an older Word file. Save it as a normal .docx and try again."
            DocxTextExtractor.REASON_NO_TEXT -> "'$name' has no text Orbit can read. It may be only pictures."
            DocxTextExtractor.REASON_TOO_BIG -> "'$name' has too much text to attach. Copy the part you need instead."
            else -> "'$name' is not a readable Word file."
        }
    }

    fun pagesToRender(pageCount: Int): Int = pageCount.coerceIn(0, MAX_PDF_PAGES)

    /** Page bitmap size: fit the page into [maxSide] px on its longest side, keeping its aspect ratio. */
    fun renderSize(pageWidthPt: Int, pageHeightPt: Int, maxSide: Int = 1600): Pair<Int, Int> {
        if (pageWidthPt <= 0 || pageHeightPt <= 0) return maxSide to maxSide
        val scale = maxSide.toDouble() / maxOf(pageWidthPt, pageHeightPt)
        val w = (pageWidthPt * scale).toInt().coerceAtLeast(1)
        val h = (pageHeightPt * scale).toInt().coerceAtLeast(1)
        return w to h
    }
}
