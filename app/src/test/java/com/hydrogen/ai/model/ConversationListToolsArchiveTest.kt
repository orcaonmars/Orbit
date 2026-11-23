package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationListToolsArchiveTest {

    private fun conv(id: String, updated: Long = 1L, withMessage: Boolean = true) = Conversation(
        id = id,
        title = id,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = updated,
        messages = if (!withMessage) emptyList() else listOf(
            ChatMessage(id = "$id-m", correlationId = "c", role = MessageRole.USER, content = "hi", createdAtEpochMs = 1L)
        )
    )

    private val list = listOf(
        conv("plain"),
        conv("old-archive", updated = 2L).copy(archived = true),
        conv("new-archive", updated = 9L).copy(archived = true),
        conv("in-project").copy(projectId = "p1"),
        conv("archived-in-project").copy(projectId = "p1", archived = true),
        conv("deleted-project").copy(projectId = "gone"),
        conv("temp").copy(temporary = true),
        conv("blank", withMessage = false)
    )

    @Test
    fun mainListLeavesOutArchivedChatsAndChatsOfProjectsThatExist() {
        assertEquals(
            listOf("plain", "deleted-project"),
            ConversationListTools.forDrawer(list, setOf("p1")).map { it.id }
        )
    }

    @Test
    fun withoutProjectsEveryUnarchivedSavedChatIsListed() {
        assertEquals(
            listOf("plain", "in-project", "deleted-project"),
            ConversationListTools.forDrawer(list).map { it.id }
        )
    }

    @Test
    fun archiveListsNewestFirstWhateverTheProject() {
        assertEquals(
            listOf("new-archive", "old-archive", "archived-in-project"),
            ConversationListTools.archived(list).map { it.id }
        )
    }

    @Test
    fun projectListHasOnlyItsUnarchivedChats() {
        assertEquals(listOf("in-project"), ConversationListTools.inProject(list, "p1").map { it.id })
        assertEquals(emptyList<String>(), ConversationListTools.inProject(list, "other").map { it.id })
    }

    @Test
    fun searchReachesArchivedAndProjectChatsButNotTemporaryOrBlankOnes() {
        assertEquals(
            listOf("plain", "old-archive", "new-archive", "in-project", "archived-in-project", "deleted-project"),
            ConversationListTools.searchable(list).map { it.id }
        )
    }
}
