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

    @Test fun parsesStructuredTextBlocksWithoutDisplayingNonTextPayloads() {
        val parsed = RelayResponseParser.parseCompleteResponse(
            """{"choices":[{"message":{"content":[{"type":"text","text":"Hello "},{"type":"image_url","image_url":{"url":"https://example.test"}},{"type":"output_text","text":"world"}]}}]}"""
        )
        assertEquals("Hello world", parsed?.text)
    }

    @Test fun optionalWrongTypesDoNotDiscardValidContent() {
        val parsed = RelayResponseParser.parseSseEventData(
            """{"choices":[{"finish_reason":{},"delta":{"content":"Visible","reasoning_content":{"unexpected":1},"tool_calls":[{"function":{"name":{}}}]}}]}"""
        )
        assertEquals("Visible", parsed?.text)
        assertEquals("", parsed?.thinkingText)
    }

    @Test fun readsReasoningDetailsWithoutExposingEncryptedData() {
        val parsed = RelayResponseParser.parseCompleteResponse(
            """{"choices":[{"message":{"content":"Answer","reasoning_details":[{"type":"reasoning.text","text":"Thinking "},{"type":"reasoning.encrypted","data":"private"},{"type":"reasoning.summary","summary":"summary"}]}}]}"""
        )
        assertEquals("Answer", parsed?.text)
        assertEquals("Thinking summary", parsed?.thinkingText)
    }

    @Test fun reasoningTextTakesPrecedenceOverDuplicateDetails() {
        val parsed = RelayResponseParser.parseSseEventData(
            """{"choices":[{"delta":{"content":"A","reasoning":"B","reasoning_details":[{"type":"reasoning.text","text":"B"}]}}]}"""
        )
        assertEquals("B", parsed?.thinkingText)
    }

    @Test fun acceptsObjectToolArgumentsAndKeepsOrdinaryText() {
        val parsed = RelayResponseParser.parseCompleteResponse(
            """{"choices":[{"message":{"content":"Checking","tool_calls":[{"id":"call1","function":{"name":"lookup","arguments":{"q":"test"}}}]}}]}"""
        )
        assertEquals("Checking", parsed?.text)
        assertEquals("""{"q":"test"}""", parsed?.toolCalls?.single()?.argumentsJson)
    }

    @Test fun nativeGeminiUnexpectedOptionalFieldsKeepVisibleText() {
        val parsed = RelayResponseParser.parseCompleteResponse(
            """{"candidates":[{"finishReason":[],"content":{"parts":[{"text":"Answer","thoughtSignature":{},"thought":{}},{"text":7}]}}]}"""
        )
        assertEquals("Answer", parsed?.text)
    }

    @Test fun refusalTextIsVisibleInsteadOfEmptyProtocolFailure() {
        val parsed = RelayResponseParser.parseCompleteResponse(
            """{"choices":[{"message":{"content":null,"refusal":"Cannot comply"}}]}"""
        )
        assertEquals("Cannot comply", parsed?.text)
    }
}
