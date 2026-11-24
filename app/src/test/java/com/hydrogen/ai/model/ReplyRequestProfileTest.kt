package com.hydrogen.ai.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ReplyRequestProfileTest {
    private val config = RelayConfig(route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrl = "https://cleanapis.com", modelId = "opus", reasoningMode = ReasoningMode.ULTRA)

    @Test fun recordsRequestedBudgetWithoutRawEndpoint() {
        val profile = ReplyRequestProfile.from(config)
        assertEquals(64000, profile.completionBudget)
        assertTrue(profile.detail().contains("requested 64000"))
        assertFalse(profile.detail().contains("cleanapis.com"))
        val encoded = Json.encodeToString(ReplyRequestProfile.serializer(), profile)
        assertFalse(encoded.contains("https://"))
        assertEquals(profile, Json.decodeFromString(ReplyRequestProfile.serializer(), encoded))
    }

    @Test fun differentEffortModelOrEndpointChangesRetryIdentity() {
        val original = ReplyRequestProfile.from(config)
        assertTrue(original.sameRequestTarget(ReplyRequestProfile.from(config)))
        assertFalse(original.sameRequestTarget(ReplyRequestProfile.from(config.copy(reasoningMode = ReasoningMode.HIGH))))
        assertFalse(original.sameRequestTarget(ReplyRequestProfile.from(config.copy(modelId = "another"))))
        assertFalse(original.sameRequestTarget(ReplyRequestProfile.from(config.copy(baseUrl = "https://example.com"))))
    }
}
