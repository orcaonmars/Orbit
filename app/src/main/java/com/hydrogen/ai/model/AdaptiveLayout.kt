package com.hydrogen.ai.model

/**
 * Screen-size rules for the chat layout, kept pure so they can be unit-tested.
 *
 * Two-pane means the chat list stays open beside the chat instead of sliding in as a drawer. It needs a wide
 * screen (Material's "expanded" width, 840dp) that is also tall enough: phones in landscape are wide but only
 * about 400dp tall, and a fixed list there would leave too little room for the chat and the keyboard.
 */
object AdaptiveLayout {
    const val TWO_PANE_MIN_WIDTH_DP = 840
    const val TWO_PANE_MIN_HEIGHT_DP = 480

    /** Width of the chat list when it stays open beside the chat. */
    const val CHAT_LIST_PANE_WIDTH_DP = 320

    fun isTwoPane(screenWidthDp: Int, screenHeightDp: Int): Boolean =
        screenWidthDp >= TWO_PANE_MIN_WIDTH_DP && screenHeightDp >= TWO_PANE_MIN_HEIGHT_DP
}
