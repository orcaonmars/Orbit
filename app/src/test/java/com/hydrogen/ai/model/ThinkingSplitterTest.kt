package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingSplitterTest {

    /** Feeds [chunks] in order and returns the joined answer and thinking, as the stream reader would. */
    private fun stream(vararg chunks: String): ThinkingSplitter.Part {
        val splitter = ThinkingSplitter()
        val answer = StringBuilder()
        val thinking = StringBuilder()
        (chunks.map { splitter.feed(it) } + splitter.finish()).forEach {
            answer.append(it.answer)
            thinking.append(it.thinking)
        }
        return ThinkingSplitter.Part(answer.toString(), thinking.toString())
    }

    @Test
    fun plainReplyPassesThroughUnchanged() {
        assertEquals(ThinkingSplitter.Part("Hello there.", ""), stream("Hello", " there."))
    }

    @Test
    fun leadingThinkBlockGoesToThinkingAndBlankLinesAfterItAreDropped() {
        val part = stream("<think>\nThe user wants a greeting.\n</think>\n\nHi.")
        assertEquals("Hi.", part.answer)
        assertEquals("\nThe user wants a greeting.\n", part.thinking)
    }

    @Test
    fun tagsSplitAcrossChunksStillWork() {
        val part = stream("  <th", "ink>pla", "n it</th", "ink>", "\n", "\nAnswer", " here")
        assertEquals("Answer here", part.answer)
        assertEquals("plan it", part.thinking)
    }

    @Test
    fun thinkingTagVariantIsRecognisedCaseInsensitively() {
        val part = stream("<THINKING>weigh options</Thinking>Pick B.")
        assertEquals("Pick B.", part.answer)
        assertEquals("weigh options", part.thinking)
    }

    @Test
    fun thinkingIsReleasedAsItStreamsExceptAPossibleClosingTag() {
        val splitter = ThinkingSplitter()
        assertEquals(ThinkingSplitter.Part(), splitter.feed("<think>"))
        assertEquals(ThinkingSplitter.Part(thinking = "step one "), splitter.feed("step one </th"))
        assertEquals(ThinkingSplitter.Part(thinking = "</thx"), splitter.feed("x"))
        assertEquals(ThinkingSplitter.Part(thinking = " done"), splitter.feed(" done</think>"))
        assertEquals(ThinkingSplitter.Part(answer = "Yes."), splitter.feed("Yes."))
        assertTrue(splitter.sawThinkingBlock)
    }

    @Test
    fun aTagLaterInTheAnswerStaysPartOfTheAnswer() {
        val text = "Use the <think> tag like this: <think>x</think>"
        assertEquals(ThinkingSplitter.Part(text, ""), stream(text))
        assertEquals(ThinkingSplitter.Part(text, ""), ThinkingSplitter.split(text))
    }

    @Test
    fun somethingThatOnlyLooksLikeATagStartIsReleasedAsAnswer() {
        assertEquals(ThinkingSplitter.Part("<b>bold</b>", ""), stream("<", "b>bold</b>"))
        assertEquals(ThinkingSplitter.Part("<", ""), stream("<"))
        assertEquals(ThinkingSplitter.Part("<thin", ""), stream("<thin"))
        assertEquals(ThinkingSplitter.Part("  ", ""), stream("  "))
    }

    @Test
    fun anUnclosedBlockIsAllThinking() {
        val part = stream("<think>ran out of room mid-thou", "ght</thi")
        assertEquals("", part.answer)
        assertEquals("ran out of room mid-thought</thi", part.thinking)
    }

    @Test
    fun anEmptyBlockLeavesNoThinking() {
        val part = stream("<think>\n\n</think>\n\nShort answer.")
        assertEquals("Short answer.", part.answer)
        assertTrue(part.thinking.isBlank())
    }

    @Test
    fun splitHandlesWholeRepliesAndAnOrphanClosingTag() {
        assertEquals(ThinkingSplitter.Part("Answer", "plan"), ThinkingSplitter.split("<think>plan</think>Answer"))
        // The chat template opened the block in the prompt, so only the closing tag came back.
        assertEquals(
            ThinkingSplitter.Part("The answer is 4.", "Two plus two.\n"),
            ThinkingSplitter.split("Two plus two.\n</think>\n\nThe answer is 4.")
        )
    }

    @Test
    fun orphanClosingTagIsIgnoredInsideCodeOrAtTheStart() {
        assertNull(ThinkingSplitter.splitOrphanClose("```xml\n</think>\n```"))
        assertNull(ThinkingSplitter.splitOrphanClose("</think> is a closing tag"))
        assertNull(ThinkingSplitter.splitOrphanClose("No tags here."))
    }

    @Test
    fun partialTagSuffixFindsOnlyRealTagStarts() {
        assertEquals(3, ThinkingSplitter.partialTagSuffix("abc</t", "</think>"))
        assertEquals(1, ThinkingSplitter.partialTagSuffix("abc<", "</think>"))
        assertEquals(0, ThinkingSplitter.partialTagSuffix("abc</x", "</think>"))
        assertFalse(ThinkingSplitter().sawThinkingBlock)
    }

    @Test
    fun thoughtLineLabelsFollowTheDesignStrings() {
        assertEquals("Thinking", ThoughtLineText.label(live = true, thinkingMs = null))
        assertEquals("Thinking", ThoughtLineText.label(live = true, thinkingMs = 9_000))
        assertEquals("Show thinking", ThoughtLineText.label(live = false, thinkingMs = null))
        assertEquals("Thought for a moment", ThoughtLineText.label(live = false, thinkingMs = 0))
        assertEquals("Thought for a moment", ThoughtLineText.label(live = false, thinkingMs = 1_999))
        assertEquals("Thought for 2 seconds", ThoughtLineText.label(live = false, thinkingMs = 2_000))
        assertEquals("Thought for 12 seconds", ThoughtLineText.label(live = false, thinkingMs = 12_400))
        assertEquals("Thought for 99 seconds", ThoughtLineText.label(live = false, thinkingMs = 99_000))
        assertEquals("Thought for 2 minutes", ThoughtLineText.label(live = false, thinkingMs = 100_000))
        assertEquals("Thought for 4 minutes", ThoughtLineText.label(live = false, thinkingMs = 250_000))
    }
}
