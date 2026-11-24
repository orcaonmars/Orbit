package com.hydrogen.ai.model

import org.junit.Assert.*
import org.junit.Test

class ChatFindTest {
    private fun message(role: MessageRole, text: String) = ChatMessage("id", "corr", role, text, 1L)
    @Test fun multiTermCaseInsensitiveSearchUsesDisplayedMessagesOnly() {
        val messages = listOf(message(MessageRole.SYSTEM, "Orbit engine"),
            message(MessageRole.USER, "Orbit chat"), message(MessageRole.ASSISTANT, "Engine for ORBIT"))
        assertEquals(listOf(2), ChatFind.matches(messages, "orbit   engine"))
        assertEquals(listOf(1, 2), ChatFind.matches(messages, "orbit"))
        assertTrue(ChatFind.matches(messages, "  ").isEmpty())
    }
    @Test fun navigationWrapsBothWays() {
        assertEquals(2, ChatFind.step(0, -1, 3))
        assertEquals(0, ChatFind.step(2, 1, 3))
        assertEquals(0, ChatFind.step(1, 1, 0))
    }

    @Test fun previewsLocateMatchesDeepInLongReplies() {
        val content = "Intro ".repeat(100) + "Section 15 has a unique answer. " + "Outro ".repeat(100)
        val result = ChatFind.preview(message(MessageRole.ASSISTANT, content), "Section 15")
        assertTrue(result.contains("Section 15"))
        assertTrue(result.length <= 156)
        assertTrue(result.startsWith("..."))
        assertTrue(ChatFind.preview(message(MessageRole.USER, "abc"), "missing").isEmpty())
    }
}
