package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalServerPresetsTest {

    @Test
    fun presetsAreLocalAndMatchIgnoringTrailingSlashAndCase() {
        LocalServerPresets.presets.forEach { assertTrue(it.label, LocalServerPresets.isLocalServer(it.baseUrl)) }
        assertEquals("LM Studio", LocalServerPresets.matchingPreset(" HTTP://10.0.2.2:1234/ ")?.label)
        assertEquals("Ollama", LocalServerPresets.matchingPreset("http://10.0.2.2:11434")?.label)
        assertNull(LocalServerPresets.matchingPreset("http://10.0.2.2:18757"))
    }

    @Test
    fun realPhoneUsesLoopbackAndStillRecognisesEmulatorConfigs() {
        val before = LocalServerPresets.pcHost
        try {
            LocalServerPresets.pcHost = LocalServerPresets.DEVICE_HOST
            assertEquals("http://127.0.0.1:1234", ProviderPreset.LM_STUDIO.defaultBaseUrl)
            assertEquals("http://127.0.0.1:11434", ProviderPreset.OLLAMA.defaultBaseUrl)
            assertEquals("http://127.0.0.1:18757", ProviderPreset.HYDROGEN_RELAY.defaultBaseUrl)
            assertEquals("http://127.0.0.1:1234", LocalServerPresets.matchingPreset("http://10.0.2.2:1234")?.baseUrl)
            assertEquals("LM Studio", LocalServerPresets.matchingPreset("http://127.0.0.1:1234/")?.label)
            assertEquals(ProviderPreset.OLLAMA, ProviderPresets.detect(RelayConfig(baseUrl = "http://127.0.0.1:11434/v1")))
            assertEquals(ProviderPreset.LM_STUDIO, ProviderPresets.detect(RelayConfig(baseUrl = "http://10.0.2.2:1234")))
        } finally {
            LocalServerPresets.pcHost = before
        }
        assertEquals("http://10.0.2.2:1234", ProviderPreset.LM_STUDIO.defaultBaseUrl)
    }

    @Test
    fun remoteOrBlankUrlsAreNotLocalAndGetTheRightHint() {
        assertTrue(LocalServerPresets.isLocalServer("http://127.0.0.1:1234/"))
        assertFalse(LocalServerPresets.isLocalServer(""))
        assertFalse(LocalServerPresets.isLocalServer("https://relay.example.com"))
        assertFalse(LocalServerPresets.isLocalServer("http://192.168.1.20:1234"))

        assertNotNull(LocalServerPresets.hintFor(""))
        assertTrue(LocalServerPresets.hintFor("http://10.0.2.2:1234")!!.contains("token optional"))
        assertNull(LocalServerPresets.hintFor("https://relay.example.com"))
    }

    @Test
    fun privateServerClassificationRejectsEmbeddedCredentialsAndNonHttpSchemes() {
        assertFalse(LocalServerPresets.isLocalServer("ftp://127.0.0.1:1234"))
        assertFalse(LocalServerPresets.isLocalServer("http://admin:secret@127.0.0.1:1234"))
        assertFalse(LocalServerPresets.isWifiServer("http://admin:secret@192.168.1.20:1234"))
        assertFalse(LocalServerPresets.isPrivateServer("javascript:alert(1)"))
        assertTrue(LocalServerPresets.isLocalServer("https://127.0.0.1:1234"))
        assertTrue(LocalServerPresets.isWifiServer("http://192.168.1.20:1234"))
    }
}
