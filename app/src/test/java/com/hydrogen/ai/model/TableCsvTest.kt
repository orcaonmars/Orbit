package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TableCsvTest {

    private fun table(markdown: String): MdBlock.Table =
        MarkdownParser.parse(markdown).filterIsInstance<MdBlock.Table>().single()

    @Test
    fun simple_table_becomes_csv_and_tsv() {
        val t = table("| City | Pop |\n|---|--:|\n| Paris | 2.1 |\n| Rome | 2.8 |")
        assertEquals("City,Pop\nParis,2.1\nRome,2.8", TableCsv.toCsv(t))
        assertEquals("City\tPop\nParis\t2.1\nRome\t2.8", TableCsv.toTsv(t))
    }

    @Test
    fun formatting_is_dropped_from_cells() {
        val t = table("| Name | Note |\n|---|---|\n| **Bold** | `code` and [link](https://example.com) |")
        assertEquals("Name,Note\nBold,code and link", TableCsv.toCsv(t))
    }

    @Test
    fun csv_quotes_commas_and_doubles_quotes() {
        val t = table("| A | B |\n|---|---|\n| 1,000 | say \"hi\" |")
        assertEquals("A,B\n\"1,000\",\"say \"\"hi\"\"\"", TableCsv.toCsv(t))
    }

    @Test
    fun escaped_pipe_stays_in_one_cell() {
        val t = table("| Expr | Meaning |\n|---|---|\n| a \\| b | or |")
        assertEquals("Expr,Meaning\na | b,or", TableCsv.toCsv(t))
    }

    @Test
    fun formulas_are_neutralised_but_negative_numbers_kept() {
        assertEquals("'=SUM(A1:A9)", TableCsv.guardFormula("=SUM(A1:A9)"))
        assertEquals("'+cmd", TableCsv.guardFormula("+cmd"))
        assertEquals("'@foo", TableCsv.guardFormula("@foo"))
        assertEquals("'-2+3", TableCsv.guardFormula("-2+3"))
        assertEquals("-5", TableCsv.guardFormula("-5"))
        assertEquals("-3.25", TableCsv.guardFormula("-3.25"))
        assertEquals("-1,000", TableCsv.guardFormula("-1,000"))
        assertEquals("plain", TableCsv.guardFormula("plain"))
        assertEquals("", TableCsv.guardFormula(""))
    }

    @Test
    fun guard_applies_in_both_formats() {
        val t = table("| X |\n|---|\n| =HYPERLINK(\"http://evil\") |")
        assertEquals("X\n\"'=HYPERLINK(\"\"http://evil\"\")\"", TableCsv.toCsv(t))
        assertEquals("X\n'=HYPERLINK(\"http://evil\")", TableCsv.toTsv(t))
    }

    @Test
    fun short_rows_give_empty_cells() {
        val t = table("| A | B | C |\n|---|---|---|\n| 1 |")
        assertEquals("A,B,C\n1,,", TableCsv.toCsv(t))
        assertEquals("A\tB\tC\n1\t\t", TableCsv.toTsv(t))
    }

    @Test
    fun tsv_flattens_tabs_inside_cells() {
        val t = MdBlock.Table(
            header = listOf(listOf(MdSpan("H"))),
            align = listOf(MdAlign.START),
            rows = listOf(listOf(listOf(MdSpan("a\tb\nc"))))
        )
        assertEquals("H\na b c", TableCsv.toTsv(t))
        assertEquals("H\n\"a\tb\nc\"", TableCsv.toCsv(t))
    }

    @Test
    fun cell_text_is_trimmed() {
        assertEquals("hi there", TableCsv.cellText(listOf(MdSpan(" hi "), MdSpan("there "))))
    }
}
