package com.hydrogen.ai.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Settings → Personal: what Orbit calls you, what it should know about you and how it should reply. Kept on this
 * device only and sent inside the system message of every chat, temporary chats included (memory is not).
 */
@Serializable
data class PersonalProfile(
    val name: String = "",
    val about: String = "",
    val replyStyle: String = ""
) {
    val isEmpty: Boolean
        get() = name.isBlank() && about.isBlank() && replyStyle.isBlank()
}

object Personal {
    const val MAX_NAME = 40
    const val MAX_FIELD = 1_500

    const val NAME_LABEL = "What should Orbit call you?"
    const val ABOUT_LABEL = "What should Orbit know about you?"
    const val STYLE_LABEL = "How should Orbit reply?"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** One line for the name, trimmed text for the two fields, each within its limit. */
    fun clean(profile: PersonalProfile): PersonalProfile = PersonalProfile(
        name = profile.name.replace(Regex("\\s+"), " ").trim().take(MAX_NAME).trim(),
        about = profile.about.trim().take(MAX_FIELD).trim(),
        replyStyle = profile.replyStyle.trim().take(MAX_FIELD).trim()
    )

    /** The part of the system message that describes you, or null when every field is blank. */
    fun systemBlock(profile: PersonalProfile): String? {
        val p = clean(profile)
        val about = listOfNotNull(
            p.name.takeIf { it.isNotEmpty() }?.let { "Their name is $it." },
            p.about.takeIf { it.isNotEmpty() }
        )
        val parts = listOfNotNull(
            about.takeIf { it.isNotEmpty() }?.let { "About the user:\n" + it.joinToString("\n") },
            p.replyStyle.takeIf { it.isNotEmpty() }?.let { "How the user wants you to reply:\n$it" }
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }

    /** First name for the start screen ("Good evening, Sam"); null when no name is set. */
    fun greetingName(profile: PersonalProfile): String? =
        clean(profile).name.substringBefore(' ').takeIf { it.isNotEmpty() }

    /** A one-tap reply style. Picking one drops its [opposite] (by label), so "Short" and "Detailed" never clash. */
    data class StyleChip(val label: String, val sentence: String, val opposite: String? = null)

    val STYLE_CHIPS: List<StyleChip> = listOf(
        StyleChip("Short", "Keep answers short unless I ask for more.", opposite = "Detailed"),
        StyleChip("Detailed", "Go into detail when it helps.", opposite = "Short"),
        StyleChip("Casual", "Keep it casual, like talking to a friend.", opposite = "Professional"),
        StyleChip("Professional", "Keep a professional tone.", opposite = "Casual"),
        StyleChip("Direct", "Be direct, and tell me when I'm wrong."),
        StyleChip("Examples", "Use concrete examples."),
        StyleChip("No emojis", "Don't use emojis.")
    )

    fun hasChip(style: String, chip: StyleChip): Boolean = style.contains(chip.sentence)

    /**
     * Adds [chip]'s sentence to [style] (after removing its opposite), or takes it out if it's already there. Text
     * the user typed is left alone. Returns [style] unchanged if the result would go over [MAX_FIELD].
     */
    fun toggleChip(style: String, chip: StyleChip): String {
        if (hasChip(style, chip)) return tidy(style.replace(chip.sentence, ""))
        val opposite = STYLE_CHIPS.firstOrNull { it.label == chip.opposite }
        val base = tidy(if (opposite != null) style.replace(opposite.sentence, "") else style)
        val added = if (base.isEmpty()) chip.sentence else "$base ${chip.sentence}"
        return if (added.length > MAX_FIELD) style else added
    }

    private fun tidy(text: String): String = text
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex("[ \\t]+\\n"), "\n")
        .replace(Regex("\\n[ \\t]+"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    fun encode(profile: PersonalProfile): String = json.encodeToString(PersonalProfile.serializer(), clean(profile))

    /** A damaged or unreadable file gives an empty profile rather than an error. */
    fun decode(text: String): PersonalProfile =
        runCatching { clean(json.decodeFromString(PersonalProfile.serializer(), text)) }.getOrDefault(PersonalProfile())
}
