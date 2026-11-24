package com.hydrogen.ai.model

/**
 * The "What's new" sheet shown once after updating from an older Orbit. A fresh install has nothing to compare with,
 * so it never sees the sheet; the version is simply recorded. Copy follows the house rules: plain words, short lines,
 * no exclamation marks, no em dashes. Rewrite [TITLE] and [ITEMS] whenever versionCode goes up, or the next update
 * would show these notes again.
 */
object WhatsNew {
    data class Item(val title: String, val body: String)

    const val TITLE = "What's new in Orbit 0.3"
    const val SUBTITLE = "Your chats, notes and settings came with you."
    const val DONE = "Start chatting"

    val ITEMS: List<Item> = listOf(
        Item("Cleaner chats", "Find text in conversations, navigate saved prompts, and use compact reply controls."),
        Item("More model controls", "Browse providers, favorite models, and choose supported reasoning options."),
        Item("Safer local data", "Improved backups, attachment limits, context reporting, and local writes."),
        Item("Replies and projects", "Pause reading aloud, preview project files, and export code safely.")
    )

    /**
     * True when this phone last ran an older Orbit. [lastSeenVersion] is 0 when no version was ever recorded, which
     * is either a fresh install or Orbit 0.1 (it recorded none); [hadSavedData] tells them apart.
     */
    fun shouldShow(lastSeenVersion: Int, currentVersion: Int, hadSavedData: Boolean): Boolean =
        lastSeenVersion < currentVersion && (lastSeenVersion > 0 || hadSavedData)
}
