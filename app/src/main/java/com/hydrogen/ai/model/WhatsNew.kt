package com.hydrogen.ai.model

/**
 * The "What's new" sheet shown once after updating from an older Orbit. A fresh install has nothing to compare with,
 * so it never sees the sheet; the version is simply recorded. Copy follows the house rules: plain words, short lines,
 * no exclamation marks, no em dashes. Rewrite [TITLE] and [ITEMS] whenever versionCode goes up, or the next update
 * would show these notes again.
 */
object WhatsNew {
    data class Item(val title: String, val body: String)

    const val TITLE = "What's new in Orbit 0.2"
    const val SUBTITLE = "Your chats, notes and settings came with you."
    const val DONE = "Start chatting"

    val ITEMS: List<Item> = listOf(
        Item("A new look", "Night, Paper and Midnight themes with five accents. Your accent moved to the closest new one."),
        Item("Voice in the app", "Talk instead of typing, with no popup. Voice chat has its own calm screen."),
        Item("Projects", "Keep chats, instructions and files together for one piece of work."),
        Item("Archive", "Put chats away without deleting them. Search still finds them."),
        Item("Edit and try again", "Change a message or ask again, then use the arrows to compare versions."),
        Item("Thinking, folded away", "See how long a model thought. Tap to read its thinking."),
        Item("Temporary chat", "A chat that is never saved and skips memory."),
        Item("Personal", "Tell Orbit your name and how you like replies."),
        Item("Word files", "Attach .docx files, like PDFs and text files.")
    )

    /**
     * True when this phone last ran an older Orbit. [lastSeenVersion] is 0 when no version was ever recorded, which
     * is either a fresh install or Orbit 0.1 (it recorded none); [hadSavedData] tells them apart.
     */
    fun shouldShow(lastSeenVersion: Int, currentVersion: Int, hadSavedData: Boolean): Boolean =
        lastSeenVersion < currentVersion && (lastSeenVersion > 0 || hadSavedData)
}
