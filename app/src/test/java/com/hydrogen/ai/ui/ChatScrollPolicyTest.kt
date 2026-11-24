package com.hydrogen.ai.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScrollPolicyTest {
    @Test fun readingHistoryPausesStreamingFollow() {
        val following = ChatScrollPolicy.afterUserScroll(true, towardHistory = true, atEnd = false)
        assertFalse(ChatScrollPolicy.shouldFollow(following, sentNewMessage = false, flippedVersion = false))
    }

    @Test fun returningToEndResumesFollow() {
        assertTrue(ChatScrollPolicy.afterUserScroll(false, towardHistory = false, atEnd = true))
        assertFalse(ChatScrollPolicy.afterUserScroll(false, towardHistory = false, atEnd = false))
    }

    @Test fun aNewUserMessageFollowsEvenWhenReadingHistory() {
        assertTrue(ChatScrollPolicy.shouldFollow(false, sentNewMessage = true, flippedVersion = false))
    }

    @Test fun versionNavigationNeverJumpsEvenWhenBranchChangesUserMessage() {
        assertFalse(ChatScrollPolicy.shouldFollow(true, sentNewMessage = true, flippedVersion = true))
    }

    @Test fun streamedUpdatesFollowOnlyWhenAttached() {
        assertTrue(ChatScrollPolicy.shouldFollow(true, sentNewMessage = false, flippedVersion = false))
        assertFalse(ChatScrollPolicy.shouldFollow(false, sentNewMessage = false, flippedVersion = false))
    }
}
