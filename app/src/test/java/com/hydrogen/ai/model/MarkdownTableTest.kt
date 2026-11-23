package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTableTest {
    private fun text(cell: List<MdSpan>) = cell.joinToString("") { it.text }

    @Test
    fun parsesHeaderAlignmentAndRows() {
        val blocks = MarkdownParser.parse(
            """
            Intro
            | Name | Qty | Price |
            |:-----|:---:|------:|
            | **Apple** | 3 | $1 |
            | Pear | 10 | $2 |

            After
            """.trimIndent()
        )
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MdBlock.Paragraph)
        val table = blocks[1] as MdBlock.Table
        assertEquals(listOf("Name", "Qty", "Price"), table.header.map(::text))
        assertEquals(listOf(MdAlign.START, MdAlign.CENTER, MdAlign.END), table.align)
        assertEquals(2, table.rows.size)
        assertEquals("Apple", text(table.rows[0][0]))
        assertTrue(table.rows[0][0].single().bold)
        assertTrue(blocks[2] is MdBlock.Paragraph)
    }

    @Test
    fun ragged_rows_are_padded_and_trimmed_to_header_width() {
        val blocks = MarkdownParser.parse("a | b\n--- | ---\n| only |\n1 | 2 | 3\nno pipe ends the table")
        val table = blocks[0] as MdBlock.Table
        assertEquals(listOf("only", ""), table.rows[0].map(::text))
        assertEquals(listOf("1", "2"), table.rows[1].map(::text))
        assertTrue(blocks[1] is MdBlock.Paragraph)
    }

    @Test
    fun escaped_and_code_pipes_stay_inside_cells() {
        val table = MarkdownParser.parse("| op | meaning |\n|---|---|\n| `a|b` | x \\| y |").single() as MdBlock.Table
        assertEquals("a|b", text(table.rows[0][0]))
        assertTrue(table.rows[0][0].single().code)
        assertEquals("x | y", text(table.rows[0][1]))
    }

    @Test
    fun not_a_table_without_matching_delimiter_row() {
        // Streaming: header arrived but delimiter has not yet; and a mismatched column count.
        assertTrue(MarkdownParser.parse("| a | b |").single() is MdBlock.Paragraph)
        assertTrue(MarkdownParser.parse("| a | b |\n|---|").none { it is MdBlock.Table })
        assertTrue(MarkdownParser.parse("x | y\nplain text").none { it is MdBlock.Table })
    }

    @Test
    fun table_inside_code_fence_is_left_as_code() {
        val block = MarkdownParser.parse("```\n| a | b |\n|---|---|\n```").single()
        assertTrue(block is MdBlock.CodeBlock)
    }

    @Test
    fun row_and_column_counts_are_capped() {
        val wide = (1..MarkdownParser.MAX_TABLE_COLUMNS + 1).joinToString("|", "|", "|") { "h$it" }
        val wideDelimiter = (1..MarkdownParser.MAX_TABLE_COLUMNS + 1).joinToString("|", "|", "|") { "-" }
        assertTrue(MarkdownParser.parse("$wide\n$wideDelimiter").none { it is MdBlock.Table })
        val many = "| a |\n|---|\n" + (1..MarkdownParser.MAX_TABLE_ROWS + 5).joinToString("\n") { "| $it |" }
        val table = MarkdownParser.parse(many).first() as MdBlock.Table
        assertEquals(MarkdownParser.MAX_TABLE_ROWS, table.rows.size)
    }
}
