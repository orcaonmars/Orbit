package com.hydrogen.ai.data

import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.Conversation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * What a load of the saved chats file could not read. [unreadable] means the file as a whole was not a chats file
 * (not JSON, or no chat list in it); otherwise [skippedChats] chats and [skippedMessages] messages inside chats that
 * were kept had to be left out.
 */
data class ChatsLoadProblem(
    val unreadable: Boolean = false,
    val skippedChats: Int = 0,
    val skippedMessages: Int = 0
) {
    /** The one-off notice shown after starting. Plain words; the kept copy is why nothing is lost. */
    val notice: String
        get() = when {
            unreadable -> "Orbit couldn't read your saved chats, so it started fresh. A copy of the old file is kept on this phone."
            skippedChats > 0 && skippedMessages > 0 ->
                "${count(skippedChats, "saved chat")} and ${count(skippedMessages, "message")} couldn't be read and were left out. A copy is kept on this phone."
            skippedChats > 0 -> "${count(skippedChats, "saved chat")} couldn't be read and ${if (skippedChats == 1) "was" else "were"} left out. A copy is kept on this phone."
            else -> "${count(skippedMessages, "message")} couldn't be read and ${if (skippedMessages == 1) "was" else "were"} left out. A copy is kept on this phone."
        }

    private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
}

/**
 * Reads conversations.json as forgivingly as it can, so one damaged chat (a half-written file, a value a newer or
 * older Orbit wrote differently) never costs every other chat or stops the app from starting. A chat that can't be
 * read whole is retried message by message, keeping the messages that can be read; a chat that still can't be read
 * is left out. Pure and Android-free so it is unit-tested, including against a file written by Orbit 0.1.
 */
object ConversationFileCodec {

    data class Decoded(val conversations: List<Conversation>, val problem: ChatsLoadProblem?)

    fun decode(raw: String, json: Json): Decoded {
        if (raw.isBlank()) return Decoded(emptyList(), null)
        val root = guard { json.parseToJsonElement(raw) } as? JsonObject
        val items = root?.get("conversations") as? JsonArray
            ?: return Decoded(emptyList(), ChatsLoadProblem(unreadable = true))

        val kept = mutableListOf<Conversation>()
        var skippedChats = 0
        var skippedMessages = 0
        for (item in items) {
            val whole = guard { json.decodeFromJsonElement(Conversation.serializer(), item) }
            if (whole != null) {
                kept += whole
                continue
            }
            val repaired = repair(item, json)
            if (repaired == null) {
                skippedChats++
            } else {
                kept += repaired.first
                skippedMessages += repaired.second
            }
        }
        val problem = if (skippedChats > 0 || skippedMessages > 0) {
            ChatsLoadProblem(skippedChats = skippedChats, skippedMessages = skippedMessages)
        } else {
            null
        }
        return Decoded(kept, problem)
    }

    /** The chat with its unreadable messages left out, and how many were left out; null when even that fails. */
    private fun repair(item: JsonElement, json: Json): Pair<Conversation, Int>? {
        val chat = item as? JsonObject ?: return null
        val messages = chat["messages"] as? JsonArray ?: return null
        val readable = messages.filter { m -> guard { json.decodeFromJsonElement(ChatMessage.serializer(), m) } != null }
        val dropped = messages.size - readable.size
        if (dropped == 0) return null
        val fixed = guard {
            json.decodeFromJsonElement(Conversation.serializer(), JsonObject(chat + ("messages" to JsonArray(readable))))
        } ?: return null
        return fixed to dropped
    }

    /** Null for anything the decoder rejects; versions nest, so a crafted file could also overflow the stack. */
    private inline fun <T> guard(block: () -> T): T? =
        try {
            block()
        } catch (_: Exception) {
            null
        } catch (_: StackOverflowError) {
            null
        }
}
