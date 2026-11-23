package com.hydrogen.ai.model

/**
 * Pure, Android-free conversion of a conversation into shareable Markdown.
 *
 * Privacy: only titles, message text, model ids and attachment display names are exported.
 * Local URIs, technical error details, tool metadata and thought summaries are never included.
 */
object ConversationMarkdownExporter {

    fun export(conversation: Conversation): String = buildString {
        val title = singleLine(conversation.title).ifBlank { "Orbit chat" }
        append("# ").append(title).append('\n')
        append('\n').append("_Exported from Orbit · model: ")
            .append(singleLine(conversation.modelId)).append("_\n")

        conversation.messages.forEach { message ->
            if (message.status == MessageStatus.STAGED) return@forEach
            val body = message.content.trim()
            val names = message.attachments.map { singleLine(it.displayName).ifBlank { "attachment" } }
            if (body.isEmpty() && names.isEmpty()) return@forEach

            append('\n').append("## ").append(heading(message)).append("\n\n")
            if (names.isNotEmpty()) {
                append("Attachments: ").append(names.joinToString(", ")).append("\n\n")
            }
            if (body.isNotEmpty()) append(body).append('\n')
            when (message.status) {
                MessageStatus.FAILED -> append("\n_(request failed)_\n")
                MessageStatus.CANCELLED -> append("\n_(cancelled; partial output)_\n")
                MessageStatus.DISPATCHED, MessageStatus.STREAMING -> append("\n_(response still in progress)_\n")
                else -> Unit
            }
        }
    }

    /**
     * Every chat in one readable Markdown file, newest first, separated by horizontal rules.
     * Chats with nothing exportable (new, empty chats) are left out. Same privacy rules as [export].
     */
    fun exportAll(conversations: List<Conversation>): String {
        val parts = conversations
            .filter { conversation ->
                conversation.messages.any {
                    it.status != MessageStatus.STAGED && (it.content.isNotBlank() || it.attachments.isNotEmpty())
                }
            }
            .sortedByDescending { it.updatedAtEpochMs }
            .map { export(it).trimEnd() }
        return if (parts.isEmpty()) "" else parts.joinToString("\n\n---\n\n") + "\n"
    }

    /** Text placed on the clipboard when a single message is copied (document tags left out). */
    fun messageText(message: ChatMessage): String = PdfMarkdown.plainText(message.content.trim())

    private fun heading(message: ChatMessage): String = when (message.role) {
        MessageRole.USER -> "You"
        MessageRole.ASSISTANT -> "Assistant (${singleLine(message.modelId)})"
        MessageRole.SYSTEM -> "System"
    }

    private fun singleLine(value: String): String =
        value.replace(Regex("[\\r\\n]+"), " ").trim()
}
