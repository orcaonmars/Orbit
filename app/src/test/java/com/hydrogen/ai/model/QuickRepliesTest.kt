package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickRepliesTest {

    @Test
    fun blank_answer_has_no_replies() {
        assertTrue(QuickReplies.suggest("  \n").isEmpty())
    }

    @Test
    fun offer_gets_yes_and_no_first() {
        assertEquals(
            listOf("Yes, please", "No, thanks", "Tell me more"),
            QuickReplies.suggest("Here is the plan. Would you like me to add tests?")
        )
        assertEquals(listOf("Yes, please", "No, thanks"), QuickReplies.suggest("Done.\nShall I continue?").take(2))
    }

    @Test
    fun offer_detection_is_case_insensitive_and_skips_lead_ins_and_markdown() {
        assertTrue(QuickReplies.endsWithOffer("WOULD YOU like a summary?"))
        assertTrue(QuickReplies.endsWithOffer("That's it. So, should I write the email?"))
        assertTrue(QuickReplies.endsWithOffer("**Want me to make a table?**"))
    }

    @Test
    fun ordinary_questions_are_not_offers() {
        assertFalse(QuickReplies.endsWithOffer("What is 2+2?"))
        assertFalse(QuickReplies.endsWithOffer("Would you like more? Here it is anyway."))
        assertFalse(QuickReplies.suggest("Which city do you mean?").contains("Yes, please"))
    }

    @Test
    fun offer_before_a_trailing_code_block_is_still_found() {
        val answer = "Can I show an example?\n\n```kotlin\nprintln(\"hi\")\n```"
        assertEquals(listOf("Yes, please", "No, thanks", "Explain this code"), QuickReplies.suggest(answer))
    }

    @Test
    fun code_answers_offer_an_explanation() {
        assertTrue(QuickReplies.suggest("Use this:\n```\nls -la\n```\nIt lists files.").contains("Explain this code"))
    }

    @Test
    fun long_answers_offer_a_shorter_version() {
        assertTrue(QuickReplies.suggest("word ".repeat(150)).contains("Make it shorter"))
        assertFalse(QuickReplies.suggest("Short.").contains("Make it shorter"))
    }

    @Test
    fun short_plain_answer_gets_tell_me_more() {
        assertEquals(listOf("Tell me more"), QuickReplies.suggest("Paris is the capital of France."))
    }

    @Test
    fun never_more_than_three_and_no_duplicates() {
        val answer = "x".repeat(700) + "\n```\ncode\n```\nWould you like me to continue?"
        val replies = QuickReplies.suggest(answer)
        assertEquals(listOf("Yes, please", "No, thanks", "Explain this code"), replies)
        assertEquals(replies.size, replies.distinct().size)
    }
}
