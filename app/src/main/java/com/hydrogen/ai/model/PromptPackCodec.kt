package com.hydrogen.ai.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Turns a set of saved prompts into one line of plain text ("orbit-prompts:v1:" + URL-safe Base64 JSON) that can
 * be pasted into any chat app, and back. Decoding never throws: anything odd yields a short plain-English message,
 * and every field is cleaned and length-checked, so a pasted pack is only ever data (never code or a URL).
 */
object PromptPackCodec {
    const val PREFIX = "orbit-prompts:v1:"
    const val MAX_PROMPTS = 20
    const val MAX_TITLE = 60
    const val MAX_TEXT = 4000
    const val MAX_TOKEN = 120_000
    private const val AUTO_TITLE = 40

    @Serializable
    data class SharedPrompt(val title: String, val text: String)

    @Serializable
    private data class Pack(val prompts: List<SharedPrompt> = emptyList())

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Encodes up to [MAX_PROMPTS] prompts (extra ones dropped; titles and texts trimmed and clipped to the limits;
     * entries with a blank text dropped; a blank title becomes the first 40 characters of the text's first line).
     */
    fun encode(prompts: List<SharedPrompt>): String {
        val clean = prompts.mapNotNull { prompt ->
            val text = clean(prompt.text).trim().take(MAX_TEXT).trim()
            if (text.isEmpty()) return@mapNotNull null
            val title = clean(prompt.title).trim().ifEmpty { autoTitle(text) }.take(MAX_TITLE).trim()
            SharedPrompt(title, text)
        }.distinct().take(MAX_PROMPTS)
        val bytes = json.encodeToString(Pack(clean)).toByteArray(Charsets.UTF_8)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** Convenience for the Saved prompts dialog. */
    fun fromSaved(prompts: List<SavedPrompt>): List<SharedPrompt> = prompts.map { SharedPrompt(it.title, it.text) }

    /**
     * Adds [imported] prompts to the top of the local library, skipping any whose text is already saved, and keeps
     * the library's own limits. Ids are made locally from [idPrefix] (never taken from the code). Returns the new
     * library and how many prompts were actually added.
     */
    fun mergeInto(existing: List<SavedPrompt>, imported: List<SharedPrompt>, idPrefix: String): Pair<List<SavedPrompt>, Int> {
        val known = existing.map { it.text.trim() }.toMutableSet()
        val added = imported.mapIndexedNotNull { index, prompt ->
            val text = prompt.text.take(PromptLibrary.MAX_TEXT_LENGTH)
            if (!known.add(text.trim())) return@mapIndexedNotNull null
            val title = PromptLibrary.titleFor(prompt.title) ?: PromptLibrary.titleFor(text) ?: "Prompt"
            SavedPrompt(id = "$idPrefix-$index", title = title, text = text)
        }
        return (added + existing).take(PromptLibrary.MAX_PROMPTS) to added.size
    }

    /** Finds [PREFIX] anywhere in [text] (for example inside a whole pasted message) and reads the pack. */
    fun decode(text: String): Result<List<SharedPrompt>> {
        val start = text.indexOf(PREFIX)
        if (start < 0) {
            return failure(
                if (text.contains("orbit-prompts:")) "This prompt pack is from a newer Orbit version."
                else "No Orbit prompt pack found."
            )
        }
        val token = text.substring(start + PREFIX.length).takeWhile { !it.isWhitespace() }
        if (token.isEmpty()) return failure("The prompt pack is empty.")
        if (token.length > MAX_TOKEN) return failure("The prompt pack is too long.")
        val decoded = try {
            String(Base64.getUrlDecoder().decode(token.trimEnd('=')), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            return failure("The prompt pack is damaged.")
        }
        val pack = try {
            json.decodeFromString<Pack>(decoded)
        } catch (e: Exception) {
            return failure("The prompt pack is damaged.")
        }
        if (pack.prompts.isEmpty()) return failure("The prompt pack has no prompts.")
        if (pack.prompts.size > MAX_PROMPTS) return failure("A prompt pack can hold at most $MAX_PROMPTS prompts.")
        val prompts = pack.prompts.map { prompt ->
            val title = clean(prompt.title).trim()
            val body = clean(prompt.text).trim()
            if (title.length > MAX_TITLE) return failure("Prompt titles must be $MAX_TITLE characters or fewer.")
            if (body.isEmpty()) return failure("Every prompt in the pack needs text.")
            if (body.length > MAX_TEXT) return failure("Prompt texts must be $MAX_TEXT characters or fewer.")
            // Preserve original title even if empty; auto-generate only when title is truly empty
            SharedPrompt(title.ifEmpty { autoTitle(body) }, body)
        }
        return Result.success(prompts.distinct())
    }

    private fun autoTitle(text: String): String =
        text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty().take(AUTO_TITLE).trim()

    /** Drops ASCII control characters except newline and tab (also removes stray carriage returns). */
    private fun clean(text: String): String =
        text.filter { it == '\n' || it == '\t' || !(it < ' ' || it == '\u007F') }

    private fun failure(message: String): Result<List<SharedPrompt>> = Result.failure(IllegalArgumentException(message))
}
