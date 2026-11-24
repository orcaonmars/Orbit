package com.hydrogen.ai.model

/**
 * One-tap entry points from outside the app: the launcher's long-press shortcuts
 * (res/xml/shortcuts.xml) and the Quick Settings tile. Each opens a fresh chat; CAMERA and VOICE then
 * hand off to the phone's own camera or speech-recognizer app. Nothing is ever sent automatically.
 */
enum class QuickAction(val intentAction: String) {
    NEW_CHAT("com.hydrogen.ai.action.NEW_CHAT"),
    ASK_PHOTO("com.hydrogen.ai.action.ASK_PHOTO"),
    VOICE("com.hydrogen.ai.action.VOICE");

    companion object {
        /**
         * Android's assistant gesture (long-press Home / swipe from a bottom corner) once the user picks
         * Hydrogen under Settings → Default apps → Digital assistant app. Opens a fresh chat, keyboard up.
         */
        const val SYSTEM_ASSIST = "android.intent.action.ASSIST"

        /** The action for an incoming intent, or null for a normal launch, a share or anything unknown. */
        fun fromIntentAction(action: String?): QuickAction? = when {
            action.isNullOrBlank() -> null
            else -> {
                val trimmed = action.trim()
                when {
                    trimmed == SYSTEM_ASSIST -> NEW_CHAT
                    else -> values().firstOrNull { it.intentAction == trimmed }
                }
            }
        }
    }
}
