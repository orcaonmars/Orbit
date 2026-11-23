package com.hydrogen.ai.model

/**
 * "Branch from here": a new chat that holds a copy of another chat up to one message, so you can take the
 * conversation in a different direction without losing the original. The copy never shares message ids with
 * the source and leaves out anything still in flight.
 */
object ConversationBranch {
    const val MAX_TITLE = 60

    private val IN_FLIGHT = setOf(MessageStatus.STAGED, MessageStatus.DISPATCHED, MessageStatus.STREAMING)
    private val NUMBERED = Regex("""^(.*) \(branch(?: (\d+))?\)$""")

    /** New chat holding [source]'s messages up to and including [messageId]; null when it is not in [source]. */
    fun branchAt(
        source: Conversation,
        messageId: String,
        newConversationId: String,
        nowMs: Long,
        newId: () -> String
    ): Conversation? {
        val index = source.messages.indexOfFirst { it.id == messageId }
        if (index < 0) return null
        // Only the versions on screen come along; the new chat starts with one version of every turn.
        val messages = MessageVersions.shownOnly(source.messages.take(index + 1))
            .filterNot { it.status in IN_FLIGHT }
            .map { it.copy(id = newId(), correlationId = newId(), hasSideEffectsInProgress = false) }
        return Conversation(
            id = newConversationId,
            title = branchTitle(source.title),
            createdAtEpochMs = nowMs,
            updatedAtEpochMs = nowMs,
            modelId = source.modelId,
            route = source.route,
            messages = messages,
            systemPrompt = source.systemPrompt,
            pinned = false,
            // A branch stays in the same project, and starts out in the list even if its source was archived.
            projectId = source.projectId
        )
    }

    /** "Trip ideas" -> "Trip ideas (branch)" -> "Trip ideas (branch 2)" -> "Trip ideas (branch 3)". */
    fun branchTitle(title: String): String {
        val trimmed = title.trim()
        val match = NUMBERED.matchEntire(trimmed)
        val base = (match?.groupValues?.get(1) ?: trimmed).trim().ifEmpty { "New chat" }
        val suffix = when {
            match == null -> " (branch)"
            else -> " (branch ${(match.groupValues[2].toIntOrNull() ?: 1) + 1})"
        }
        if (base.length + suffix.length <= MAX_TITLE) return base + suffix
        return base.take(MAX_TITLE - suffix.length - 1).trimEnd() + "…" + suffix
    }
}
