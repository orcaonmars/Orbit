package com.hydrogen.ai.model

import kotlin.random.Random

/**
 * Quiet suggestions under the greeting on a new chat. Tapping one puts [draft] in the composer and leaves the
 * cursor at its end, so the person finishes the sentence; nothing is sent until they press Send.
 */
data class StartIdea(val id: String, val kind: Kind, val title: String, val detail: String, val draft: String) {
    /** Picks the icon in the UI; the model layer stays free of Compose. */
    enum class Kind { Plan, Summarize, Explain, Write, Reply, Ideas, Learn, Decide }
}

object StartIdeas {
    val ALL: List<StartIdea> = listOf(
        StartIdea("trip", StartIdea.Kind.Plan, "Plan a trip", "Two days, a city, a rough budget", "Plan a two day trip to "),
        StartIdea("summarize", StartIdea.Kind.Summarize, "Summarize something", "Paste an article, notes or a thread",
            "Summarize this in five short bullet points:\n\n"),
        StartIdea("explain", StartIdea.Kind.Explain, "Explain it simply", "Any topic, as if I am new to it", "Explain simply, with one everyday example: "),
        StartIdea("writing", StartIdea.Kind.Write, "Fix my writing", "Clearer and shorter, same meaning",
            "Make this clearer and shorter without changing what it says:\n\n"),
        StartIdea("reply", StartIdea.Kind.Reply, "Write a reply", "Friendly, short and to the point",
            "Write a short, friendly reply to this message:\n\n"),
        StartIdea("ideas", StartIdea.Kind.Ideas, "Brainstorm", "Ten fresh ideas to pick from", "Give me ten fresh ideas for "),
        StartIdea("learn", StartIdea.Kind.Learn, "Learn something", "A short lesson with a quick quiz",
            "Teach me the basics of this in five minutes, then quiz me with three questions: "),
        StartIdea("decide", StartIdea.Kind.Decide, "Help me decide", "Weigh the options side by side",
            "Help me decide between these options. Compare them and recommend one: ")
    )

    /** [count] different ideas in a stable order for one [seed], so a new chat shows a fresh set that survives rotation. */
    fun pick(seed: Long, count: Int = 3): List<StartIdea> =
        ALL.shuffled(Random(seed)).take(count.coerceIn(0, ALL.size))
}
