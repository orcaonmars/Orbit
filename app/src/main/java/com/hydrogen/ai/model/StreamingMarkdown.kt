package com.hydrogen.ai.model

/**
 * A reply that is still arriving can end in bare markdown syntax: a heading's "##" before its words, a list
 * marker, a quote mark or an opening fence. Rendered as is, those flash on screen as raw symbols for a moment.
 * [settled] hides such a trailing line until the words after it arrive. Finished replies are shown untouched.
 */
object StreamingMarkdown {
    private val DANGLING = Regex("""^[ \t]*(#{1,6}|[-*+]|\d{1,3}[.)]|>|\*{1,2}|_{1,2}|`{1,3}\w*)[ \t]*$""")

    fun settled(content: String): String {
        val cut = content.lastIndexOf('\n') + 1
        val tail = content.substring(cut)
        return if (DANGLING.matches(tail)) content.substring(0, cut).trimEnd('\n') else content
    }
}
