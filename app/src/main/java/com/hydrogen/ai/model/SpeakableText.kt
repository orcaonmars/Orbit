package com.hydrogen.ai.model

/**
 * Turns a Markdown reply into text that sounds natural when read aloud: no "asterisk asterisk", no URLs,
 * and code blocks replaced by a short spoken note. [chunk] then splits it at sentence boundaries into
 * pieces small enough for Android's TextToSpeech (whose per-utterance limit is about 4000 characters).
 */
object SpeakableText {
    const val MAX_CHUNK = 3900
    const val CODE_BLOCK_NOTE = "(code block shown on screen)"

    private val fencedCode = Regex("```[\\s\\S]*?(```|$)")
    private val image = Regex("!\\[([^\\]]*)]\\([^)]*\\)")
    private val link = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val bareUrl = Regex("https?://\\S+")
    private val inlineCode = Regex("`([^`]*)`")
    private val heading = Regex("(?m)^[ \\t]{0,3}#{1,6}\\s+")
    private val blockquote = Regex("(?m)^[ \\t]{0,3}>[ \\t]?")
    private val bullet = Regex("(?m)^[ \\t]*[-*+][ \\t]+")
    private val rule = Regex("(?m)^[ \\t]*([-*_])([ \\t]*\\1){2,}[ \\t]*$")
    private val tableDivider = Regex("(?m)^[ \\t]*\\|?[ \\t]*:?-{2,}:?[ \\t]*(\\|[ \\t]*:?-{2,}:?[ \\t]*)*\\|?[ \\t]*$")
    private val strongOrStrike = Regex("(\\*\\*|__|~~)(.+?)\\1")
    private val emphasis = Regex("(?<![\\w*])[*_](?!\\s)(.+?)(?<!\\s)[*_](?![\\w*])")
    private val extraBlankLines = Regex("\\n{3,}")
    private val spaces = Regex("[ \\t]{2,}")

    fun fromMarkdown(markdown: String): String {
        var text = markdown.replace("\r\n", "\n")
        text = fencedCode.replace(text) { " $CODE_BLOCK_NOTE " }
        text = image.replace(text) { it.groupValues[1] }
        text = link.replace(text) { it.groupValues[1] }
        text = bareUrl.replace(text, "link")
        text = inlineCode.replace(text) { it.groupValues[1] }
        text = tableDivider.replace(text, "")
        text = rule.replace(text, "")
        text = heading.replace(text, "")
        text = blockquote.replace(text, "")
        text = bullet.replace(text, "")
        text = strongOrStrike.replace(text) { it.groupValues[2] }
        text = emphasis.replace(text) { it.groupValues[1] }
        text = text.lines().joinToString("\n") { line ->
            val t = line.trim()
            if (t.startsWith("|") && t.endsWith("|") && t.length > 1) {
                t.trim('|').split('|').joinToString(", ") { it.trim() }
            } else {
                line
            }
        }
        text = spaces.replace(text, " ")
        text = extraBlankLines.replace(text, "\n\n")
        return text.trim()
    }

    /** Splits [text] into pieces of at most [max] characters, preferring sentence and paragraph ends. */
    fun chunk(text: String, max: Int = MAX_CHUNK): List<String> {
        require(max > 0)
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.length <= max) return listOf(trimmed)
        val sentences = trimmed.split(Regex("(?<=[.!?])\\s+|\\n+")).filter { it.isNotBlank() }
        val out = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotBlank()) out += current.toString().trim()
            current.clear()
        }
        for (sentence in sentences) {
            var piece = sentence.trim()
            while (piece.length > max) {
                flush()
                val cut = piece.lastIndexOf(' ', max).takeIf { it > max / 2 } ?: max
                out += piece.substring(0, cut).trim()
                piece = piece.substring(cut).trim()
            }
            if (current.isNotEmpty() && current.length + 1 + piece.length > max) flush()
            if (current.isNotEmpty()) current.append(' ')
            current.append(piece)
        }
        flush()
        return out
    }
}
