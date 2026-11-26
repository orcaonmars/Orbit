package com.hydrogen.ai.model

/**
 * The "What's new" sheet shown once after updating from an older Orbit. A fresh install has nothing to compare with,
 * so it never sees the sheet; the version is simply recorded. Copy follows the house rules: plain words, short lines,
 * no exclamation marks, no em dashes. Rewrite [TITLE] and [ITEMS] whenever versionCode goes up, or the next update
 * would show these notes again.
 */
object WhatsNew {
    data class Item(val title: String, val body: String)

    const val TITLE = "What's new in Orbit 0.3.2"
    const val SUBTITLE = "Your chats, notes and settings came with you."
    const val DONE = "Start chatting"

    val ITEMS: List<Item> = listOf(
        Item("A clearer model switcher", "Recent and favorite models first, then one list per provider."),
        Item("A calmer chat", "Replies no longer dim or jump when they finish, and new chats offer a few ideas to start."),
        Item("Settings that fit", "Settings and About no longer sit under the navigation buttons."),
        Item("Fixes", "Pairing with a PC no longer crashes on Android 8 to 12, and read aloud picks a better voice.")
    )

    /**
     * True when this phone last ran an older Orbit. [lastSeenVersion] is 0 when no version was ever recorded, which
     * is either a fresh install or Orbit 0.1 (it recorded none); [hadSavedData] tells them apart.
     */
    fun shouldShow(lastSeenVersion: Int, currentVersion: Int, hadSavedData: Boolean): Boolean =
        lastSeenVersion < currentVersion && (lastSeenVersion > 0 || hadSavedData)
}
