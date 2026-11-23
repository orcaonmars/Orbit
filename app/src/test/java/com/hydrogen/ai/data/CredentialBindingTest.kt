package com.hydrogen.ai.data

import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialBindingTest {
    private fun config(url: String, route: ProviderRoute = ProviderRoute.GEMINI_HYDROGEN_RELAY) =
        RelayConfig(route = route, baseUrl = url, modelId = "test-model")

    @Test fun separate_services_never_share_a_credential() {
        val openAi = config("https://api.openai.com")
        val openRouter = config("https://openrouter.ai/api")
        val groq = config("https://api.groq.com/openai")
        val memory = InMemoryRelayCredentialStore()
        memory.setToken(openAi, "openai-secret")
        memory.setToken(openRouter, "openrouter-secret")
        assertEquals("openai-secret", memory.getToken(openAi))
        assertEquals("openrouter-secret", memory.getToken(openRouter))
        assertNull(memory.getToken(groq))
        assertNotEquals(CredentialBinding.forConfig(openAi), CredentialBinding.forConfig(openRouter))
    }

    @Test fun same_service_restores_its_key_after_switching_and_model_change() {
        val a = config("https://api.openai.com")
        val b = config("https://openrouter.ai/api")
        val composite = CompositeRelayCredentialStore(
            InMemoryRelayCredentialStore(), InMemoryRelayCredentialStore(), persistEnabled = true
        )
        composite.setToken(a, "A")
        composite.setToken(b, "B")
        assertEquals("A", composite.getToken(a.copy(modelId = "different")))
        assertEquals("B", composite.getToken(b))
        assertEquals("A", composite.getToken(a))
        composite.setToken(a, null)
        assertNull(composite.getToken(a))
        assertEquals("B", composite.getToken(b))
    }

    @Test fun exact_base_paths_and_routes_are_different_boundaries() {
        val a = config("https://api.example.org/tenant-a")
        val b = config("https://api.example.org/tenant-b")
        val c = config("https://api.example.org/tenant-a", ProviderRoute.ANTHROPIC_MESSAGES)
        val memory = InMemoryRelayCredentialStore()
        memory.setToken(a, "A")
        assertNull(memory.getToken(b))
        assertNull(memory.getToken(c))
        assertNotEquals(CredentialBinding.forConfig(a), CredentialBinding.forConfig(b))
        assertNotEquals(CredentialBinding.forConfig(a), CredentialBinding.forConfig(c))
    }

    @Test fun canonical_host_and_port_are_restored_but_model_is_not_in_identity() {
        val a = config("https://API.OPENAI.COM/v1/")
        val b = config("https://api.openai.com:443", ProviderRoute.GEMINI_HYDROGEN_RELAY)
        assertEquals(CredentialBinding.forConfig(a), CredentialBinding.forConfig(b))
        assertEquals(CredentialBinding.preferenceKey(CredentialBinding.forConfig(a)!!),
            CredentialBinding.preferenceKey(CredentialBinding.forConfig(b)!!))
    }

    @Test fun unsupported_or_unsafe_addresses_get_no_credential_binding() {
        assertNull(CredentialBinding.forConfig(config("")))
        assertNull(CredentialBinding.forConfig(config("http://api.example.com")))
        assertNull(CredentialBinding.forConfig(config("https://user@api.example.com")))
        assertNull(CredentialBinding.forConfig(config("https://api.example.com/?redirect=evil")))
        assertNull(CredentialBinding.forConfig(config("https://api.example.com/#token")))
        assertNull(CredentialBinding.forConfig(config("https://api.example.com", ProviderRoute.LOCAL_OFFLINE)))
        assertTrue(CredentialBinding.forConfig(config("http://127.0.0.1:1234")) != null)
        assertTrue(CredentialBinding.forConfig(config("http://10.0.2.2:1234")) != null)
        assertFalse(CredentialBinding.preferenceKey("not-secret").contains("not-secret"))
    }

    @Test fun disabled_persistence_does_not_leave_scoped_keys_on_disk() {
        val tokenConfig = config("https://api.openai.com")
        val memory = InMemoryRelayCredentialStore()
        val disk = InMemoryRelayCredentialStore()
        val composite = CompositeRelayCredentialStore(memory, disk, persistEnabled = true)
        composite.setToken(tokenConfig, "secret")
        assertEquals("secret", disk.getToken(tokenConfig))
        composite.persistEnabled = false
        composite.clearPersistedTokens()
        assertNull(disk.getToken(tokenConfig))
        assertEquals("secret", composite.getToken(tokenConfig))
        composite.clearAllTokens()
        assertNull(composite.getToken(tokenConfig))
    }
}
