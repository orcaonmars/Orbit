package com.hydrogen.ai.model

/** Up to three one-tap replies under the latest answer: "Yes, please" to an offer, "Explain this code", ... */
object QuickReplies {
    const val MAX = 3
    const val LONG_ANSWER = 600

    const val YES = "Yes, please"
    const val NO = "No, thanks"
    const val EXPLAIN_CODE = "Explain this code"
    const val SHORTER = "Make it shorter"
    const val MORE = "Tell me more"

    private val offerStarts = listOf("would you", "do you want", "shall i", "should i", "want me", "can i", "may i")
    private val leadIn = Regex("^(so,?\\s+|and\\s+)")
    private val sentenceBreak = Regex("(?<=[.!?])\\s+|\\n")

    fun suggest(answer: String): List<String> {
        if (answer.isBlank()) return emptyList()
        val replies = LinkedHashSet<String>()
        if (endsWithOffer(answer)) {
            replies += YES
            replies += NO
        }
        if (answer.contains("```")) replies += EXPLAIN_CODE
        if (answer.length >= LONG_ANSWER) replies += SHORTER
        if (replies.size < MAX) replies += MORE
        return replies.take(MAX)
    }

    /** True when the last sentence (ignoring a trailing code block) is an offer such as "Shall I continue?". */
    fun endsWithOffer(answer: String): Boolean {
        var text = answer.trimEnd()
        if (text.endsWith("```")) {
            val open = text.lastIndexOf("```", text.length - 4)
            if (open >= 0) text = text.substring(0, open).trimEnd()
        }
        val last = text.split(sentenceBreak).lastOrNull { it.isNotBlank() } ?: return false
        val sentence = last.trim().trimEnd('*', '_').trimEnd()
        if (!sentence.endsWith("?")) return false
        val start = sentence.trimStart { !it.isLetter() }.lowercase().replace(leadIn, "")
        return offerStarts.any { start.startsWith(it) }
    }
}
