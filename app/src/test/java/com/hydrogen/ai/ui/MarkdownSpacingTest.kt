package com.hydrogen.ai.ui

import com.hydrogen.ai.model.MarkdownParser
import com.hydrogen.ai.model.MdBlock
import com.hydrogen.ai.model.MdSpan
import org.junit.Assert.assertEquals
import org.junit.Test

/** Block spacing in replies follows docs/ORBIT_DESIGN.md ("Markdown"). */
class MarkdownSpacingTest {
    private val paragraph = MdBlock.Paragraph(listOf(MdSpan("Text")))
    private val item = MdBlock.ListItem("•", 0, listOf(MdSpan("Item")))
    private val heading = MdBlock.Heading(2, listOf(MdSpan("Title")))

    @Test
    fun paragraphsAreTwelveApart() {
        assertEquals(12, markdownBlockGap(paragraph, paragraph))
        assertEquals(12, markdownBlockGap(paragraph, item))
        assertEquals(12, markdownBlockGap(item, paragraph))
    }

    @Test
    fun itemsOfOneListAreSixApart() {
        assertEquals(6, markdownBlockGap(item, item))
    }

    @Test
    fun headingsGetMoreRoomAboveThanBelow() {
        assertEquals(16, markdownBlockGap(paragraph, heading))
        assertEquals(16, markdownBlockGap(item, heading))
        assertEquals(12, markdownBlockGap(heading, paragraph))
    }

    @Test
    fun aRuleHasTwentyFourOnEachSide() {
        assertEquals(24, markdownBlockGap(paragraph, MdBlock.Rule))
        assertEquals(24, markdownBlockGap(MdBlock.Rule, heading))
    }

    @Test
    fun aParsedReplyUsesTheseGaps() {
        val blocks = MarkdownParser.parse("Intro\n\n- one\n- two\n\n## Next\n\nMore")
        val gaps = blocks.zipWithNext { a, b -> markdownBlockGap(a, b) }
        assertEquals(listOf(12, 6, 16, 12), gaps)
    }
}
