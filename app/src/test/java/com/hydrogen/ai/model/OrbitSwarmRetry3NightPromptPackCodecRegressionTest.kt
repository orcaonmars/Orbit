package com.hydrogen.ai.model

import org.junit.Test
import org.junit.Assert.*

class OrbitSwarmRetry3NightPromptPackCodecRegressionTest {
    @Test
    fun testAutoTitleOnlyWhenTitleEmpty() {
        // Prompt with title that becomes empty after cleaning (e.g., only spaces)
        val original = PromptPackCodec.SharedPrompt("   ", "Hello world")
        val encoded = PromptPackCodec.encode(listOf(original))
        val result = PromptPackCodec.decode(encoded)
        assertTrue("Decoding should succeed", result.isSuccess)
        val decoded = result.getOrThrow()
        // Title should be auto-generated from body, not empty
        assertEquals("Hello world", decoded[0].title)
        assertEquals("Hello world", decoded[0].text)
    }
}