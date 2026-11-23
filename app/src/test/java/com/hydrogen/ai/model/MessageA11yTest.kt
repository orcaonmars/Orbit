package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageA11yTest {

    private val now = 1_800_000_000_000L
    private fun ago(seconds: Long) = now - seconds * 1000

    @Test
    fun speakersAreHumanNames() {
        assertEquals("You", MessageA11y.speaker(MessageRole.USER))
        assertEquals("Orbit", MessageA11y.speaker(MessageRole.ASSISTANT))
        assertEquals("Instructions", MessageA11y.speaker(MessageRole.SYSTEM))
    }

    @Test
    fun relativeTimeReadsNaturally() {
        assertEquals("just now", MessageA11y.relativeTime(ago(5), now))
        assertEquals("just now", MessageA11y.relativeTime(now + 30_000, now)) // clock skew
        assertEquals("1 minute ago", MessageA11y.relativeTime(ago(60), now))
        assertEquals("5 minutes ago", MessageA11y.relativeTime(ago(5 * 60 + 10), now))
        assertEquals("1 hour ago", MessageA11y.relativeTime(ago(3600), now))
        assertEquals("23 hours ago", MessageA11y.relativeTime(ago(23 * 3600), now))
        assertEquals("yesterday", MessageA11y.relativeTime(ago(30 * 3600), now))
        assertEquals("3 days ago", MessageA11y.relativeTime(ago(3 * 86_400), now))
    }

    @Test
    fun countsCodeBlocksAndTables() {
        val md = "Intro\n```kotlin\nval a = 1\n```\ntext\n```\nmore\n```\n| a | b |\n| --- | --- |\n| 1 | 2 |\n"
        assertEquals(2, MessageA11y.codeBlockCount(md))
        assertEquals(1, MessageA11y.tableCount(md))
        assertEquals(1, MessageA11y.codeBlockCount("```\nstill streaming"))
        assertEquals(0, MessageA11y.codeBlockCount("no code, just `inline`"))
        assertEquals(0, MessageA11y.tableCount("a - b -- c"))
    }

    @Test
    fun countsMatchWhatTheScreenDraws() {
        // One-column tables are drawn as tables, so TalkBack names them too.
        assertEquals(1, MessageA11y.tableCount("| Step |\n| --- |\n| Install |\n"))
        // A table written inside a code block is code, not a table.
        val fenced = "```\n| a | b |\n| --- | --- |\n```"
        assertEquals(0, MessageA11y.tableCount(fenced))
        assertEquals(1, MessageA11y.codeBlockCount(fenced))
        // Tilde fences are code blocks too, including an unclosed one while streaming.
        assertEquals(1, MessageA11y.codeBlockCount("~~~python\nprint(1)\n~~~"))
        assertEquals(1, MessageA11y.codeBlockCount("~~~\nstill streaming"))
        // Inline triple backticks on one line are not a block.
        assertEquals(0, MessageA11y.codeBlockCount("use ```x``` here"))
        assertEquals(
            "Orbit, just now, 1 table",
            MessageA11y.header(MessageRole.ASSISTANT, "| Step |\n| --- |\n| Install |", ago(1), now)
        )
        assertEquals(0 to 0, MessageA11y.blockCounts("plain text"))
    }

    @Test
    fun labelHasSpeakerTimeExtrasAndPlainBody() {
        val label = MessageA11y.label(
            MessageRole.ASSISTANT,
            "**Done.** Here:\n```\ncode\n```",
            ago(120),
            now,
            attachmentCount = 2
        )
        assertTrue(label, label.startsWith("Orbit, 2 minutes ago, 1 code block, 2 attachments: "))
        assertTrue(label, label.contains("Done. Here:"))
        assertFalse(label, label.contains("**"))
        assertFalse(label, label.contains("```"))
    }

    @Test
    fun codeBlocksAreNamedOnceNotTwice() {
        val label = MessageA11y.label(
            MessageRole.ASSISTANT,
            "Try this:\n```kotlin\nval a = 1\n```\nThen run it.",
            ago(1),
            now
        )
        assertEquals("Orbit, just now, 1 code block: Try this: (code) Then run it.", label.replace('\n', ' ').replace("  ", " "))
        assertFalse(label, label.contains(SpeakableText.CODE_BLOCK_NOTE))
        assertEquals(label, 1, Regex("code block").findAll(label).count())
        // Read-aloud keeps its longer note, because there is no header there.
        assertTrue(SpeakableText.fromMarkdown("```\nx\n```").contains(SpeakableText.CODE_BLOCK_NOTE))
    }

    @Test
    fun errorAndEmptyMessages() {
        assertEquals(
            "Orbit, error, just now: (empty message)",
            MessageA11y.label(MessageRole.ASSISTANT, "  ", ago(1), now, isError = true)
        )
        assertEquals("You, just now: Hi", MessageA11y.label(MessageRole.USER, "Hi", ago(1), now))
    }

    @Test
    fun longBodiesAreCutOnAWordUnlessKeptWhole() {
        val long = (1..200).joinToString(" ") { "word$it" }
        val cut = MessageA11y.label(MessageRole.ASSISTANT, long, ago(1), now)
        val body = cut.substringAfter(": ")
        assertTrue(body.endsWith("…"))
        assertTrue(body.length <= MessageA11y.MAX_BODY_CHARS + 1)
        assertFalse("cut mid-word: $body", body.removeSuffix("…").endsWith("wor"))
        val whole = MessageA11y.label(MessageRole.USER, long, ago(1), now, maxBodyChars = Int.MAX_VALUE)
        assertTrue(whole.endsWith("word200"))
    }
}
