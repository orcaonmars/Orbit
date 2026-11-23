package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ContextWindowTrimmerTest {

    private fun msg(id: String, role: MessageRole, chars: Int, images: Int = 0) = ChatMessage(
        id = id,
        correlationId = id,
        role = role,
        content = "x".repeat(chars),
        createdAtEpochMs = 1L,
        attachments = List(images) { n ->
            AttachmentManifestItem(
                id = "$id-img$n",
                displayName = "p.png",
                mimeType = "image/png",
                sizeBytes = 1L,
                localUri = "file:///p.png"
            )
        }
    )

    @Test
    fun shortChatIsReturnedUnchanged() {
        val history = listOf(msg("u1", MessageRole.USER, 10), msg("a1", MessageRole.ASSISTANT, 20))
        assertSame(history, ContextWindowTrimmer.trim(history, "", emptySet(), budgetChars = 1_000))
    }

    @Test
    fun longChatKeepsNewestTurnsAndSystemMessages() {
        val history = listOf(
            msg("s", MessageRole.SYSTEM, 50),
            msg("u1", MessageRole.USER, 400),
            msg("a1", MessageRole.ASSISTANT, 400),
            msg("u2", MessageRole.USER, 100),
            msg("a2", MessageRole.ASSISTANT, 100),
            msg("u3", MessageRole.USER, 100)
        )
        val trimmed = ContextWindowTrimmer.trim(history, systemPrompt = "x".repeat(50), emptySet(), budgetChars = 500)
        // Reserve 100 for system text; 100+100+100 fits, a1 (400) does not.
        assertEquals(listOf("s", "u2", "a2", "u3"), trimmed.map { it.id })
    }

    @Test
    fun imagesCountUnlessOmitted() {
        val history = listOf(
            msg("u1", MessageRole.USER, 10, images = 1),
            msg("a1", MessageRole.ASSISTANT, 10),
            msg("u2", MessageRole.USER, 10)
        )
        val budget = 1_000
        assertEquals(listOf("u2"), ContextWindowTrimmer.trim(history, "", emptySet(), budget).map { it.id })
        assertSame(history, ContextWindowTrimmer.trim(history, "", setOf("u1-img0"), budget))
    }

    @Test
    fun hostedGeminiGetsLargerBudget() {
        assertEquals(ContextWindowTrimmer.LARGE_CONTEXT_BUDGET_CHARS, ContextWindowTrimmer.budgetFor(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT))
        assertEquals(ContextWindowPolicy.DEFAULT_BUDGET_CHARS, ContextWindowTrimmer.budgetFor(ProviderRoute.GEMINI_HYDROGEN_RELAY))
    }
}
