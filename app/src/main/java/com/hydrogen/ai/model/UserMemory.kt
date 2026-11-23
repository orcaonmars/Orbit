package com.hydrogen.ai.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MemoryItem(
    val id: String,
    val text: String,
    val createdAt: Long
)

/**
 * "Memory you control": a short list of facts the user chose to share with every chat.
 * Stored only on the device; it travels only inside the system prompt of the provider the user picked.
 */
object UserMemory {
    const val MAX_ITEMS = 50
    const val MAX_ITEM_CHARS = 300
    const val MAX_BLOCK_CHARS = 3000
    const val HEADER = "Things the user asked you to remember:"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prefixes = listOf("remember that ", "remember: ", "please remember ")

    /** Trims, collapses whitespace (including newlines) and clips to [MAX_ITEM_CHARS]. */
    fun clean(text: String): String =
        text.replace(Regex("\\s+"), " ").trim().take(MAX_ITEM_CHARS).trim()

    /** New items go first; blank text or a case-insensitive duplicate leaves the list unchanged. */
    fun add(items: List<MemoryItem>, text: String, id: String, now: Long): List<MemoryItem> {
        val cleaned = clean(text)
        if (cleaned.isEmpty()) return items
        if (items.any { it.text.equals(cleaned, ignoreCase = true) }) return items
        return (listOf(MemoryItem(id = id, text = cleaned, createdAt = now)) + items).take(MAX_ITEMS)
    }

    fun remove(items: List<MemoryItem>, id: String): List<MemoryItem> = items.filterNot { it.id == id }

    /** Replaces the text of [id]; blank text or a duplicate of another item leaves the list unchanged. */
    fun update(items: List<MemoryItem>, id: String, text: String): List<MemoryItem> {
        val cleaned = clean(text)
        if (cleaned.isEmpty()) return items
        if (items.any { it.id != id && it.text.equals(cleaned, ignoreCase = true) }) return items
        return items.map { if (it.id == id) it.copy(text = cleaned) else it }
    }

    /** Null when empty; otherwise a bullet list, oldest first, that stays within [MAX_BLOCK_CHARS]. */
    fun systemBlock(items: List<MemoryItem>): String? {
        if (items.isEmpty()) return null
        val builder = StringBuilder(HEADER)
        var added = 0
        for (item in items.asReversed()) {
            val line = "\n- " + item.text
            if (builder.length + line.length > MAX_BLOCK_CHARS) break
            builder.append(line)
            added++
        }
        return if (added == 0) null else builder.toString()
    }

    /**
     * Combines the chat's own instructions with the memory block (memory first) into one system text.
     * Returns the instructions unchanged when memory is off or empty.
     */
    fun combineWithInstructions(instructions: String, items: List<MemoryItem>, enabled: Boolean): String {
        val block = if (enabled) systemBlock(items) else null
        val own = instructions.trim()
        return when {
            block == null -> instructions
            own.isEmpty() -> block
            else -> block + "\n\n" + own
        }
    }

    /**
     * Puts the memory block at the top of the dispatch's single system message (after
     * [SystemPromptPolicy.applyTo]), creating that message when the chat has no instructions.
     * Memory is never stored in the conversation history.
     */
    fun applyTo(
        messages: List<ChatMessage>,
        items: List<MemoryItem>,
        enabled: Boolean,
        createdAtEpochMs: Long
    ): List<ChatMessage> {
        val block = (if (enabled) systemBlock(items) else null) ?: return messages
        val existing = messages.firstOrNull { it.id == SystemPromptPolicy.MESSAGE_ID }
        if (existing != null) {
            return messages.map {
                if (it.id == SystemPromptPolicy.MESSAGE_ID) it.copy(content = block + "\n\n" + it.content) else it
            }
        }
        val systemMessage = ChatMessage(
            id = SystemPromptPolicy.MESSAGE_ID,
            correlationId = SystemPromptPolicy.MESSAGE_ID,
            role = MessageRole.SYSTEM,
            content = block,
            createdAtEpochMs = createdAtEpochMs
        )
        return listOf(systemMessage) + messages
    }

    /** "Remember that X" / "remember: X" / "please remember X" -> X (no final '.'), else null. */
    fun rememberCommand(message: String): String? {
        val trimmed = message.trim()
        val prefix = prefixes.firstOrNull { trimmed.startsWith(it, ignoreCase = true) } ?: return null
        val rest = trimmed.substring(prefix.length).trim().removeSuffix(".").trim()
        return rest.ifEmpty { null }
    }

    fun encode(items: List<MemoryItem>): String = json.encodeToString(items.take(MAX_ITEMS))

    /** Never throws: bad JSON gives an empty list; drops blank ids/texts and repeated ids. */
    fun decode(text: String): List<MemoryItem> = runCatching {
        json.decodeFromString<List<MemoryItem>>(text)
            .filter { it.id.isNotBlank() && it.text.isNotBlank() }
            .distinctBy { it.id }
            .map { it.copy(text = clean(it.text)) }
            .take(MAX_ITEMS)
    }.getOrDefault(emptyList())
}
