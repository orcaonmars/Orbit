package com.hydrogen.ai.model

/** Pure helpers for the conversation drawer (search + rename), kept testable outside Compose. */
object ConversationListTools {

    const val MAX_TITLE_LENGTH = 80

    /** Returns a single-line, trimmed, length-capped title, or null when the input is blank. */
    fun normalizeTitle(raw: String): String? =
        raw.replace(Regex("\\s+"), " ").trim().take(MAX_TITLE_LENGTH).trim().ifBlank { null }

    /**
     * Chats the drawer lists. A temporary chat is never saved, so there is nothing to come back to; an untouched
     * new chat joins the list with its first message, as in other chat apps.
     */
    fun forDrawer(conversations: List<Conversation>, projectIds: Set<String> = emptySet()): List<Conversation> =
        searchable(conversations).filter { !it.archived && (it.projectId == null || it.projectId !in projectIds) }

    /** Every chat search can find: archived ones and project chats too, never a temporary or empty one. */
    fun searchable(conversations: List<Conversation>): List<Conversation> =
        conversations.filter { !it.temporary && it.messages.any { message -> message.role != MessageRole.SYSTEM } }

    /** Archived chats, newest first. */
    fun archived(conversations: List<Conversation>): List<Conversation> =
        searchable(conversations).filter { it.archived }.sortedByDescending { it.updatedAtEpochMs }

    /** A project's chats (not archived ones), newest first. */
    fun inProject(conversations: List<Conversation>, projectId: String): List<Conversation> =
        searchable(conversations).filter { !it.archived && it.projectId == projectId }
            .sortedByDescending { it.updatedAtEpochMs }

    /**
     * Case-insensitive match on title or any message text, either as written or with Markdown symbols removed
     * (so "hello world" finds "hello **world**"); a blank query keeps every conversation.
     */
    fun filter(conversations: List<Conversation>, query: String): List<Conversation> {
        val needle = query.trim()
        if (needle.isEmpty()) return conversations
        return conversations.filter { conversation ->
            conversation.title.contains(needle, ignoreCase = true) ||
                conversation.messages.any { it.role != MessageRole.SYSTEM && messageMatches(it, needle) }
        }
    }

    /** What the drawer shows for one search result. */
    data class SearchPreview(
        /**
         * One line around the match in the newest matching message that says more than the title; null when only
         * the title matched or every matching message just repeats it.
         */
        val snippet: String?,
        /** Where the query sits in [snippet], for highlighting. */
        val snippetHighlights: List<IntRange>,
        /** Where the query sits in the chat title, for highlighting. */
        val titleHighlights: List<IntRange>,
        /** How many of the chat's messages contain the query. */
        val matchingMessages: Int
    )

    /** Snippet and highlight ranges for a conversation that [filter] kept; null for a blank query or no match. */
    fun preview(conversation: Conversation, query: String): SearchPreview? {
        val needle = query.trim()
        if (needle.isEmpty()) return null
        val matching = conversation.messages.filter { it.role != MessageRole.SYSTEM && messageMatches(it, needle) }
        val snippet = matching.asReversed().firstNotNullOfOrNull { message ->
            ChatSearch.snippet(message.content, needle)?.takeUnless { repeatsTitle(it, conversation.title) }
        }
        val titleHighlights = ChatSearch.matchRanges(conversation.title, needle)
        if (snippet == null && titleHighlights.isEmpty()) return null
        return SearchPreview(
            snippet = snippet,
            snippetHighlights = snippet?.let { ChatSearch.snippetRanges(it, needle) }.orEmpty(),
            titleHighlights = titleHighlights,
            matchingMessages = matching.size
        )
    }

    /** The title is usually made from the first message, so a snippet the title already shows in full adds nothing. */
    private fun repeatsTitle(snippet: String, title: String): Boolean =
        title.contains(snippet.removePrefix(ChatSearch.ELLIPSIS).removeSuffix(ChatSearch.ELLIPSIS).trim(), ignoreCase = true)

    private fun messageMatches(message: ChatMessage, needle: String): Boolean =
        message.content.contains(needle, ignoreCase = true) ||
            ChatSearch.plain(message.content).contains(needle, ignoreCase = true)
}
