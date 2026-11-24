package com.hydrogen.ai.model

import java.text.Normalizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MemoryItem(
    val id: String,
    val text: String,
    val createdAt: Long,
    /**
     * Pinned notes are shown before newer unpinned notes. Missing from older stored JSON,
     * so those files simply decode as unpinned.
     */
    val pinned: Boolean = false
)

/**
 * "Memory you control": a short list of facts the user chose to share with every chat.
 * Stored only on the device; it travels only inside the system prompt of the provider the user picked.
 */
object UserMemory {
    const val MAX_ITEMS = 100
    const val MAX_ITEM_CHARS = 300
    const val MAX_BLOCK_CHARS = 3000
    const val HEADER = "Things the user asked you to remember:"

    /** Two notes are treated as the same when they share more than this share of their words. */
    private const val NEAR_DUPLICATE_OVERLAP = 0.8

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prefixes = listOf("remember that ", "remember: ", "please remember ")
    private val whitespace = Regex("\\s+")
    private val combiningMarks = Regex("\\p{Mn}+")
    private val wordSeparators = Regex("[^\\p{L}\\p{N}]+")

    /** Trims, collapses whitespace (including newlines) and clips to [MAX_ITEM_CHARS]. */
    fun clean(text: String): String =
        text.replace(whitespace, " ").trim().take(MAX_ITEM_CHARS).trim()

    /** Lowercased words without accents or punctuation; used only for duplicate detection. */
    private fun words(text: String): Set<String> =
        combiningMarks
            .replace(Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD), "")
            .replace("-", "")
            .split(wordSeparators)
            .filterTo(LinkedHashSet()) { it.isNotEmpty() }

    /** Exact repeats (ignoring case, accents and punctuation) and near-identical word overlap. */
    private fun isDuplicate(existing: String, candidate: String): Boolean {
        val left = words(existing)
        val right = words(candidate)
        if (left.isEmpty() || right.isEmpty()) {
            return clean(existing).equals(clean(candidate), ignoreCase = true)
        }
        val shared = left.count { it in right }
        val union = left.size + right.size - shared
        return shared.toDouble() / union > NEAR_DUPLICATE_OVERLAP
    }

    /**
     * New items go first; blank text or a duplicate (case-insensitive or near-identical)
     * leaves the list unchanged.
     */
    fun add(items: List<MemoryItem>, text: String, id: String, now: Long): List<MemoryItem> {
        val cleaned = clean(text)
        if (cleaned.isEmpty()) return items
        if (items.any { isDuplicate(it.text, cleaned) }) return items
        return trimToCap(listOf(MemoryItem(id = id, text = cleaned, createdAt = now)) + items)
    }

    /** Local, accent-insensitive note search. Never changes the saved list or prompt order. */
    fun search(items: List<MemoryItem>, query: String): List<MemoryItem> {
        fun fold(text: String): String =
            combiningMarks.replace(Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD), "")
        val terms = fold(query).split(whitespace).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return items
        return items.filter { item -> terms.all { it in fold(item.text) } }
    }

    fun setPinned(items: List<MemoryItem>, id: String, pinned: Boolean): List<MemoryItem> =
        items.map { if (it.id == id) it.copy(pinned = pinned) else it }

    fun remove(items: List<MemoryItem>, id: String): List<MemoryItem> = items.filterNot { it.id == id }

    /** Replaces the text of [id]; blank text or a duplicate of another item leaves the list unchanged. */
    fun update(items: List<MemoryItem>, id: String, text: String): List<MemoryItem> {
        val cleaned = clean(text)
        if (cleaned.isEmpty()) return items
        if (items.any { it.id != id && isDuplicate(it.text, cleaned) }) return items
        return items.map { if (it.id == id) it.copy(text = cleaned) else it }
    }

    /**
     * Null when empty; otherwise a bullet list, pinned notes first and then newest first,
     * that stays within [MAX_BLOCK_CHARS].
     */
    fun systemBlock(items: List<MemoryItem>): String? {
        if (items.isEmpty()) return null
        val builder = StringBuilder(HEADER)
        var added = 0
        for (item in promptOrder(items)) {
            val line = "\n- " + item.text
            if (builder.length + line.length > MAX_BLOCK_CHARS) break
            builder.append(line)
            added++
        }
        return if (added == 0) null else builder.toString()
    }

    /** Pinned notes first, then newest first; ties keep the stored (newest-first) order. */
    private fun promptOrder(items: List<MemoryItem>): List<MemoryItem> =
        items.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<MemoryItem>> { it.value.pinned }
                    .thenByDescending { it.value.createdAt }
                    .thenBy { it.index }
            )
            .map { it.value }

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

    fun encode(items: List<MemoryItem>): String = json.encodeToString(trimToCap(items))

    /**
     * Never throws: bad JSON gives an empty list; drops invalid entries and repeated ids.
     * Preserve existing distinct IDs even if their text overlaps: loading must not silently
     * remove previously saved notes. Duplicate detection applies to new edits/additions.
     */
    fun decode(text: String): List<MemoryItem> = runCatching {
        val parsed = json.decodeFromString<List<MemoryItem>>(text)
            .filter { it.id.isNotBlank() && it.text.isNotBlank() }
            .distinctBy { it.id }
            .map { it.copy(text = clean(it.text)) }
            .filter { it.text.isNotEmpty() }
        trimToCap(parsed)
    }.getOrDefault(emptyList())

    /** Keeps [MAX_ITEMS]; drops the oldest unpinned notes first so pinned notes survive the cap. */
    private fun trimToCap(items: List<MemoryItem>): List<MemoryItem> {
        if (items.size <= MAX_ITEMS) return items
        var toDrop = items.size - MAX_ITEMS
        val dropped = BooleanArray(items.size)
        for (index in items.indices.reversed()) {
            if (toDrop == 0) break
            if (!items[index].pinned) {
                dropped[index] = true
                toDrop--
            }
        }
        for (index in items.indices.reversed()) {
            if (toDrop == 0) break
            if (!dropped[index]) {
                dropped[index] = true
                toDrop--
            }
        }
        return items.filterIndexed { index, _ -> !dropped[index] }
    }
}
