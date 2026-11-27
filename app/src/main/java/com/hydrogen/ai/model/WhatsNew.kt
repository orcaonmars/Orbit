package com.hydrogen.ai.model

/**
 * The "What's new" sheet shown once after updating from an older Orbit. A fresh install has nothing to compare with,
 * so it never sees the sheet; the version is simply recorded. Copy follows the house rules: plain words, short lines,
 * no exclamation marks, no em dashes. Rewrite [TITLE] and [ITEMS] whenever versionCode goes up, or the next update
 * would show these notes again.
 */
object WhatsNew {
    data class Item(val title: String, val body: String)

    const val TITLE = "What's new in Orbit 0.3.4"
    const val SUBTITLE = "Your chats, notes and settings came with you."
    const val DONE = "Start chatting"

    val ITEMS: List<Item> = listOf(
        Item("Delete a model", "Every saved model has Delete in its menu, including the one you are using."),
        Item("Delete a provider", "Remove a whole provider, its saved models and its saved key, from its menu or page."),
        Item("Tidier model list", "The provider you are using comes first, and favorites sit at the top of each provider."),
        Item("Clearer billing message", "When a service blocks requests until billing is verified, Orbit now says so instead of out of credits.")
    )

    /**
     * True when this phone last ran an older Orbit. [lastSeenVersion] is 0 when no version was ever recorded, which
     * is either a fresh install or Orbit 0.1 (it recorded none); [hadSavedData] tells them apart.
     */
    fun shouldShow(lastSeenVersion: Int, currentVersion: Int, hadSavedData: Boolean): Boolean =
        lastSeenVersion < currentVersion && (lastSeenVersion > 0 || hadSavedData)
}
