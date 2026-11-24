package com.hydrogen.ai.data

import android.content.Context
import com.hydrogen.ai.model.AppearancePrefs

/** Theme choices live in their own small file, apart from provider settings and secrets. */
class AppearanceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("hydrogen_appearance", Context.MODE_PRIVATE)

    fun load(): AppearancePrefs {
        val values = prefs.all
        return AppearancePrefs.parseStoredValues(
            themeMode = values[KEY_MODE],
            accent = values[KEY_ACCENT],
            pureBlack = values[KEY_PURE_BLACK]
        )
    }

    fun save(value: AppearancePrefs) {
        prefs.edit()
            .putString(KEY_MODE, value.themeMode.name)
            .putString(KEY_ACCENT, value.accent.name)
            .putBoolean(KEY_PURE_BLACK, value.pureBlack)
            .apply()
    }

    private companion object {
        const val KEY_MODE = "theme_mode"
        const val KEY_ACCENT = "accent"
        const val KEY_PURE_BLACK = "pure_black"
    }
}
