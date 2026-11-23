package com.hydrogen.ai.model

/** The words on the thought line above a reply (docs/ORBIT_DESIGN.md, "Thought line"). */
object ThoughtLineText {
    /**
     * [live] while the model is still thinking. [thinkingMs] is how long it thought, measured on this phone from
     * the first thinking text to the first word of the answer; null when unknown (the reply wasn't streamed).
     */
    fun label(live: Boolean, thinkingMs: Long?): String = when {
        live -> "Thinking"
        thinkingMs == null -> "Show thinking"
        thinkingMs < 2_000 -> "Thought for a moment"
        thinkingMs < 100_000 -> "Thought for ${(thinkingMs + 500) / 1_000} seconds"
        else -> {
            val minutes = (thinkingMs + 30_000) / 60_000
            if (minutes == 1L) "Thought for 1 minute" else "Thought for $minutes minutes"
        }
    }
}

/**
 * Separates a model's thinking from its answer when the model writes both into the same text, as local
 * reasoning models served by LM Studio or Ollama often do: `<think>…</think>` (or `<thinking>…</thinking>`)
 * at the start of the reply, then the answer. Orbit shows the thinking on the thought line, never in the answer,
 * and never sends it back to the model.
 *
 * Streaming: call [feed] with each text chunk in order, then [finish] once. Tags may be split across chunks.
 * Only a tag at the very start of the reply (after optional whitespace) counts, so the same text later in an
 * answer, e.g. in a code sample about these tags, stays part of the answer. A block that never closes (the
 * model ran out of room while thinking) is all thinking.
 */
class ThinkingSplitter {

    data class Part(val answer: String = "", val thinking: String = "") {
        fun isEmpty(): Boolean = answer.isEmpty() && thinking.isEmpty()
    }

    private enum class Mode { START, THINKING, AFTER_THINKING, ANSWER }

    private var mode = Mode.START
    private val pending = StringBuilder()
    private var closeTag = ""

    /** True once an opening tag was found at the start of the reply. */
    val sawThinkingBlock: Boolean
        get() = closeTag.isNotEmpty()

    fun feed(chunk: String): Part {
        if (chunk.isEmpty()) return Part()
        pending.append(chunk)
        return drain(final = false)
    }

    /** Releases anything still held back, e.g. a reply that is only "<" or a partial closing tag. */
    fun finish(): Part = drain(final = true)

    private fun drain(final: Boolean): Part {
        val answer = StringBuilder()
        val thinking = StringBuilder()
        while (true) {
            when (mode) {
                Mode.START -> {
                    val text = pending.toString()
                    val rest = text.trimStart()
                    val lead = text.length - rest.length
                    val open = OPEN_TAGS.firstOrNull { rest.startsWith(it, ignoreCase = true) }
                    if (open != null) {
                        closeTag = "</" + open.substring(1)
                        pending.delete(0, lead + open.length)
                        mode = Mode.THINKING
                    } else if (!final && (rest.isEmpty() || OPEN_TAGS.any { it.startsWith(rest, ignoreCase = true) })) {
                        break // Only whitespace or the start of a tag so far: wait for more.
                    } else {
                        mode = Mode.ANSWER
                    }
                }
                Mode.THINKING -> {
                    val text = pending.toString()
                    val close = text.indexOf(closeTag, ignoreCase = true)
                    if (close >= 0) {
                        thinking.append(text, 0, close)
                        pending.delete(0, close + closeTag.length)
                        mode = Mode.AFTER_THINKING
                    } else {
                        val keep = if (final) 0 else partialTagSuffix(text, closeTag)
                        thinking.append(text, 0, text.length - keep)
                        pending.delete(0, text.length - keep)
                        break
                    }
                }
                Mode.AFTER_THINKING -> {
                    // The answer usually starts with blank lines after the closing tag; drop them.
                    val rest = pending.toString().trimStart()
                    pending.setLength(0)
                    if (rest.isEmpty()) break
                    pending.append(rest)
                    mode = Mode.ANSWER
                }
                Mode.ANSWER -> {
                    answer.append(pending)
                    pending.setLength(0)
                    break
                }
            }
        }
        return Part(answer = answer.toString(), thinking = thinking.toString())
    }

    companion object {
        private val OPEN_TAGS = listOf("<think>", "<thinking>")
        private const val ORPHAN_CLOSE = "</think>"

        /** Splits a whole reply at once (replies that weren't streamed). */
        fun split(text: String): Part {
            val splitter = ThinkingSplitter()
            val first = splitter.feed(text)
            val last = splitter.finish()
            val whole = Part(answer = first.answer + last.answer, thinking = first.thinking + last.thinking)
            return if (splitter.sawThinkingBlock) whole else splitOrphanClose(whole.answer) ?: whole
        }

        /**
         * Some chat templates put the opening `<think>` in the prompt, so the reply starts with the thinking and
         * only the closing tag appears. Splits at that first `</think>` when no opening tag comes before it;
         * returns null when the reply has no such tag.
         */
        fun splitOrphanClose(text: String): Part? {
            val close = text.indexOf(ORPHAN_CLOSE, ignoreCase = true)
            if (close <= 0) return null
            val before = text.substring(0, close)
            if (before.contains("<think", ignoreCase = true) || before.contains("```")) return null
            if (before.isBlank()) return null
            return Part(answer = text.substring(close + ORPHAN_CLOSE.length).trimStart(), thinking = before)
        }

        /** Length of the longest end of [text] that could be the start of [tag], so it isn't shown too early. */
        internal fun partialTagSuffix(text: String, tag: String): Int {
            val max = minOf(text.length, tag.length - 1)
            for (n in max downTo 1) {
                if (tag.regionMatches(0, text, text.length - n, n, ignoreCase = true)) return n
            }
            return 0
        }
    }
}
