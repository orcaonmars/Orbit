package com.hydrogen.ai.model

import org.junit.Assert.*
import org.junit.Test

class ReasoningPolicyTest {
    @Test fun defaultsAreBackwardCompatible() {
        assertEquals(ReasoningMode.AUTO, ReasoningMode.fromStored("bogus"))
        assertEquals(ReasoningMode.AUTO, ReasoningMode.fromStored(null))
        assertNull(ReasoningPolicy.googleLevel("gemini-4-hydrogen", ReasoningMode.AUTO))
        assertNull(ReasoningPolicy.openAiEffort("https://api.openai.com", "gpt-5", ReasoningMode.AUTO))
    }
    @Test fun googleUsesOnlyKnownModels() {
        assertEquals("HIGH", ReasoningPolicy.googleLevel("models/gemini-4-hydrogen", ReasoningMode.HIGH))
        assertEquals("MEDIUM", ReasoningPolicy.googleLevel("gemini-3.8-flash", ReasoningMode.MEDIUM))
        assertNull(ReasoningPolicy.googleLevel("gemma-4", ReasoningMode.HIGH))
        assertNull(ReasoningPolicy.googleLevel("unknown", ReasoningMode.LOW))
    }
    @Test fun openAiUsesOnlyOfficialHostAndReasoningModels() {
        assertEquals("low", ReasoningPolicy.openAiEffort("https://api.openai.com/v1", "gpt-6.2-sol", ReasoningMode.LOW))
        assertNull(ReasoningPolicy.openAiEffort("https://api.openai.com.evil.test", "gpt-5", ReasoningMode.HIGH))
        assertNull(ReasoningPolicy.openAiEffort("http://api.openai.com", "gpt-5", ReasoningMode.HIGH))
        assertNull(ReasoningPolicy.openAiEffort("https://api.openai.com", "gpt-4o", ReasoningMode.HIGH))
        assertNull(ReasoningPolicy.openAiEffort("https://openrouter.ai/api", "openrouter/free", ReasoningMode.HIGH))
    }
}
