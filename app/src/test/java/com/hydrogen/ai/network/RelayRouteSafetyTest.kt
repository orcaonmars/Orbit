package com.hydrogen.ai.network

import com.hydrogen.ai.model.HydrogenDefaults
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class RelayRouteSafetyTest {
    @Test fun directGoogleModeNeverReusesRelayHostForApiKey() {
        val config = RelayConfig(
            route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT,
            baseUrl = "http://10.0.2.2:18757/v1",
            modelId = HydrogenDefaults.GOOGLE_AI_STUDIO_DEFAULT_MODEL
        )
        assertEquals(HydrogenDefaults.GOOGLE_AI_STUDIO_BASE_URL, config.effectiveBaseUrl)
    }

    @Test fun directGoogleModeCannotSendKeyToArbitraryEndpoint() {
        val config = RelayConfig(
            route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT,
            baseUrl = "https://untrusted.example.invalid/collect"
        )
        assertEquals("https://generativelanguage.googleapis.com/v1beta", config.effectiveBaseUrl)
    }

    @Test fun customHydrogenRelayRemainsConfigurable() {
        val config = RelayConfig(
            route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
            baseUrl = "http://10.0.2.2:18757/v1/"
        )
        assertEquals("http://10.0.2.2:18757/v1", config.effectiveBaseUrl)
    }
}
