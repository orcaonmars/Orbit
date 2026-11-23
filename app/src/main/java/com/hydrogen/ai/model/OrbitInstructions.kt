package com.hydrogen.ai.model

/**
 * The one system message every dispatch starts with. Order, from general to specific so later parts win:
 * Orbit's default reply style, then Personal (you), then memory, then the chat's own instructions (a persona fills
 * these). Nothing here is stored in the chat history.
 */
object OrbitInstructions {

    val DEFAULT_STYLE: String = listOf(
        "You are Orbit, a helpful assistant. Answer the question first, then add detail only if it helps.",
        "Write plainly and naturally, like a knowledgeable friend. Keep paragraphs short.",
        "Use headings, lists or tables only when they make the answer easier to scan. Short questions get short answers.",
        "Don't open with praise or filler like \"Great question\" or \"Certainly\". Don't end with a list of offers.",
        "If you're not sure, say so. Put code in fenced blocks with a language tag.",
        "When asked for a PDF or a document to keep, such as a worksheet from a photo with the answers filled in, " +
            "put the whole document in Markdown between <pdf title=\"Short title\"> and </pdf>. The app turns it into " +
            "a PDF to open, share or save, so never say you can't make files. For a worksheet, copy each question in " +
            "order and put its answer right under it. In the document, write math with Unicode (x², √2, ×, ÷, π, ≤, ½), not LaTeX. " +
            "Outside the tags, add at most one short sentence."
    ).joinToString("\n")

    /**
     * [memoryBlock] is null for a temporary chat or when memory is off. [projectBlock] (see [Projects.systemBlock])
     * comes after memory and before the chat's own instructions, so a chat can still override its project.
     */
    fun compose(
        personal: PersonalProfile,
        memoryBlock: String?,
        chatInstructions: String,
        projectBlock: String? = null
    ): String =
        listOfNotNull(
            DEFAULT_STYLE,
            Personal.systemBlock(personal),
            memoryBlock?.trim()?.takeIf { it.isNotEmpty() },
            projectBlock?.trim()?.takeIf { it.isNotEmpty() },
            SystemPromptPolicy.normalize(chatInstructions).takeIf { it.isNotEmpty() }
        ).joinToString("\n\n")

    /** Puts [text] first as the single SYSTEM message, replacing an earlier one. */
    fun applyTo(messages: List<ChatMessage>, text: String, createdAtEpochMs: Long): List<ChatMessage> {
        val rest = messages.filterNot { it.id == SystemPromptPolicy.MESSAGE_ID }
        if (text.isBlank()) return rest
        val system = ChatMessage(
            id = SystemPromptPolicy.MESSAGE_ID,
            correlationId = SystemPromptPolicy.MESSAGE_ID,
            role = MessageRole.SYSTEM,
            content = text,
            createdAtEpochMs = createdAtEpochMs
        )
        return listOf(system) + rest
    }
}
