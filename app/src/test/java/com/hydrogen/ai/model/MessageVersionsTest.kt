package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageVersionsTest {

    private fun msg(id: String, role: MessageRole = MessageRole.USER, content: String = id) =
        ChatMessage(id = id, correlationId = "c-$id", role = role, content = content, createdAtEpochMs = 1)

    private val chat = listOf(
        msg("u1"),
        msg("a1", MessageRole.ASSISTANT),
        msg("u2"),
        msg("a2", MessageRole.ASSISTANT)
    )

    @Test
    fun aPlainMessageHasOneVersion() {
        assertEquals(1, MessageVersions.count(chat[0]))
        assertEquals(0, MessageVersions.position(chat[0]))
    }

    @Test
    fun addingAVersionKeepsTheOldTurnAndEverythingAfterIt() {
        val edited = MessageVersions.addVersion(chat, 2, msg("u2b", content = "edited"))

        assertEquals(listOf("u1", "a1", "u2b"), edited.map { it.id })
        val first = edited[2]
        assertEquals(2, MessageVersions.count(first))
        assertEquals(1, MessageVersions.position(first))
        assertEquals(listOf("u2", "a2"), first.otherVersions.single().messages.map { it.id })
    }

    @Test
    fun switchingBackAndForthRestoresEachVersionWithStableNumbers() {
        val edited = MessageVersions.addVersion(chat, 2, msg("u2b")) + msg("a2b", MessageRole.ASSISTANT)

        val back = MessageVersions.switchTo(edited, 2, 0)
        assertEquals(listOf("u1", "a1", "u2", "a2"), back.map { it.id })
        assertEquals(0, MessageVersions.position(back[2]))
        assertEquals(2, MessageVersions.count(back[2]))
        assertEquals(listOf("u2b", "a2b"), back[2].otherVersions.single().messages.map { it.id })

        val forward = MessageVersions.switchTo(back, 2, 1)
        assertEquals(listOf("u1", "a1", "u2b", "a2b"), forward.map { it.id })
        assertEquals(1, MessageVersions.position(forward[2]))
    }

    @Test
    fun aThirdVersionGoesLastAndTheMiddleOneStaysInTheMiddle() {
        var list = MessageVersions.addVersion(chat, 3, msg("a2b", MessageRole.ASSISTANT))
        list = MessageVersions.addVersion(list, 3, msg("a2c", MessageRole.ASSISTANT))
        assertEquals(3, MessageVersions.count(list[3]))
        assertEquals(2, MessageVersions.position(list[3]))

        val middle = MessageVersions.switchTo(list, 3, 1)
        assertEquals("a2b", middle[3].id)
        assertEquals(1, MessageVersions.position(middle[3]))
        assertEquals(listOf("a2", "a2c"), middle[3].otherVersions.map { it.messages.single().id })

        // A new version made while looking at the middle one still goes at the end.
        val fourth = MessageVersions.addVersion(middle, 3, msg("a2d", MessageRole.ASSISTANT))
        assertEquals(3, MessageVersions.position(fourth[3]))
        assertEquals(listOf("a2", "a2b", "a2c"), fourth[3].otherVersions.map { it.messages.single().id })
    }

    @Test
    fun switchingToTheShownOrAMissingVersionChangesNothing() {
        val edited = MessageVersions.addVersion(chat, 2, msg("u2b"))
        assertSame(edited, MessageVersions.switchTo(edited, 2, 1))
        assertSame(edited, MessageVersions.switchTo(edited, 2, 5))
        assertSame(edited, MessageVersions.switchTo(edited, 2, -1))
        assertSame(edited, MessageVersions.switchTo(edited, 9, 0))
        assertSame(chat, MessageVersions.switchTo(chat, 0, 0))
    }

    @Test
    fun aBadSavedPositionIsClamped() {
        val odd = msg("x").copy(otherVersions = listOf(MessageBranch(listOf(msg("y")))), versionIndex = 7)
        assertEquals(1, MessageVersions.position(odd))
        assertEquals(0, MessageVersions.position(odd.copy(versionIndex = -3)))
    }

    @Test
    fun nestedVersionsAreReachedByEveryMessageAndMapEvery() {
        // An edit inside an older version: u2 had its reply regenerated, then u2 itself was edited.
        var list = MessageVersions.addVersion(chat, 3, msg("a2b", MessageRole.ASSISTANT))
        list = MessageVersions.addVersion(list, 2, msg("u2b"))

        val ids = MessageVersions.everyMessage(list).map { it.id }.toSet()
        assertEquals(setOf("u1", "a1", "u2", "a2", "a2b", "u2b"), ids)

        val upper = MessageVersions.mapEvery(list) { it.copy(content = it.content.uppercase()) }
        assertTrue(MessageVersions.everyMessage(upper).all { it.content == it.content.uppercase() })
        assertEquals(6, MessageVersions.everyMessage(upper).count())
    }

    @Test
    fun shownOnlyDropsHiddenVersions() {
        val edited = MessageVersions.addVersion(chat, 2, msg("u2b"))
        val shown = MessageVersions.shownOnly(edited)
        assertEquals(listOf("u1", "a1", "u2b"), shown.map { it.id })
        assertTrue(shown.all { it.otherVersions.isEmpty() && it.versionIndex == 0 })
    }

    @Test
    fun branchingFromAChatCopiesOnlyTheShownVersions() {
        val edited = MessageVersions.addVersion(chat, 2, msg("u2b", content = "new question")) +
            msg("a2b", MessageRole.ASSISTANT, content = "new answer")
        val conversation = Conversation(id = "c", title = "t", createdAtEpochMs = 1, updatedAtEpochMs = 1, messages = edited)
        var n = 0
        val branched = ConversationBranch.branchAt(conversation, "a2b", "c2", 5) { "id${n++}" }
            ?: error("expected a branch")
        assertEquals(listOf("u1", "a1", "new question", "new answer"), branched.messages.map { it.content })
        assertTrue(branched.messages.all { it.otherVersions.isEmpty() && it.versionIndex == 0 })
    }
}
