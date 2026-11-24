package com.hydrogen.ai.model

/** Physical-keyboard shortcuts shared by tablets, foldables and desktop Android. */
internal enum class ChatKeyboardAction {
    FIND_IN_CHAT,
    NEW_CHAT,
    OPEN_CONVERSATIONS,
    CLOSE_FIND
}

/**
 * Pure, independently testable shortcut rules. Ordinary typing and text selection remain untouched.
 * Ctrl on Windows/Linux and Meta on macOS-compatible keyboards have the same meaning.
 */
internal object ChatKeyboardShortcuts {
    fun resolve(
        key: Char?,
        ctrl: Boolean,
        meta: Boolean,
        shift: Boolean,
        escape: Boolean,
        hasMessages: Boolean,
        findOpen: Boolean,
        canOpenDrawer: Boolean
    ): ChatKeyboardAction? {
        if (escape && findOpen && !ctrl && !meta) return ChatKeyboardAction.CLOSE_FIND
        if (!ctrl && !meta) return null
        return when (key?.lowercaseChar()) {
            'f' -> if (hasMessages && !shift) ChatKeyboardAction.FIND_IN_CHAT else null
            'o' -> if (shift) ChatKeyboardAction.NEW_CHAT else null
            'k' -> if (canOpenDrawer && !shift) ChatKeyboardAction.OPEN_CONVERSATIONS else null
            else -> null
        }
    }
}
