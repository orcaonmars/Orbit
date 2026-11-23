package com.hydrogen.ai.model

/**
 * Saved prompts with blanks: "Explain {{topic}} to a {{audience}}" asks for the topic and audience each time
 * it is used. Only `{{name}}` placeholders with a short, plain name count; anything else stays as written.
 */
object PromptTemplate {
    const val MAX_VARIABLES = 8
    const val MAX_NAME = 32

    private val placeholder = Regex("\\{\\{([^{}]*)\\}\\}")
    private val validName = Regex("[\\p{L}\\p{N} _-]{1,$MAX_NAME}")

    private fun nameOf(raw: String): String? = raw.trim().takeIf { validName.matches(it) }

    /** Distinct variable names in order of first appearance, at most [MAX_VARIABLES]. */
    fun variables(text: String): List<String> =
        placeholder.findAll(text)
            .mapNotNull { nameOf(it.groupValues[1]) }
            .distinct()
            .take(MAX_VARIABLES)
            .toList()

    /** Replaces each placeholder that has a value; values are inserted literally and never re-scanned. */
    fun fill(text: String, values: Map<String, String>): String {
        if (values.isEmpty()) return text
        return placeholder.replace(text) { match ->
            val name = nameOf(match.groupValues[1])
            if (name != null && name in values) values.getValue(name) else match.value
        }
    }

    /** One-line preview with each valid blank shown as "___": "Plan a ___-day trip to ___". */
    fun blanked(text: String): String =
        placeholder.replace(text) { match -> if (nameOf(match.groupValues[1]) != null) "___" else match.value }
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Ready-made templates offered when the saved-prompt list is empty. */
    val EXAMPLES = listOf(
        "Write a short, friendly email to {{recipient}} about {{subject}}.",
        "Translate this into {{language}}, keeping the tone:\n\n{{text}}",
        "Explain {{topic}} to {{audience}} in five sentences, with one everyday example.",
        "Plan a {{days}}-day trip to {{place}} on a {{budget}} budget, day by day."
    )

    /** Adds the [EXAMPLES] to [prompts] (newest first), skipping any already saved. */
    fun withExamples(prompts: List<SavedPrompt>, idPrefix: String): List<SavedPrompt> =
        EXAMPLES.foldIndexed(prompts) { index, list, text -> PromptLibrary.add(list, text, id = "$idPrefix-$index") }

    /** "topic" -> "Topic", "target_language" -> "Target language", "due-date" -> "Due date". */
    fun label(name: String): String {
        val words = name.replace('_', ' ').replace('-', ' ').trim().replace(Regex("\\s+"), " ")
        return words.replaceFirstChar { it.uppercaseChar() }
    }
}
