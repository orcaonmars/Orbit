package com.hydrogen.ai.model

/** What the user typed in the assistant editor; [starterPrompts] may hold blank lines (empty fields). */
data class PersonaDraft(
    val name: String = "",
    val tagline: String = "",
    val greeting: String = "",
    val instructions: String = "",
    val starterPrompts: List<String> = emptyList()
)

/**
 * Turns the in-app assistant editor's fields into a persona. Users only write starter prompts; each card's
 * short title and hint are taken from the prompt ("Write a haiku" / "about the sea"), and an empty tagline
 * or greeting gets a friendly default, so a name and instructions are all that is required.
 */
object PersonaEditor {
    const val DEFAULT_TAGLINE = "Your own assistant"

    fun fromPersona(persona: Persona): PersonaDraft = PersonaDraft(
        name = persona.name,
        tagline = persona.tagline,
        greeting = persona.greeting,
        instructions = persona.instructions,
        starterPrompts = persona.starters.map { it.prompt }
    )

    /** A cleaned, validated persona for [draft], or the first problem as a short message. */
    fun build(draft: PersonaDraft): Result<SharedPersona> {
        val name = draft.name.trim()
        val prompts = draft.starterPrompts.map { it.trim() }.filter { it.isNotEmpty() }
        if (prompts.size > PersonaShareCodec.MAX_STARTERS) {
            return Result.failure(
                IllegalArgumentException("Add at most ${PersonaShareCodec.MAX_STARTERS} conversation starters.")
            )
        }
        val usedTitles = HashSet<String>()
        val starters = prompts.mapIndexed { index, prompt ->
            var (title, hint) = splitStarter(prompt)
            if (!usedTitles.add(title.lowercase())) {
                title = "Idea ${index + 1}"
                usedTitles.add(title.lowercase())
            }
            SharedStarter(title = title, hint = hint, prompt = prompt, icon = PersonaShareCodec.DEFAULT_ICON)
        }
        return PersonaShareCodec.validate(
            SharedPersona(
                name = name,
                tagline = draft.tagline.trim().ifEmpty { DEFAULT_TAGLINE },
                greeting = draft.greeting.trim().ifEmpty { defaultGreeting(name) },
                instructions = draft.instructions,
                starters = starters
            )
        )
    }

    fun defaultGreeting(name: String): String = if (name.isBlank()) "" else "Hi, I'm ${name.trim()}. How can I help?"

    /**
     * Card title (whole words, at most [PersonaShareCodec.MAX_STARTER_TITLE] characters) and hint (the rest
     * of the first line, at most [PersonaShareCodec.MAX_STARTER_HINT]) for a starter prompt.
     */
    fun splitStarter(prompt: String): Pair<String, String> {
        val line = prompt.trim().lineSequence().first().replace(Regex("\\s+"), " ").trimEnd(':', ' ')
        val maxTitle = PersonaShareCodec.MAX_STARTER_TITLE
        val words = line.split(' ').filter { it.isNotEmpty() }
        val title = StringBuilder()
        var used = 0
        for (word in words) {
            val next = if (title.isEmpty()) word.length else title.length + 1 + word.length
            if (next > maxTitle) break
            if (title.isNotEmpty()) title.append(' ')
            title.append(word)
            used++
        }
        if (title.isEmpty()) {
            // One very long first word: cut it.
            return line.take(maxTitle - 1) + ELLIPSIS to ""
        }
        val rest = words.drop(used).joinToString(" ")
        return title.toString() to shorten(rest, PersonaShareCodec.MAX_STARTER_HINT)
    }

    private fun shorten(text: String, max: Int): String {
        if (text.length <= max) return text
        val cut = text.take(max - 1)
        val space = cut.lastIndexOf(' ')
        return (if (space >= max / 2) cut.substring(0, space) else cut).trimEnd() + ELLIPSIS
    }

    private const val ELLIPSIS = "…"
}
