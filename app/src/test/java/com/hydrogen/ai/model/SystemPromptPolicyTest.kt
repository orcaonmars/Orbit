package com.hydrogen.ai.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SystemPromptPolicyTest {

    private fun userMessage(id: String = "user-1", content: String = "hello") = ChatMessage(
        id = id,
        correlationId = id,
        role = MessageRole.USER,
        content = content,
        createdAtEpochMs = 1_000L
    )

    private fun assistantMessage(id: String = "assistant-1", content: String = "hi") = ChatMessage(
        id = id,
        correlationId = id,
        role = MessageRole.ASSISTANT,
        content = content,
        createdAtEpochMs = 2_000L
    )

    @Test
    fun `blank prompt returns same list unchanged`() {
        val messages = listOf(userMessage(), assistantMessage())
        assertSame(messages, SystemPromptPolicy.applyTo(messages, "   ", 3_000L))
        assertSame(messages, SystemPromptPolicy.applyTo(messages, "", 3_000L))
    }

    @Test
    fun `non-blank prompt is prepended as SYSTEM message`() {
        val messages = listOf(userMessage(), assistantMessage())
        val result = SystemPromptPolicy.applyTo(messages, "Be brief", 3_000L)
        assertEquals(3, result.size)
        assertEquals(MessageRole.SYSTEM, result[0].role)
        assertEquals(SystemPromptPolicy.MESSAGE_ID, result[0].id)
        assertEquals("Be brief", result[0].content)
        assertEquals(3_000L, result[0].createdAtEpochMs)
        // Original messages preserved in order.
        assertEquals(messages[0], result[1])
        assertEquals(messages[1], result[2])
    }

    @Test
    fun `prompt is trimmed and capped at MAX_LENGTH`() {
        val padded = "  " + "x".repeat(SystemPromptPolicy.MAX_LENGTH + 5) + "  "
        val result = SystemPromptPolicy.applyTo(listOf(userMessage()), padded, 3_000L)
        assertEquals(SystemPromptPolicy.MAX_LENGTH, result[0].content.length)
    }

    @Test
    fun `stale SYSTEM message is replaced in place`() {
        val staleSystem = ChatMessage(
            id = SystemPromptPolicy.MESSAGE_ID,
            correlationId = SystemPromptPolicy.MESSAGE_ID,
            role = MessageRole.SYSTEM,
            content = "old prompt",
            createdAtEpochMs = 9_000L
        )
        val messages = listOf(staleSystem, userMessage(), assistantMessage())
        val result = SystemPromptPolicy.applyTo(messages, "new prompt", 3_000L)
        assertEquals(3, result.size)
        // Stale entry replaced at its original index; ordering of the rest is stable.
        assertEquals(MessageRole.SYSTEM, result[0].role)
        assertEquals("new prompt", result[0].content)
        assertEquals(messages[1], result[1])
        assertEquals(messages[2], result[2])
    }

    @Test
    fun `other SYSTEM messages are left untouched`() {
        val otherSystem = ChatMessage(
            id = "system-other",
            correlationId = "system-other",
            role = MessageRole.SYSTEM,
            content = "tool hint",
            createdAtEpochMs = 500L
        )
        val messages = listOf(otherSystem, userMessage())
        val result = SystemPromptPolicy.applyTo(messages, "chat prompt", 3_000L)
        assertEquals(3, result.size)
        // Chat system prompt prepended; the other SYSTEM message keeps its relative position.
        assertEquals(MessageRole.SYSTEM, result[0].role)
        assertEquals("chat prompt", result[0].content)
        assertEquals(otherSystem, result[1])
        assertEquals(messages[1], result[2])
    }

    @Test
    fun `applyTo is pure and does not mutate input`() {
        val messages = listOf(userMessage(), assistantMessage())
        val snapshot = messages.toList()
        SystemPromptPolicy.applyTo(messages, "prompt", 3_000L)
        assertEquals(snapshot, messages)
    }

    @Test
    fun `normalize trims and caps`() {
        assertEquals("abc", SystemPromptPolicy.normalize("  abc  "))
        assertEquals("", SystemPromptPolicy.normalize("   "))
        val long = "y".repeat(SystemPromptPolicy.MAX_LENGTH + 10)
        assertEquals(SystemPromptPolicy.MAX_LENGTH, SystemPromptPolicy.normalize(long).length)
    }

    @Test
    fun `old conversations without a system prompt still decode`() {
        val legacy = """{"id":"c1","title":"Old","createdAtEpochMs":1,"updatedAtEpochMs":2}"""
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString(Conversation.serializer(), legacy)
        assertEquals("", decoded.systemPrompt)
    }

    @Test
    fun `stale system prompt at a nonzero index keeps original position`() {
        val first = userMessage()
        val stale = ChatMessage(
            id = SystemPromptPolicy.MESSAGE_ID,
            correlationId = SystemPromptPolicy.MESSAGE_ID,
            role = MessageRole.SYSTEM,
            content = "stale",
            createdAtEpochMs = 10L
        )
        val result = SystemPromptPolicy.applyTo(listOf(first, stale, assistantMessage()), "updated", 30L)
        assertEquals(3, result.size)
        assertEquals(first, result[0])
        assertEquals(SystemPromptPolicy.MESSAGE_ID, result[1].id)
        assertEquals("updated", result[1].content)
        assertEquals(MessageRole.ASSISTANT, result[2].role)
    }
}
