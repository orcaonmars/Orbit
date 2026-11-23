package com.hydrogen.ai.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteHealthProbeGuardTest {
    @Test
    fun https_provider_with_a_key_may_be_probed() {
        assertTrue(HttpGeminiHydrogenRelayBackend.remoteModelProbeAllowed("https://api.example.com", "k"))
        assertTrue(HttpGeminiHydrogenRelayBackend.remoteModelProbeAllowed("HTTPS://api.example.com:8443/openai", "k"))
    }

    @Test
    fun no_key_plain_http_or_local_hosts_are_never_probed_this_way() {
        assertFalse(HttpGeminiHydrogenRelayBackend.remoteModelProbeAllowed("https://api.example.com", "  "))
        assertFalse(HttpGeminiHydrogenRelayBackend.remoteModelProbeAllowed("http://192.168.1.50:1234", "k"))
        assertFalse(HttpGeminiHydrogenRelayBackend.remoteModelProbeAllowed("http://[::ffff:127.0.0.1]:1234", "k"))
        // Loopback servers keep their own dedicated fallback path.
        assertFalse(HttpGeminiHydrogenRelayBackend.remoteModelProbeAllowed("https://127.0.0.1:1234", "k"))
        assertFalse(HttpGeminiHydrogenRelayBackend.remoteModelProbeAllowed("not a url", "k"))
        assertFalse(HttpGeminiHydrogenRelayBackend.remoteModelProbeAllowed("", "k"))
    }
}
