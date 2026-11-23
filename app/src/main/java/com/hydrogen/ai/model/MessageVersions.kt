package com.hydrogen.ai.model

import kotlinx.serialization.Serializable

/** One version of a turn: the message at that spot and everything that followed it. */
@Serializable
data class MessageBranch(val messages: List<ChatMessage> = emptyList())

/**
 * Versions of a turn, behind the "< 2 / 3 >" control (docs/ORBIT_DESIGN.md, "Branches"). Editing a sent message or
 * regenerating the latest reply keeps what was there as an earlier version instead of throwing it away.
 *
 * A chat's message list is always the path being shown. The first message of a turn that has versions carries the
 * hidden ones in [ChatMessage.otherVersions] (each holds that message and everything after it) and its own place
 * among them in [ChatMessage.versionIndex], so the numbering never changes as you flip back and forth.
 */
object MessageVersions {

    /** How many versions the turn starting with [message] has; 1 when it was never edited or regenerated. */
    fun count(message: ChatMessage): Int = message.otherVersions.size + 1

    /** Which version [message] is, from 0, clamped so a damaged index can never point outside the list. */
    fun position(message: ChatMessage): Int = message.versionIndex.coerceIn(0, message.otherVersions.size)

    /**
     * [messages] up to [index], then [newFirst] as the newest version of that turn. What was shown from [index] on is
     * kept as an earlier version. The caller adds whatever follows [newFirst], such as the reply on its way.
     */
    fun addVersion(messages: List<ChatMessage>, index: Int, newFirst: ChatMessage): List<ChatMessage> {
        require(index in messages.indices) { "No message at $index" }
        val all = allVersions(messages, index)
        return messages.take(index) + newFirst.copy(otherVersions = all, versionIndex = all.size)
    }

    /** Shows version [target] (from 0) of the turn starting at [index]. Returns [messages] itself when nothing changes. */
    fun switchTo(messages: List<ChatMessage>, index: Int, target: Int): List<ChatMessage> {
        if (index !in messages.indices) return messages
        val first = messages[index]
        if (first.otherVersions.isEmpty() || target !in 0..first.otherVersions.size || target == position(first)) {
            return messages
        }
        val all = allVersions(messages, index)
        val chosen = all[target].messages
        if (chosen.isEmpty()) return messages
        val rest = all.filterIndexed { i, _ -> i != target }
        return messages.take(index) + chosen.first().copy(otherVersions = rest, versionIndex = target) + chosen.drop(1)
    }

    /** Every message, hidden versions included, for questions like "does any message still use this file". */
    fun everyMessage(messages: List<ChatMessage>): Sequence<ChatMessage> = sequence {
        for (message in messages) {
            yield(message)
            for (version in message.otherVersions) yieldAll(everyMessage(version.messages))
        }
    }

    /**
     * [transform] applied to every message, hidden versions included, keeping each turn's versions in place.
     * [transform] works on one message's own fields; its versions are rebuilt from the transformed copies.
     */
    fun mapEvery(messages: List<ChatMessage>, transform: (ChatMessage) -> ChatMessage): List<ChatMessage> =
        messages.map { message ->
            val mapped = transform(message)
            if (message.otherVersions.isEmpty()) {
                mapped
            } else {
                mapped.copy(otherVersions = message.otherVersions.map { MessageBranch(mapEvery(it.messages, transform)) })
            }
        }

    /** The path being shown with every hidden version left out, for copies that should hold only what you see. */
    fun shownOnly(messages: List<ChatMessage>): List<ChatMessage> = messages.map { it.withoutVersions() }

    /** All versions of the turn starting at [index], in order, with the shown one in its place. */
    private fun allVersions(messages: List<ChatMessage>, index: Int): List<MessageBranch> {
        val first = messages[index]
        val shown = MessageBranch(listOf(first.withoutVersions()) + messages.drop(index + 1))
        return first.otherVersions.toMutableList().apply { add(position(first), shown) }
    }

    private fun ChatMessage.withoutVersions(): ChatMessage =
        if (otherVersions.isEmpty() && versionIndex == 0) this else copy(otherVersions = emptyList(), versionIndex = 0)
}
