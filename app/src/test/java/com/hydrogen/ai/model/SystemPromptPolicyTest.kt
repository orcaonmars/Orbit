package com.hydrogen.ai.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SystemPromptPolicyTest {

    private fun user(id: String, text: String) =
        ChatMessage(id = id, correlationId = id, role = MessageRole.USER, content = text, createdAtEpochMs = 5L)

    @Test
    fun blankPromptLeavesMessagesUntouched() {
        val messages = listOf(user("u1", "hi"))
        assertSame(messages, SystemPromptPolicy.applyTo(messages, "   \n", 1L))
    }

    @Test
    fun promptIsPrependedOnceAsTrimmedSystemMessage() {
        val messages = listOf(user("u1", "hi"), user("u2", "again"))
        val once = SystemPromptPolicy.applyTo(messages, "  Be brief.  ", 1L)
        assertEquals(3, once.size)
        assertEquals(MessageRole.SYSTEM, once.first().role)
        assertEquals("Be brief.", once.first().content)
        assertEquals(listOf("u1", "u2"), once.drop(1).map { it.id })
        // Applying again (e.g. a retry built from an already-prefixed list) never duplicates it.
        val twice = SystemPromptPolicy.applyTo(once, "Be brief.", 1L)
        assertEquals(once, twice)
    }

    @Test
    fun normalizeCapsLengthAndOldChatsDecodeWithoutPrompt() {
        val long = "x".repeat(SystemPromptPolicy.MAX_LENGTH + 50)
        assertEquals(SystemPromptPolicy.MAX_LENGTH, SystemPromptPolicy.normalize(long).length)
        val legacy = """{"id":"c1","title":"Old","createdAtEpochMs":1,"updatedAtEpochMs":2}"""
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString(Conversation.serializer(), legacy)
        assertEquals("", decoded.systemPrompt)
    }
}
