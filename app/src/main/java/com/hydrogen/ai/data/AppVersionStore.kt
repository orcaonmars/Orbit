package com.hydrogen.ai.data

import android.content.Context

/** The app version this phone last finished starting with, for the one-time "What's new" sheet. 0 = never recorded. */
interface AppVersionStore {
    fun lastSeenVersion(): Int
    fun recordSeen(versionCode: Int)
}

class InMemoryAppVersionStore(private var seen: Int = 0) : AppVersionStore {
    override fun lastSeenVersion(): Int = seen
    override fun recordSeen(versionCode: Int) {
        seen = versionCode
    }
}

/** A version number in its own small file; nothing personal, so Delete all data leaves it. */
class SharedPrefsAppVersionStore(context: Context) : AppVersionStore {
    private val prefs = context.applicationContext.getSharedPreferences("orbit_app", Context.MODE_PRIVATE)

    override fun lastSeenVersion(): Int = prefs.getInt(KEY, 0)

    override fun recordSeen(versionCode: Int) {
        prefs.edit().putInt(KEY, versionCode).apply()
    }

    private companion object {
        const val KEY = "last_seen_version_code"
    }
}
