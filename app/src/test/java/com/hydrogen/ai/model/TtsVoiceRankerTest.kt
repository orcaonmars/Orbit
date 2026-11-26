package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsVoiceRankerTest {
    private fun voice(
        name: String,
        lang: String = "en",
        country: String = "US",
        quality: Int = 300,
        latency: Int = 300,
        network: Boolean = false,
        notInstalled: Boolean = false
    ) = TtsVoiceRanker.Candidate(name, lang, country, quality, latency, network, notInstalled)

    @Test
    fun prefersHigherQualityOnDeviceVoiceInSameRegion() {
        val picked = TtsVoiceRanker.pick(
            listOf(voice("flat", quality = 300), voice("rich", quality = 400), voice("gb", country = "GB", quality = 500)),
            "en", "US"
        )
        assertEquals("rich", picked?.name)
    }

    @Test
    fun neverPicksNetworkOrMissingVoices() {
        val picked = TtsVoiceRanker.pick(
            listOf(voice("cloud", quality = 500, network = true), voice("absent", quality = 500, notInstalled = true),
                voice("local", quality = 200)),
            "en", "US"
        )
        assertEquals("local", picked?.name)
    }

    @Test
    fun fallsBackToSameLanguageOtherRegionAndThenNothing() {
        assertEquals("gb", TtsVoiceRanker.pick(listOf(voice("gb", country = "GB")), "en", "US")?.name)
        assertNull(TtsVoiceRanker.pick(listOf(voice("fr", lang = "fr", country = "FR")), "en", "US"))
    }

    @Test
    fun tiesBreakOnLatencyThenName() {
        val picked = TtsVoiceRanker.pick(
            listOf(voice("b", latency = 300), voice("a", latency = 300), voice("slow", latency = 500)),
            "en", "US"
        )
        assertEquals("a", picked?.name)
    }
}
