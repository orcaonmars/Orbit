package com.hydrogen.ai.data

import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.Conversation
import com.hydrogen.ai.model.MessageRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConversationStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun conversation(id: String, updated: Long): Conversation =
        Conversation(
            id = id,
            title = "Thread $id",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = updated,
            messages = listOf(
                ChatMessage(
                    id = "message-$id",
                    correlationId = "request-$id",
                    role = MessageRole.USER,
                    content = "Hello $id",
                    createdAtEpochMs = updated
                )
            )
        )

    @Test fun emptyStoreStartsWithNoConversations() = runTest {
        val store = FileConversationStore(temporaryFolder.newFolder())
        assertTrue(store.loadConversations().isEmpty())
    }

    @Test fun savedMessagesSurviveStoreRecreationAndRemainSorted() = runTest {
        val root = temporaryFolder.newFolder()
        val original = FileConversationStore(root)
        original.saveConversations(listOf(conversation("old", 10L), conversation("new", 50L)))
        val restored = FileConversationStore(root).loadConversations()
        assertEquals(listOf("new", "old"), restored.map { it.id })
        assertEquals("Hello new", restored.first().messages.single().content)
        assertTrue(java.io.File(root, "conversations.json").exists())
    }

    private fun keptCopies(root: java.io.File): List<java.io.File> =
        root.listFiles { f -> f.name.startsWith("conversations.unreadable-") }.orEmpty().toList()

    @Test fun fileFromOrbit01LoadsNewestFirstAndSurvivesASave() = runTest {
        val root = temporaryFolder.newFolder()
        val v01 = requireNotNull(javaClass.classLoader?.getResource("upgrade/v0.1-conversations.json")).readBytes()
        java.io.File(root, "conversations.json").writeBytes(v01)

        val store = FileConversationStore(root)
        val loaded = store.loadConversations()
        assertEquals(listOf("Explain photosynthesis simply", "Plan a weekend in Lisbon with a table"), loaded.map { it.title })
        assertNull(store.lastLoadProblem())
        assertTrue(keptCopies(root).isEmpty())

        store.saveConversations(loaded)
        assertEquals(loaded, FileConversationStore(root).loadConversations())
    }

    @Test fun damagedFileIsKeptByteForByteAndReportedWithoutThrowing() = runTest {
        val root = temporaryFolder.newFolder()
        val garbage = "{\"schemaVersion\":1,\"conversations\":[{\"id\":\"x\",\"ti".toByteArray()
        java.io.File(root, "conversations.json").writeBytes(garbage)

        val store = FileConversationStore(root, clock = { 1234L })
        assertTrue(store.loadConversations().isEmpty())
        assertEquals(ChatsLoadProblem(unreadable = true), store.lastLoadProblem())
        val kept = keptCopies(root).single()
        assertEquals("conversations.unreadable-1234.json", kept.name)
        assertTrue(garbage.contentEquals(kept.readBytes()))

        // Saving a clean list afterwards leaves the kept copy alone, and the next load has nothing to report.
        store.saveConversations(listOf(conversation("fresh", 5L)))
        assertEquals(listOf("fresh"), store.loadConversations().map { it.id })
        assertNull(store.lastLoadProblem())
        assertEquals(1, keptCopies(root).size)
    }

    @Test fun deleteKeptCopiesRemovesOnlyTheCopies() = runTest {
        val root = temporaryFolder.newFolder()
        java.io.File(root, "conversations.json").writeText("not json")
        var now = 1L
        val store = FileConversationStore(root, clock = { now++ })
        store.loadConversations()
        store.loadConversations()
        assertEquals(2, keptCopies(root).size)

        store.saveConversations(listOf(conversation("keep", 9L)))
        store.deleteKeptCopies()
        assertTrue(keptCopies(root).isEmpty())
        assertEquals(listOf("keep"), store.loadConversations().map { it.id })
    }

    @Test fun replacingConversationsDoesNotLeaveStaleTempFile() = runTest {
        val root = temporaryFolder.newFolder()
        val store = FileConversationStore(root)
        store.saveConversations(listOf(conversation("original", 10L)))
        store.saveConversations(listOf(conversation("replacement", 20L)))
        assertEquals(listOf("replacement"), store.loadConversations().map { it.id })
        assertTrue(!java.io.File(root, "conversations.json.tmp").exists())
    }
}
