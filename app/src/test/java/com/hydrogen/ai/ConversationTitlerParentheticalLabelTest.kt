package com.hydrogen.ai

import com.hydrogen.ai.model.ConversationTitler
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationTitlerParentheticalLabelTest {

    @Test
    fun skipsParentheticalAttachmentPlaceholderLine() {
        val input = """
            (attachment)
            How to implement binary search?
        """.trimIndent()

        val title = ConversationTitler.titleFor(input)
        assertEquals("How to implement binary search?", title)
    }

    @Test
    fun skipsParentheticalImagePlaceholderLine() {
        val input = """
            (image)
            Analyze this chart for trends
        """.trimIndent()

        val title = ConversationTitler.titleFor(input)
        assertEquals("Analyze this chart for trends", title)
    }
}
