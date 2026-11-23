package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderPresetsTest {
    private fun relay(url: String, model: String = "m") =
        RelayConfig(route = ProviderRoute.GEMINI_HYDROGEN_RELAY, baseUrl = url, modelId = model)

    @Test
    fun detect_maps_saved_configs_back_to_presets() {
        assertEquals(
            ProviderPreset.GOOGLE_GEMINI,
            ProviderPresets.detect(RelayConfig(route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT))
        )
        assertEquals(ProviderPreset.LM_STUDIO, ProviderPresets.detect(relay("http://10.0.2.2:1234/")))
        assertEquals(ProviderPreset.OLLAMA, ProviderPresets.detect(relay("http://10.0.2.2:11434/v1")))
        assertEquals(ProviderPreset.HYDROGEN_RELAY, ProviderPresets.detect(relay("http://10.0.2.2:18757")))
        assertEquals(ProviderPreset.HYDROGEN_RELAY, ProviderPresets.detect(relay("")))
        assertEquals(ProviderPreset.CUSTOM, ProviderPresets.detect(relay("https://api.example.com/v1", "some-model")))
    }

    @Test
    fun normalize_strips_trailing_slash_and_v1_once() {
        assertEquals("https://api.example.com", ProviderPresets.normalizeBaseUrl(" https://api.example.com/v1/ "))
        assertEquals("https://api.example.com/openai", ProviderPresets.normalizeBaseUrl("https://api.example.com/openai/V1"))
        assertEquals("https://api.example.com/v12", ProviderPresets.normalizeBaseUrl("https://api.example.com/v12"))
        assertEquals("", ProviderPresets.normalizeBaseUrl("   "))
    }

    @Test
    fun normalize_accepts_a_full_endpoint_copied_from_docs() {
        assertEquals("https://api.example.com", ProviderPresets.normalizeBaseUrl("https://api.example.com/v1/chat/completions"))
        assertEquals("https://api.example.com/openai", ProviderPresets.normalizeBaseUrl("https://api.example.com/openai/v1/models/"))
        assertEquals(
            "https://example.com/api/paas/v4",
            ProviderPresets.normalizeBaseUrl("https://example.com/api/paas/v4/chat/completions")
        )
    }

    @Test
    fun endpoint_keeps_a_version_the_address_already_names() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            ProviderPresets.openAiEndpoint("https://api.example.com", "chat/completions")
        )
        assertEquals(
            "https://api.example.com/openai/v1/models",
            ProviderPresets.openAiEndpoint("https://api.example.com/openai", "models")
        )
        assertEquals("http://10.0.2.2:1234/v1/models", ProviderPresets.openAiEndpoint("http://10.0.2.2:1234", "models"))
        assertEquals(
            "https://example.googleapis.com/v1beta/openai/chat/completions",
            ProviderPresets.openAiEndpoint("https://example.googleapis.com/v1beta/openai", "chat/completions")
        )
        assertEquals(
            "https://example.com/api/paas/v4/chat/completions",
            ProviderPresets.openAiEndpoint("https://example.com/api/paas/v4/", "chat/completions")
        )
        // "v1" in the host name is not a path version.
        assertEquals(
            "https://v1.example.com/v1/models",
            ProviderPresets.openAiEndpoint("https://v1.example.com", "models")
        )
    }

    @Test
    fun pasted_keys_split_and_clean() {
        assertEquals(listOf("sk-one-123456"), ProviderPresets.splitPastedKeys("  \"sk-one-123456\"\n"))
        assertEquals(listOf("sk-one-123456"), ProviderPresets.splitPastedKeys("Authorization: Bearer sk-one-123456"))
        assertEquals(listOf("abc"), ProviderPresets.splitPastedKeys("abc"))
        assertEquals(
            listOf("key-aaaaaaaa", "key-bbbbbbbb", "key-cccccccc"),
            ProviderPresets.splitPastedKeys("key-aaaaaaaa\nkey-bbbbbbbb, key-cccccccc;key-aaaaaaaa\r\n")
        )
        assertEquals(
            listOf("key-aaaaaaaa", "key-bbbbbbbb"),
            ProviderPresets.splitPastedKeys("x-api-key: key-aaaaaaaa\n1. key-bbbbbbbb")
        )
        assertEquals(emptyList<String>(), ProviderPresets.splitPastedKeys("  \n "))
    }

    @Test
    fun validate_requires_https_for_remote_and_allows_http_on_loopback() {
        assertNull(ProviderPresets.validate(ProviderPreset.CUSTOM, "https://api.example.com/v1", "m"))
        assertNotNull(ProviderPresets.validate(ProviderPreset.CUSTOM, "http://api.example.com", "m"))
        assertNotNull(ProviderPresets.validate(ProviderPreset.CUSTOM, "", "m"))
        assertNotNull(ProviderPresets.validate(ProviderPreset.CUSTOM, "ftp://x", "m"))
        assertNull(ProviderPresets.validate(ProviderPreset.LM_STUDIO, "http://10.0.2.2:1234", "m"))
    }

    @Test
    fun validate_model_rules() {
        assertNotNull(ProviderPresets.validate(ProviderPreset.GOOGLE_GEMINI, "", ""))
        assertNull(ProviderPresets.validate(ProviderPreset.GOOGLE_GEMINI, "", "gemini-2.5-flash"))
        assertNull(ProviderPresets.validate(ProviderPreset.OLLAMA, "http://10.0.2.2:11434", "", requireModel = false))
        assertNotNull(ProviderPresets.validate(ProviderPreset.OLLAMA, "http://10.0.2.2:11434", ""))
    }

    @Test
    fun saved_key_is_dropped_when_the_relay_server_changes() {
        val hydrogen = relay("http://10.0.2.2:18757")
        val relayRoute = ProviderRoute.GEMINI_HYDROGEN_RELAY
        assertEquals(true, ProviderPresets.keyBelongsToOtherServer(hydrogen, relayRoute, "https://api.example.com/v1"))
        assertEquals(true, ProviderPresets.keyBelongsToOtherServer(hydrogen, relayRoute, "http://10.0.2.2:1234"))
        assertEquals(false, ProviderPresets.keyBelongsToOtherServer(hydrogen, relayRoute, "http://10.0.2.2:18757/"))
        val custom = relay("https://api.example.com")
        assertEquals(false, ProviderPresets.keyBelongsToOtherServer(custom, relayRoute, "https://API.example.com:443/v1"))
        // Unparseable or first-time configs have no key to protect; Google keys live under their own route.
        assertEquals(false, ProviderPresets.keyBelongsToOtherServer(relay(""), relayRoute, "https://api.example.com"))
        assertEquals(false, ProviderPresets.keyBelongsToOtherServer(hydrogen, ProviderRoute.GOOGLE_AI_STUDIO_DIRECT, ""))
        // After a detour through Google the relay key's server is unknown, so it never reaches a relay server.
        val google = RelayConfig(route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT, modelId = "gemini-2.5-flash")
        assertEquals(true, ProviderPresets.keyBelongsToOtherServer(google, relayRoute, "http://10.0.2.2:1234"))
    }

    @Test
    fun hosted_services_use_fixed_https_addresses_and_local_servers_stay_local() {
        ProviderPreset.entries.forEach { preset ->
            when (preset) {
                ProviderPreset.GOOGLE_GEMINI -> assertEquals(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT, preset.route)
                ProviderPreset.ANTHROPIC, ProviderPreset.CUSTOM_ANTHROPIC ->
                    assertEquals(ProviderRoute.ANTHROPIC_MESSAGES, preset.route)
                else -> assertEquals(ProviderRoute.GEMINI_HYDROGEN_RELAY, preset.route)
            }
            val url = preset.defaultBaseUrl
            when (preset.group) {
                PresetGroup.HOSTED -> assert(url.startsWith("https://") && java.net.URI(url).host != null) {
                    "Hosted ${preset.name} must use a fixed HTTPS address"
                }
                PresetGroup.PC -> assert(LocalServerPresets.isLocalServer(url)) { "${preset.name} -> $url" }
                PresetGroup.OTHER -> assertEquals("", url)
            }
        }
    }
}
