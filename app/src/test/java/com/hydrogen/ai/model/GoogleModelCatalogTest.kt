package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleModelCatalogTest {

    @Test
    fun defaultGoogleModelIsGeminiHydrogen() {
        assertEquals("gemini-4-hydrogen", HydrogenDefaults.GOOGLE_AI_STUDIO_DEFAULT_MODEL)
        assertEquals(HydrogenDefaults.GOOGLE_AI_STUDIO_DEFAULT_MODEL, GoogleModelCatalog.suggestions.first())
        assertEquals("gemini-4-hydrogen", ProviderPreset.GOOGLE_GEMINI.defaultModelId)
    }

    @Test
    fun keepsChatModelsOnlyAndPutsHydrogenFirst() {
        val body = """
            {"models": [
              {"name": "models/gemini-2.5-flash", "supportedGenerationMethods": ["generateContent", "countTokens"]},
              {"name": "models/text-embedding-004", "supportedGenerationMethods": ["embedContent"]},
              {"name": "models/gemma-3-27b-it", "supportedGenerationMethods": ["generateContent"]},
              {"name": "models/gemini-4-hydrogen", "supportedGenerationMethods": ["generateContent"], "extra": 1},
              {"name": "models/gemini-2.5-flash-preview-tts", "supportedGenerationMethods": ["generateContent"]},
              {"name": "models/gemini-3-pro", "supportedGenerationMethods": ["generateContent"]},
              {"name": "models/gemini-2.5-flash", "supportedGenerationMethods": ["generateContent"]},
              {"supportedGenerationMethods": ["generateContent"]},
              "not-an-object"
            ], "nextPageToken": "x"}
        """.trimIndent()

        assertEquals(
            listOf("gemini-4-hydrogen", "gemini-3-pro", "gemini-2.5-flash", "gemma-3-27b-it"),
            GoogleModelCatalog.parse(body)
        )
    }

    @Test
    fun malformedOrEmptyBodiesGiveEmptyList() {
        assertTrue(GoogleModelCatalog.parse("").isEmpty())
        assertTrue(GoogleModelCatalog.parse("not json").isEmpty())
        assertTrue(GoogleModelCatalog.parse("""{"models": "nope"}""").isEmpty())
        assertTrue(GoogleModelCatalog.parse("""{"error": {"code": 403}}""").isEmpty())
    }
}
