package com.hydrogen.ai.model

/**
 * Turns a parsed Markdown table into spreadsheet text: RFC 4180 CSV, or tab-separated values that paste straight
 * into Sheets/Excel cells. Cells are plain text (formatting and link targets dropped). A cell that a spreadsheet would
 * run as a formula (=, +, @, tab, or '-' that is not a plain number) gets a leading apostrophe, so pasting a model's
 * table can never execute anything.
 */
object TableCsv {
    private val PLAIN_NUMBER = Regex("""^-\d+(?:[.,]\d+)*$""")

    fun toCsv(table: MdBlock.Table): String =
        lines(table).joinToString("\n") { row -> row.joinToString(",") { csvField(it) } }

    fun toTsv(table: MdBlock.Table): String =
        lines(table).joinToString("\n") { row -> row.joinToString("\t") { tsvField(it) } }

    fun cellText(spans: List<MdSpan>): String = spans.joinToString("") { it.text }.trim()

    /** Prefixes an apostrophe when a spreadsheet would treat the text as a formula. */
    fun guardFormula(text: String): String {
        if (text.isEmpty()) return text
        val risky = when (text[0]) {
            '=', '+', '@', '\t', '\r' -> true
            '-' -> !PLAIN_NUMBER.matches(text)
            else -> false
        }
        return if (risky) "'$text" else text
    }

    private fun lines(table: MdBlock.Table): List<List<String>> =
        (listOf(table.header) + table.rows).map { row -> row.map { cellText(it) } }

    private fun csvField(text: String): String {
        val safe = guardFormula(text)
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }

    private fun tsvField(text: String): String =
        guardFormula(text.replace(Regex("[\\t\\r\\n]+"), " ").trim())
}
