package com.hydrogen.ai.model

/**
 * Words and sky position for the empty chat (docs/ORBIT_DESIGN.md, "Empty state"). Pure so it can be tested
 * without a clock: callers pass the local hour or minute of the day.
 */
object StartGreeting {

    /** One of these sits under the greeting, picked once per new chat. */
    val SUBLINES: List<String> = listOf(
        "What are we working on?",
        "Where should we start?",
        "What's on your mind?",
        "Ask me anything, or add a photo."
    )

    /** "Good morning" and friends by local hour. Adds ", name" when a first name is known, except late at night. */
    fun greeting(hour: Int, firstName: String? = null): String {
        val h = Math.floorMod(hour, 24)
        val base = when (h) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            in 18..22 -> "Good evening"
            else -> return "Still up?"
        }
        val name = firstName?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NAME_LENGTH)
        return if (name == null) base else "$base, $name"
    }

    /**
     * Where the dot sits on the horizon arc. [fraction] runs 0 (left end) to 1 (right end) through the top.
     * By day the sun rises at 06:00 on the left, peaks at 12:00 and sets at 18:00 on the right. By night the
     * moon takes the same path from 18:00 to 06:00.
     */
    data class SkyPosition(val fraction: Float, val day: Boolean)

    fun skyPosition(minuteOfDay: Int): SkyPosition {
        val m = Math.floorMod(minuteOfDay, MINUTES_PER_DAY)
        return if (m in SUNRISE until SUNSET) {
            SkyPosition((m - SUNRISE) / HALF_DAY.toFloat(), day = true)
        } else {
            SkyPosition(Math.floorMod(m - SUNSET, MINUTES_PER_DAY) / HALF_DAY.toFloat(), day = false)
        }
    }

    private const val MAX_NAME_LENGTH = 24
    private const val MINUTES_PER_DAY = 24 * 60
    private const val HALF_DAY = 12 * 60
    private const val SUNRISE = 6 * 60
    private const val SUNSET = 18 * 60
}
