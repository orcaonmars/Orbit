package com.hydrogen.ai

import com.hydrogen.ai.model.CodeHighlighter
import com.hydrogen.ai.model.CodeTokenKind
import org.junit.Assert.assertEquals
import org.junit.Test

class CodeHighlighterDollarIdentTest {
    @Test
    fun testDollarIdentifierInJsAndJava() {
        val code = "\$variable"
        val tokens = CodeHighlighter.tokenize(code, "js")
        assertEquals(1, tokens.size)
        assertEquals(CodeTokenKind.PLAIN, tokens[0].kind)
        assertEquals(0, tokens[0].start)
        assertEquals(code.length, tokens[0].end)
    }
}
