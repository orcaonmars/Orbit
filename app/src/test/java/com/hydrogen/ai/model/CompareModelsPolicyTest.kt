package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareModelsPolicyTest {
    private fun msg(
        id: String,
        role: MessageRole,
        model: String = "gemma",
        status: MessageStatus = MessageStatus.COMPLETED,
        comparison: Boolean = false
    ) = ChatMessage(
        id = id, correlationId = id, role = role, content = id, createdAtEpochMs = 0,
        modelId = model, status = status, comparison = comparison
    )

    private val chat = listOf(
        msg("q1", MessageRole.USER),
        msg("a1", MessageRole.ASSISTANT),
        msg("a1b", MessageRole.ASSISTANT, model = "qwen", comparison = true),
        msg("q2", MessageRole.USER),
        msg("a2", MessageRole.ASSISTANT)
    )

    @Test
    fun latestCompletedAnswerCanBeCompared() {
        assertTrue(CompareModelsPolicy.canCompare(chat))
        assertFalse(CompareModelsPolicy.canCompare(chat.dropLast(1)))
        assertFalse(CompareModelsPolicy.canCompare(emptyList()))
        assertFalse(CompareModelsPolicy.canCompare(chat.dropLast(1) + msg("a2", MessageRole.ASSISTANT, status = MessageStatus.STREAMING)))
    }

    @Test
    fun toolCallsAndFullTurnsAreNotCompared() {
        val tool = msg("a2", MessageRole.ASSISTANT).copy(
            turnMetadata = GeminiTurnMetadata(toolCalls = listOf(ToolCallRecord(id = "t", name = "list_directory")))
        )
        assertFalse(CompareModelsPolicy.canCompare(chat.dropLast(1) + tool))
        val full = chat + (1 until CompareModelsPolicy.MAX_ANSWERS).map {
            msg("c$it", MessageRole.ASSISTANT, model = "m$it", comparison = true)
        }
        assertFalse(CompareModelsPolicy.canCompare(full))
        assertTrue(CompareModelsPolicy.canCompare(full.dropLast(1)))
    }

    @Test
    fun comparedAnswersAreNeverSentAsContext() {
        assertEquals(listOf("q1", "a1", "q2"), CompareModelsPolicy.promptHistory(chat).map { it.id })
        assertEquals(listOf("q1", "a1", "q2", "a2"), CompareModelsPolicy.context(chat).map { it.id })
    }

    @Test
    fun choicesSkipModelsThatAlreadyAnswered() {
        val answered = CompareModelsPolicy.answeredModels(chat + msg("a2b", MessageRole.ASSISTANT, model = "qwen", comparison = true))
        assertEquals(setOf("gemma", "qwen"), answered)
        assertEquals(
            listOf("llama"),
            CompareModelsPolicy.choices(listOf("gemma", " llama ", "", "qwen", "llama", "text-embedding-nomic-embed-text-v1.5"), answered)
        )
    }

    @Test
    fun modelLabelsMarkEveryAnswerOfAComparedQuestion() {
        assertTrue(CompareModelsPolicy.showsModelLabel(chat, 1))
        assertTrue(CompareModelsPolicy.showsModelLabel(chat, 2))
        assertFalse(CompareModelsPolicy.showsModelLabel(chat, 0))
        assertFalse(CompareModelsPolicy.showsModelLabel(chat, 4))
    }
}
