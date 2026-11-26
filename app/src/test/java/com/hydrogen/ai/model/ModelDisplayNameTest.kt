package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDisplayNameTest {
    @Test
    fun plain_acronyms_keep_their_capitals() {
        assertEquals("Orbit QA Model", ModelDisplayName.friendly("orbit-qa-model"))
        assertEquals("My AI LLM", ModelDisplayName.friendly("my-ai-llm"))
    }

    @Test
    fun drops_publisher_and_folder_prefixes() {
        assertEquals("gemma-4-26b-a4b-it", ModelDisplayName.of("local-private/gemma-4-26b-a4b-it"))
        assertEquals("gemini-2.5-flash", ModelDisplayName.of("models/gemini-2.5-flash"))
        assertEquals("gemini-4-hydrogen", ModelDisplayName.of("gemini-4-hydrogen"))
    }

    @Test
    fun drops_ollama_latest_tag_but_keeps_other_tags() {
        assertEquals("llama3.2", ModelDisplayName.of("llama3.2:latest"))
        assertEquals("qwen2.5:7b", ModelDisplayName.of("qwen2.5:7b"))
    }

    @Test
    fun blank_and_odd_input_never_throw() {
        assertEquals(ModelDisplayName.PLACEHOLDER, ModelDisplayName.of(""))
        assertEquals(ModelDisplayName.PLACEHOLDER, ModelDisplayName.of("   "))
        assertEquals("org", ModelDisplayName.of("org/"))
        assertEquals(":latest", ModelDisplayName.of(":latest"))
    }

    @Test
    fun friendly_names_read_like_product_names() {
        assertEquals("Gemma 4 26B A4B", ModelDisplayName.friendly("local-private/gemma-4-26b-a4b-it"))
        assertEquals("Gemini 2.5 Flash", ModelDisplayName.friendly("models/gemini-2.5-flash"))
        assertEquals("Qwen2.5 7B", ModelDisplayName.friendly("qwen2.5:7b-instruct-q4_K_M"))
        assertEquals("Llama3.2", ModelDisplayName.friendly("llama3.2:latest"))
        assertEquals("Claude 3.5 Sonnet", ModelDisplayName.friendly("claude-3-5-sonnet-20241022"))
        assertEquals("Claude Sonnet 4.5", ModelDisplayName.friendly("claude-sonnet-4-5"))
        assertEquals("GPT-4o Mini", ModelDisplayName.friendly("gpt-4o-mini-2024-07-18"))
        assertEquals("GPT-OSS 20B", ModelDisplayName.friendly("openai/gpt-oss-20b"))
        assertEquals("DeepSeek R1", ModelDisplayName.friendly("deepseek-r1"))
        assertEquals("o3 Mini", ModelDisplayName.friendly("o3-mini"))
        assertEquals("Mistral 7B V0.2", ModelDisplayName.friendly("TheBloke/mistral-7b-instruct-v0.2-GGUF"))
        assertEquals("Gemini 2.5 Pro", ModelDisplayName.friendly("gemini-2.5-pro-preview-05-06"))
        assertEquals("Gemma 3n E2B", ModelDisplayName.friendly("google/gemma-3n-e2b-it"))
        assertEquals("Llama 3.1 8B", ModelDisplayName.friendly("Meta-Llama-3.1-8B-Instruct-Q8_0.gguf".removeSuffix(".gguf").removePrefix("Meta-")))
        assertEquals("Gemini 4 Hydrogen", ModelDisplayName.friendly("gemini-4-hydrogen"))
    }

    @Test
    fun friendly_never_loses_the_name_entirely() {
        assertEquals(ModelDisplayName.PLACEHOLDER, ModelDisplayName.friendly("  "))
        assertEquals("instruct", ModelDisplayName.friendly("instruct"))
        assertEquals("GPT", ModelDisplayName.friendly("gpt"))
        val long = ModelDisplayName.friendly("x".repeat(500))
        assertEquals(ModelDisplayName.MAX_CHARS, long.length)
        assertTrue(long.endsWith("…"))
        val longWords = ModelDisplayName.friendly(List(20) { "word" }.joinToString("-"))
        assertTrue(longWords.length <= ModelDisplayName.MAX_CHARS)
    }

    @Test
    fun very_long_names_are_capped() {
        val shown = ModelDisplayName.of("x".repeat(500))
        assertEquals(ModelDisplayName.MAX_CHARS, shown.length)
        assertTrue(shown.endsWith("…"))
    }

    @Test
    fun short_names_drop_the_family_when_the_rest_still_names_the_model() {
        assertEquals("2.5 Flash", ModelDisplayName.short("models/gemini-2.5-flash"))
        assertEquals("2.5 Pro", ModelDisplayName.short("gemini-2.5-pro-preview-05-06"))
        assertEquals("3.5 Sonnet", ModelDisplayName.short("claude-3-5-sonnet-20241022"))
        assertEquals("Sonnet 4.5", ModelDisplayName.short("claude-sonnet-4-5"))
        assertEquals("Opus 5.5", ModelDisplayName.short("claude-opus-5-5"))
    }

    @Test
    fun short_names_keep_the_family_when_it_is_the_only_name_or_carries_the_model() {
        // A leftover size or version alone would not say which model it is.
        assertEquals("Gemma 4 26B A4B", ModelDisplayName.short("local-private/gemma-4-26b-a4b-it"))
        assertEquals("Llama 3.1 8B", ModelDisplayName.short("Meta-Llama-3.1-8B-Instruct-Q8_0"))
        assertEquals("Qwen2.5 7B", ModelDisplayName.short("qwen2.5:7b-instruct-q4_K_M"))
        assertEquals("Mistral 7B V0.2", ModelDisplayName.short("TheBloke/mistral-7b-instruct-v0.2-GGUF"))
        assertEquals("Gemma 3n E2B", ModelDisplayName.short("google/gemma-3n-e2b-it"))
        // The first word is part of the name itself.
        assertEquals("GPT-4o Mini", ModelDisplayName.short("gpt-4o-mini-2024-07-18"))
        assertEquals("GPT-OSS 20B", ModelDisplayName.short("openai/gpt-oss-20b"))
        assertEquals("o3 Mini", ModelDisplayName.short("o3-mini"))
        assertEquals("DeepSeek R1", ModelDisplayName.short("deepseek-r1"))
        assertEquals("Gemini 4 Hydrogen", ModelDisplayName.short("gemini-4-hydrogen"))
    }

    @Test
    fun short_names_are_stable_and_never_blank() {
        // The same model reached through a service prefix reads identically, so the chip does not flicker.
        assertEquals(ModelDisplayName.short("gemini-2.5-flash"), ModelDisplayName.short("models/gemini-2.5-flash"))
        assertEquals(ModelDisplayName.PLACEHOLDER, ModelDisplayName.short("  "))
        assertEquals("instruct", ModelDisplayName.short("instruct"))
        assertEquals("Llama3.2", ModelDisplayName.short("llama3.2:latest"))
        assertEquals(
            ModelDisplayName.MAX_CHARS,
            ModelDisplayName.short(List(20) { "word" }.joinToString("-")).length
        )
    }
}
