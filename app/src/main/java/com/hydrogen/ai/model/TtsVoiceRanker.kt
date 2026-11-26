package com.hydrogen.ai.model

/**
 * Picks the best installed text-to-speech voice for reading replies aloud. Android's default voice is often the
 * oldest, flattest one the engine ships, while better voices sit unused in the same engine.
 *
 * Order of preference:
 * 1. Installed (not flagged "notInstalled") and on-device, so reply text never leaves the phone for speech.
 * 2. Same language and country as the phone, then same language.
 * 3. Higher engine-reported quality, then lower latency.
 * 4. Stable by name so the choice does not flicker between launches.
 *
 * Kept free of android.speech types so it can be unit tested; the speaker maps Voice to [Candidate].
 */
object TtsVoiceRanker {
    data class Candidate(
        val name: String,
        val language: String,
        val country: String,
        val quality: Int,
        val latency: Int,
        val needsNetwork: Boolean,
        val notInstalled: Boolean
    )

    fun pick(candidates: List<Candidate>, language: String, country: String): Candidate? {
        val lang = language.lowercase()
        val ctry = country.lowercase()
        return candidates
            .filter { !it.notInstalled && !it.needsNetwork && it.language.lowercase() == lang }
            .sortedWith(
                compareByDescending<Candidate> { it.country.lowercase() == ctry }
                    .thenByDescending { it.quality }
                    .thenBy { it.latency }
                    .thenBy { it.name }
            )
            .firstOrNull()
    }
}
