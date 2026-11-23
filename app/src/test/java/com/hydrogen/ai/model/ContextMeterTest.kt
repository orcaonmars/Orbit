package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextMeterTest {
    private val local = ProviderRoute.values().first { it != ProviderRoute.GOOGLE_AI_STUDIO_DIRECT }

    private fun msg(
        role: MessageRole,
        chars: Int,
        status: MessageStatus = MessageStatus.COMPLETED
    ): ChatMessage = ChatMessage(
        id = "m${counter}",
        correlationId = "c${counter++}",
        role = role,
        content = "x".repeat(chars),
        createdAtEpochMs = 0L,
        status = status
    )

    private var counter = 0

    @Test
    fun shortChatIsHidden() {
        val usage = ContextMeter.measure(listOf(msg(MessageRole.USER, 200), msg(MessageRole.ASSISTANT, 400)), "", local)
        assertEquals(ContextUsage.Level.HIDDEN, usage.level)
        assertEquals("", usage.label)
        assertEquals(600L, usage.usedChars)
    }

    @Test
    fun thresholdsAndLabel() {
        val budget = ContextWindowTrimmer.budgetFor(local)
        val notice = ContextMeter.measure(listOf(msg(MessageRole.USER, budget / 2)), "", local)
        assertEquals(ContextUsage.Level.NOTICE, notice.level)
        assertEquals("This chat is 50% full", notice.label)
        val near = ContextMeter.measure(listOf(msg(MessageRole.USER, (budget * 0.9).toInt())), "", local)
        assertEquals(ContextUsage.Level.NEAR, near.level)
        assertEquals("This chat is 90% full. Older messages may be left out", near.label)
        assertEquals(100, ContextUsage(usedChars = 30, budgetChars = 10, droppedMessages = 0).percent)
    }

    @Test
    fun trimmingMatchesSendPathPolicy() {
        val budget = ContextWindowTrimmer.budgetFor(local)
        val messages = (0 until 8).map { i ->
            msg(if (i % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT, budget / 5)
        }
        val usage = ContextMeter.measure(messages, "Be brief.", local)
        val plan = ContextWindowPolicy.plan(
            items = messages.map { ContextWindowPolicy.Item(it.role.name.lowercase(), it.content.length, 0) },
            budgetChars = budget,
            systemPromptChars = "Be brief.".length
        )
        assertTrue(plan.droppedCount > 0)
        assertEquals(plan.droppedCount, usage.droppedMessages)
        assertEquals(ContextUsage.Level.TRIMMED, usage.level)
        assertEquals("Long chat: the oldest ${plan.droppedCount} messages are no longer sent", usage.label)
        assertEquals(
            "Long chat: the oldest message is no longer sent",
            ContextUsage(usedChars = 1, budgetChars = 10, droppedMessages = 1).label
        )
    }

    @Test
    fun failedAndEmptyRepliesAreNotCounted() {
        val messages = listOf(
            msg(MessageRole.USER, 100),
            msg(MessageRole.ASSISTANT, 5_000, MessageStatus.FAILED),
            msg(MessageRole.ASSISTANT, 0),
            msg(MessageRole.ASSISTANT, 300, MessageStatus.STREAMING)
        )
        assertEquals(100L, ContextMeter.measure(messages, "", local).usedChars)
    }

    @Test
    fun draftTextImagesAndSystemPromptCount() {
        val usage = ContextMeter.measure(
            messages = listOf(msg(MessageRole.USER, 100)),
            systemPrompt = "x".repeat(50),
            route = local,
            draftChars = 20,
            draftImages = 2
        )
        assertEquals(50L + 100 + 20 + 2L * ContextWindowPolicy.IMAGE_COST_CHARS, usage.usedChars)
        assertEquals(0, ContextMeter.measure(emptyList(), "", local).droppedMessages)
    }

    @Test
    fun googleRouteHasLargeBudget() {
        val usage = ContextMeter.measure(
            listOf(msg(MessageRole.USER, 30_000)), "", ProviderRoute.GOOGLE_AI_STUDIO_DIRECT
        )
        assertEquals(ContextUsage.Level.HIDDEN, usage.level)
        assertEquals(0, usage.droppedMessages)
    }

    @Test
    fun compactFormatting() {
        assertEquals("950", ContextMeter.compact(950))
        assertEquals("6k", ContextMeter.compact(6_000))
        assertEquals("4.2k", ContextMeter.compact(4_200))
        assertEquals("10k", ContextMeter.compact(9_990))
        assertEquals("100k", ContextMeter.compact(100_000))
    }
}
