package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationBranchTest {
    private fun msg(id: String, role: MessageRole, status: MessageStatus = MessageStatus.COMPLETED) = ChatMessage(
        id = id, correlationId = "c-$id", role = role, content = "text $id", createdAtEpochMs = 5,
        modelId = "gemma", status = status, hasSideEffectsInProgress = status != MessageStatus.COMPLETED
    )

    private val source = Conversation(
        id = "src", title = "Trip ideas", createdAtEpochMs = 1, updatedAtEpochMs = 2, modelId = "gemma",
        route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT, systemPrompt = "Be brief", pinned = true,
        messages = listOf(
            msg("q1", MessageRole.USER), msg("a1", MessageRole.ASSISTANT),
            msg("q2", MessageRole.USER), msg("a2", MessageRole.ASSISTANT)
        )
    )

    private fun ids(): () -> String { var n = 0; return { "n${n++}" } }

    @Test
    fun branchAtMiddleKeepsOnlyThePrefix() {
        val branch = ConversationBranch.branchAt(source, "a1", "b1", 99, ids())!!
        assertEquals(listOf("text q1", "text a1"), branch.messages.map { it.content })
        assertEquals("b1", branch.id)
        assertEquals(99L, branch.createdAtEpochMs)
        assertEquals(99L, branch.updatedAtEpochMs)
    }

    @Test
    fun branchAtLastKeepsEverythingAndUnknownIsNull() {
        assertEquals(4, ConversationBranch.branchAt(source, "a2", "b1", 99, ids())!!.messages.size)
        assertNull(ConversationBranch.branchAt(source, "missing", "b1", 99, ids()))
    }

    @Test
    fun inFlightMessagesAreDropped() {
        val busy = source.copy(messages = source.messages + msg("a3", MessageRole.ASSISTANT, MessageStatus.STREAMING))
        val branch = ConversationBranch.branchAt(busy, "a3", "b1", 99, ids())!!
        assertEquals(4, branch.messages.size)
        assertTrue(branch.messages.none { it.hasSideEffectsInProgress })
    }

    @Test
    fun idsAreAllNewAndTheSourceIsUnchanged() {
        val before = source.copy()
        val branch = ConversationBranch.branchAt(source, "a2", "b1", 99, ids())!!
        val all = branch.messages.flatMap { listOf(it.id, it.correlationId) }
        assertEquals(all.size, all.toSet().size)
        assertTrue(all.none { id -> source.messages.any { it.id == id || it.correlationId == id } })
        assertEquals(before, source)
    }

    @Test
    fun settingsAreKeptAndTheBranchIsNotPinned() {
        val branch = ConversationBranch.branchAt(source, "a1", "b1", 99, ids())!!
        assertEquals("Be brief", branch.systemPrompt)
        assertEquals("gemma", branch.modelId)
        assertEquals(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT, branch.route)
        assertFalse(branch.pinned)
        assertEquals("Trip ideas (branch)", branch.title)
    }

    @Test
    fun titlesCountUp() {
        assertEquals("Trip ideas (branch)", ConversationBranch.branchTitle("Trip ideas"))
        assertEquals("Trip ideas (branch 2)", ConversationBranch.branchTitle("Trip ideas (branch)"))
        assertEquals("Trip ideas (branch 3)", ConversationBranch.branchTitle("Trip ideas (branch 2)"))
        assertEquals("New chat (branch)", ConversationBranch.branchTitle("  "))
    }

    @Test
    fun longTitlesAreCappedButKeepTheSuffix() {
        val title = ConversationBranch.branchTitle("x".repeat(80))
        assertTrue(title.length <= ConversationBranch.MAX_TITLE)
        assertTrue(title.endsWith("… (branch)"))
    }
}
