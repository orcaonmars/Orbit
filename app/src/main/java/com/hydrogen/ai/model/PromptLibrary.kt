package com.hydrogen.ai.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SavedPrompt(
    val id: String,
    val title: String,
    val text: String
)

/**
 * Pure, testable logic for the reusable prompt library shown from the composer.
 * Prompts are stored only on the device; nothing here touches the network.
 */
object PromptLibrary {
    const val MAX_PROMPTS = 100
    const val MAX_TEXT_LENGTH = 8_000
    const val MAX_TITLE_LENGTH = 40

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val starterPrompts: List<SavedPrompt> = listOf(
        SavedPrompt("starter-summarize", "Summarize", "Summarize the following in 5 concise bullet points:\n\n"),
        SavedPrompt("starter-explain", "Explain simply", "Explain this simply, as if to a smart 12-year-old:\n\n"),
        SavedPrompt("starter-translate", "Translate to English", "Translate the following into natural English:\n\n"),
        SavedPrompt("starter-grammar", "Fix grammar", "Fix the grammar and spelling, keep my tone, and return only the corrected text:\n\n"),
        SavedPrompt("starter-code-review", "Review code", "Review this code for bugs, security issues and readability. Suggest concrete fixes:\n\n")
    )

    /** First non-blank line, whitespace-collapsed and shortened, or null when the text is blank. */
    fun titleFor(text: String): String? {
        val firstLine = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        val collapsed = firstLine.replace(Regex("\\s+"), " ")
        return if (collapsed.length <= MAX_TITLE_LENGTH) {
            collapsed
        } else {
            collapsed.take(MAX_TITLE_LENGTH - 1).trimEnd() + "…"
        }
    }

    /** Adds [text] as a new prompt at the top. Blank or duplicate text leaves the list unchanged. */
    fun add(prompts: List<SavedPrompt>, text: String, id: String): List<SavedPrompt> {
        val clipped = text.take(MAX_TEXT_LENGTH)
        val title = titleFor(clipped) ?: return prompts
        if (prompts.any { it.text.trim() == clipped.trim() }) return prompts
        return (listOf(SavedPrompt(id = id, title = title, text = clipped)) + prompts).take(MAX_PROMPTS)
    }

    fun remove(prompts: List<SavedPrompt>, id: String): List<SavedPrompt> = prompts.filterNot { it.id == id }

    fun search(prompts: List<SavedPrompt>, query: String): List<SavedPrompt> =
        if (query.isBlank()) prompts else prompts.filter { SettingsSearch.matches(query, it.title, it.text) }

    /** A compact body preview; the original prompt is never modified. */
    fun preview(prompt: SavedPrompt): String = prompt.text.replace(Regex("\\s+"), " ").trim().take(180)

    /** Inserts a prompt into the composer draft without discarding what the user already typed. */
    fun insertIntoDraft(draft: String, promptText: String): String =
        if (draft.isBlank()) promptText else draft.trimEnd() + "\n\n" + promptText

    fun encode(prompts: List<SavedPrompt>): String = json.encodeToString(prompts.take(MAX_PROMPTS))

    /** Returns null for malformed input so callers can fall back without crashing. */
    fun decode(text: String): List<SavedPrompt>? = runCatching {
        json.decodeFromString<List<SavedPrompt>>(text)
            .filter { it.id.isNotBlank() && it.text.isNotBlank() }
            .distinctBy { it.id }
            .map { prompt ->
                val clipped = prompt.text.take(MAX_TEXT_LENGTH)
                prompt.copy(text = clipped, title = titleFor(prompt.title) ?: titleFor(clipped) ?: "Prompt")
            }
            .take(MAX_PROMPTS)
    }.getOrNull()
}
