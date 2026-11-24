package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationListToolsTest {

    private fun conv(id: String, title: String, vararg texts: String) = Conversation(
        id = id,
        title = title,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        messages = texts.mapIndexed { i, text ->
            ChatMessage(
                id = "$id-$i",
                correlationId = "c",
                role = MessageRole.USER,
                content = text,
                createdAtEpochMs = 1L
            )
        }
    )

    @Test
    fun drawerLeavesOutEmptyNewChatsAndTemporaryChats() {
        val list = listOf(
            conv("saved", "Trip to Kyoto", "where to eat"),
            conv("empty", "New chat"),
            conv("temp", "Secret", "just between us").copy(temporary = true)
        )

        assertEquals(listOf("saved"), ConversationListTools.forDrawer(list).map { it.id })
    }

    @Test
    fun filterMatchesTitleOrMessageTextCaseInsensitively() {
        val list = listOf(
            conv("a", "Trip to Kyoto", "where to eat"),
            conv("b", "Kotlin help", "How do I use Flow?"),
            conv("c", "New Chat")
        )

        assertEquals(list, ConversationListTools.filter(list, "   "))
        assertEquals(listOf("a"), ConversationListTools.filter(list, "kyoto").map { it.id })
        assertEquals(listOf("b"), ConversationListTools.filter(list, " FLOW ").map { it.id })
        assertEquals(emptyList<String>(), ConversationListTools.filter(list, "zzz").map { it.id })
    }

    @Test
    fun hiddenSystemMessagesAreNotSearchResults() {
        val source = conv("hidden", "Chat", "private instruction", "visible reply")
        val c = source.copy(messages = source.messages.mapIndexed { i, m ->
            if (i == 0) m.copy(role = MessageRole.SYSTEM) else m
        })
        assertEquals(emptyList<Conversation>(), ConversationListTools.filter(listOf(c), "private instruction"))
        assertNull(ConversationListTools.preview(c, "private instruction"))
        assertEquals(listOf(c), ConversationListTools.filter(listOf(c), "visible reply"))
        val systemOnly = c.copy(messages = c.messages.take(1))
        assertEquals(emptyList<Conversation>(), ConversationListTools.searchable(listOf(systemOnly)))
    }

    @Test
    fun filterFindsTextAcrossMarkdownSymbols() {
        val list = listOf(conv("a", "Chat", "say hello **world** now"), conv("b", "Other", "hello there"))
        assertEquals(listOf("a"), ConversationListTools.filter(list, "hello world").map { it.id })
    }

    @Test
    fun previewUsesNewestMatchingMessageAndHighlightsBoth() {
        val c = conv("a", "Orbit notes", "first orbit mention", "nothing here", "latest Orbit mention")
        val p = ConversationListTools.preview(c, "orbit")!!
        assertEquals("latest Orbit mention", p.snippet)
        assertEquals(listOf(7..11), p.snippetHighlights)
        assertEquals(listOf(0..4), p.titleHighlights)
        assertEquals(2, p.matchingMessages)
    }

    @Test
    fun previewForTitleOnlyMatchHasNoSnippet() {
        val p = ConversationListTools.preview(conv("a", "Trip to Kyoto", "where to eat"), "kyoto")!!
        assertNull(p.snippet)
        assertEquals(emptyList<IntRange>(), p.snippetHighlights)
        assertEquals(listOf(8..12), p.titleHighlights)
        assertEquals(0, p.matchingMessages)
    }

    @Test
    fun previewSkipsASnippetThatOnlyRepeatsTheTitle() {
        val text = "Hello there, this is a big text test"
        val p = ConversationListTools.preview(conv("a", text, text), "big")!!
        assertNull(p.snippet)
        assertEquals(listOf(23..25), p.titleHighlights)
        assertEquals(1, p.matchingMessages)
    }

    @Test
    fun previewFallsBackToAnOlderMessageWhenTheNewestOnlyRepeatsTheTitle() {
        val c = conv("a", "Plan a trip", "plan a trip to kyoto in spring", "Plan a trip")
        val p = ConversationListTools.preview(c, "trip")!!
        assertEquals("plan a trip to kyoto in spring", p.snippet)
        assertEquals(2, p.matchingMessages)
    }

    @Test
    fun previewIsNullForBlankQueryOrNoMatch() {
        val c = conv("a", "Chat", "text")
        assertNull(ConversationListTools.preview(c, "  "))
        assertNull(ConversationListTools.preview(c, "zzz"))
    }

    @Test
    fun normalizeTitleCollapsesWhitespaceCapsLengthAndRejectsBlank() {
        assertEquals("Weekly plan", ConversationListTools.normalizeTitle("  Weekly\n  plan \t"))
        assertNull(ConversationListTools.normalizeTitle(" \n\t "))
        val long = ConversationListTools.normalizeTitle("x".repeat(200))
        assertEquals(ConversationListTools.MAX_TITLE_LENGTH, long?.length)
    }
}
