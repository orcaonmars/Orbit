package com.hydrogen.ai.model

/** Follow the phone, or force one theme. */
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

/**
 * Orbit's accent choices (docs/ORBIT_DESIGN.md, section 3). Each one is contrast-tested in Night, Paper and
 * Midnight (see ThemeContrastTest); order is the picker order.
 */
enum class AccentId(val label: String) {
    SUN("Sun"),
    TIDE("Tide"),
    NEBULA("Nebula"),
    EMBER("Ember"),
    GLACIER("Glacier");

    companion object {
        /** Accents saved by builds before the Orbit redesign, mapped to the closest new one. */
        internal val LEGACY: Map<String, AccentId> = mapOf(
            "HYDROGEN" to SUN,
            "OCEAN" to TIDE,
            "AURORA" to TIDE,
            "SUNSET" to EMBER,
            "ORCHID" to NEBULA,
            "GRAPHITE" to GLACIER
        )

        fun fromStored(name: String?): AccentId? =
            values().firstOrNull { it.name == name } ?: LEGACY[name]
    }
}

/**
 * How Orbit looks. Stored by name so reordering or adding values never shifts saved choices,
 * and an unknown or corrupted value falls back to the default instead of crashing.
 */
data class AppearancePrefs(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentId = AccentId.SUN,
    /** Pure black backgrounds for OLED screens; only affects the dark theme. */
    val pureBlack: Boolean = false
) {
    fun isDark(systemDark: Boolean): Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    companion object {
        fun parse(themeMode: String?, accent: String?, pureBlack: Boolean): AppearancePrefs = AppearancePrefs(
            themeMode = ThemeMode.values().firstOrNull { it.name == themeMode } ?: ThemeMode.SYSTEM,
            accent = AccentId.fromStored(accent) ?: AccentId.SUN,
            pureBlack = pureBlack
        )
    }
}
