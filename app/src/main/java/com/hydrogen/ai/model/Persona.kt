package com.hydrogen.ai.model

/**
 * A ready-made assistant. Picking one on an empty chat sets that chat's instructions (system prompt), so
 * it works with every provider and model and can be edited later under Chat instructions.
 */
data class Persona(
    val id: String,
    val name: String,
    val tagline: String,
    val greeting: String,
    val instructions: String,
    val starters: List<PersonaStarter> = emptyList()
)

/**
 * A first-message idea shown on a persona's empty chat. [prompt] goes into the composer (never sent
 * automatically); [icon] is a key the UI maps to an icon.
 */
data class PersonaStarter(val title: String, val hint: String, val prompt: String, val icon: String)

object Personas {
    val all: List<Persona> = listOf(
        Persona(
            id = "tutor",
            name = "Tutor",
            tagline = "Learn step by step",
            greeting = "What do you want to learn?",
            instructions = "You are a patient, encouraging tutor. Teach step by step with simple examples and " +
                "analogies. Check understanding with one short question before moving on. For homework, " +
                "guide the reasoning instead of only giving the answer.",
            starters = listOf(
                PersonaStarter("Explain", "a concept simply", "Explain this like I'm new to it: ", "idea"),
                PersonaStarter("Quiz me", "one question at a time", "Quiz me with 5 questions, one at a time, on ", "quiz"),
                PersonaStarter(
                    "Homework", "guide me, step by step",
                    "Help me work through this problem step by step, without just giving the answer:\n", "math"
                ),
                PersonaStarter("Study plan", "for an exam", "Make me a study plan for my exam on ", "calendar")
            )
        ),
        Persona(
            id = "coder",
            name = "Coder",
            tagline = "Code that runs",
            greeting = "What are we building?",
            instructions = "You are a senior software engineer. Give complete, working, idiomatic code with a " +
                "brief explanation. Ask for the language or framework only if it really matters. Point out " +
                "bugs, edge cases and security issues, and prefer small runnable examples over pseudo-code.",
            starters = listOf(
                PersonaStarter("Write code", "a Python script", "Write a Python script that ", "code"),
                PersonaStarter("Fix a bug", "paste the error", "Help me fix this error. Here is the code and the message:\n", "bug"),
                PersonaStarter("Explain code", "line by line", "Explain what this code does, line by line:\n", "idea"),
                PersonaStarter("Review", "bugs and security", "Review this code for bugs, edge cases and security issues:\n", "review")
            )
        ),
        Persona(
            id = "writer",
            name = "Writer",
            tagline = "Polish your words",
            greeting = "What are we writing?",
            instructions = "You are a sharp editor and writer. Keep the user's voice and be clear, concrete " +
                "and concise. When improving a text, give the improved version first, then at most three " +
                "short notes on what changed.",
            starters = listOf(
                PersonaStarter("Improve", "keep my voice", "Improve this text and keep my voice:\n", "edit"),
                PersonaStarter("Email reply", "polite and short", "Write a polite, concise reply to this email:\n", "email"),
                PersonaStarter("Shorten", "to the key points", "Make this shorter and clearer without losing the key points:\n", "shorten"),
                PersonaStarter("Social post", "short and catchy", "Write a short, engaging social media post about ", "campaign")
            )
        ),
        Persona(
            id = "translator",
            name = "Translator",
            tagline = "Any language",
            greeting = "What should I translate?",
            instructions = "You are a professional translator. Detect the source language. Translate into " +
                "English, or into the language the user asks for; if the text is already English and no " +
                "language is named, ask which one. Keep tone, formatting and names. Reply with only the " +
                "translation unless asked to explain.",
            starters = listOf(
                PersonaStarter("To English", "from any language", "Translate into English:\n", "translate"),
                PersonaStarter("To Spanish", "natural, not literal", "Translate into natural Spanish:\n", "language"),
                PersonaStarter(
                    "Idioms", "what a phrase means",
                    "Explain what this phrase means and how a native speaker would use it: ", "idea"
                ),
                PersonaStarter("Grammar", "check and explain", "Check the grammar and spelling of this text and explain each fix:\n", "spellcheck")
            )
        ),
        Persona(
            id = "coach",
            name = "Coach",
            tagline = "Plans you keep",
            greeting = "What's the goal?",
            instructions = "You are a practical, supportive coach. Turn the user's goals into small, concrete " +
                "next steps with realistic timing. Ask one clarifying question when needed. Be encouraging " +
                "but honest, and keep plans short enough to actually follow.",
            starters = listOf(
                PersonaStarter("Plan", "my week", "Help me plan my week. My priorities are: ", "calendar"),
                PersonaStarter("New habit", "one that sticks", "Help me build a habit that sticks: ", "habit"),
                PersonaStarter("Big goal", "into small steps", "Break this goal into small weekly steps: ", "checklist"),
                PersonaStarter("Decide", "between options", "Help me decide between these options: ", "decide")
            )
        ),
        Persona(
            id = "brainstorm",
            name = "Brainstorm",
            tagline = "Ideas, fast",
            greeting = "What should we brainstorm?",
            instructions = "You are a creative brainstorming partner. Offer many varied ideas quickly, from " +
                "safe to bold, as a numbered list with one or two lines each. When the user picks favourites, " +
                "build on them and combine them.",
            starters = listOf(
                PersonaStarter("Names", "for a project", "Give me 15 name ideas for ", "name"),
                PersonaStarter("Gift ideas", "for someone special", "Give me gift ideas for someone who loves ", "gift"),
                PersonaStarter("Side project", "that fits my skills", "Suggest side project ideas that fit my skills: ", "rocket"),
                PersonaStarter("Content", "post and video ideas", "Give me 10 post or video ideas about ", "campaign")
            )
        )
    )

    /** Icon keys the UI knows how to draw for [PersonaStarter.icon]. */
    val STARTER_ICONS: Set<String> = setOf(
        "idea", "quiz", "math", "calendar", "code", "bug", "review", "edit", "email", "shorten", "campaign",
        "translate", "language", "spellcheck", "habit", "checklist", "decide", "name", "gift", "rocket"
    )

    fun byId(id: String): Persona? = all.firstOrNull { it.id == id }

    /**
     * The persona a chat is using, judged by its instructions (built-in first, then [custom] imported ones);
     * null for none or instructions the user wrote.
     */
    fun matching(systemPrompt: String, custom: List<Persona> = emptyList()): Persona? {
        val prompt = SystemPromptPolicy.normalize(systemPrompt)
        if (prompt.isEmpty()) return null
        return (all + custom).firstOrNull { SystemPromptPolicy.normalize(it.instructions) == prompt }
    }

    /** True when the chat has instructions the user wrote (not a persona), which a persona must not overwrite. */
    fun hasCustomInstructions(systemPrompt: String, custom: List<Persona> = emptyList()): Boolean =
        SystemPromptPolicy.normalize(systemPrompt).isNotEmpty() && matching(systemPrompt, custom) == null
}
