package com.hydrogen.ai.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayResponseParserTest {
    @Test fun parsesOpenAiChatCompletion() {
        val text = RelayResponseParser.extractAssistantText(
            """{"choices":[{"message":{"role":"assistant","content":"Hello from Gemini"}}]}"""
        )
        assertEquals("Hello from Gemini", text)
    }

    @Test fun parsesNativeGeminiCandidates() {
        val text = RelayResponseParser.extractAssistantText(
            """{"candidates":[{"content":{"parts":[{"text":"First"},{"text":"Second"}]}}]}"""
        )
        assertEquals("First\nSecond", text)
    }

    @Test fun emptyAndMissingContentAreNotReportedAsReplies() {
        assertNull(RelayResponseParser.extractAssistantText("""{"choices":[{"message":{"content":" "}}]}"""))
        assertNull(RelayResponseParser.extractAssistantText("""{"candidates":[]}"""))
    }

    @Test fun malformedResponseFailsSafelyWithoutFabrication() {
        assertNull(RelayResponseParser.extractAssistantText("{ not valid JSON"))
    }
}
