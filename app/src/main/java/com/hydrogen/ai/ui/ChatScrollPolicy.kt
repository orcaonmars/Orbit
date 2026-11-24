package com.hydrogen.ai.ui

/** Keeps explicit reading position separate from changes in the streamed message's height. */
internal object ChatScrollPolicy {
    fun afterUserScroll(following: Boolean, towardHistory: Boolean, atEnd: Boolean): Boolean = when {
        atEnd -> true
        towardHistory -> false
        else -> following
    }

    fun shouldFollow(following: Boolean, sentNewMessage: Boolean, flippedVersion: Boolean): Boolean =
        !flippedVersion && (following || sentNewMessage)
}
