package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacySummaryTest {

    private fun facts(
        route: ProviderRoute = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        url: String = "http://127.0.0.1:11434/v1",
        paired: Boolean = false,
        chats: Int = 3,
        memory: Int = 2,
        memoryOn: Boolean = true,
        prompts: Int = 1
    ) = PrivacyFacts(route, url, paired, chats, memory, memoryOn, prompts)

    private fun lines(f: PrivacyFacts, title: String): List<String> =
        PrivacySummary.sections(f).first { it.title == title }.lines

    @Test
    fun hostParsing() {
        assertEquals("127.0.0.1", PrivacySummary.hostOf("http://127.0.0.1:11434/v1"))
        assertEquals("api.example.com", PrivacySummary.hostOf("https://API.Example.com/v1/"))
        assertEquals("192.168.1.20", PrivacySummary.hostOf("192.168.1.20:8080"))
        assertEquals("::1", PrivacySummary.hostOf("http://[::1]:8080"))
        assertNull(PrivacySummary.hostOf(""))
        assertNull(PrivacySummary.hostOf("   "))
    }

    @Test
    fun destinations() {
        val relay = ProviderRoute.GEMINI_HYDROGEN_RELAY
        assertEquals(MessageDestination.THIS_DEVICE, PrivacySummary.destinationOf(relay, "http://localhost:8080"))
        assertEquals(MessageDestination.THIS_DEVICE, PrivacySummary.destinationOf(relay, "http://127.0.0.1:1234"))
        assertEquals(MessageDestination.THIS_DEVICE, PrivacySummary.destinationOf(relay, "http://[::1]:1234"))
        assertEquals(MessageDestination.EMULATOR_HOST, PrivacySummary.destinationOf(relay, "http://10.0.2.2:11434"))
        assertEquals(MessageDestination.LOCAL_NETWORK, PrivacySummary.destinationOf(relay, "http://192.168.0.5:1234"))
        assertEquals(MessageDestination.LOCAL_NETWORK, PrivacySummary.destinationOf(relay, "http://10.1.2.3:1234"))
        assertEquals(MessageDestination.LOCAL_NETWORK, PrivacySummary.destinationOf(relay, "http://172.20.0.9:1234"))
        assertEquals(MessageDestination.LOCAL_NETWORK, PrivacySummary.destinationOf(relay, "http://studio.local:1234"))
        assertEquals(MessageDestination.INTERNET, PrivacySummary.destinationOf(relay, "http://172.32.0.9:1234"))
        assertEquals(MessageDestination.INTERNET, PrivacySummary.destinationOf(relay, "https://openrouter.ai/api/v1"))
        assertEquals(MessageDestination.NOWHERE, PrivacySummary.destinationOf(relay, ""))
        assertEquals(
            MessageDestination.NOWHERE,
            PrivacySummary.destinationOf(ProviderRoute.LOCAL_OFFLINE, "http://127.0.0.1:1")
        )
    }

    @Test
    fun onlyLoopbackSaysStaysOnThisDevice() {
        val urls = listOf(
            "http://10.0.2.2:11434" to false,
            "http://192.168.1.4:1234" to false,
            "https://api.openai-compatible.example/v1" to false,
            "http://127.0.0.1:11434/v1" to true,
            "http://localhost:1234" to true
        )
        for ((url, local) in urls) {
            val text = PrivacySummary.asText(facts(url = url))
            assertEquals("for $url", local, text.contains("stays on this device") || text.contains("stay on this device"))
        }
        val google = PrivacySummary.asText(
            facts(route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT, url = HydrogenDefaults.GOOGLE_AI_STUDIO_BASE_URL)
        )
        assertFalse(google.contains("on this device."))
    }

    @Test
    fun googleRouteNamesGoogleAndTheKey() {
        val first = lines(
            facts(route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT, url = HydrogenDefaults.GOOGLE_AI_STUDIO_BASE_URL),
            "Where your messages go"
        ).first()
        assertTrue(first.contains("Google (generativelanguage.googleapis.com)"))
        assertTrue(first.contains("your own API key"))
    }

    @Test
    fun unconfiguredAndOfflineSendNothing() {
        assertTrue(lines(facts(url = ""), "Where your messages go").first().contains("no messages are sent"))
        val offline = lines(facts(route = ProviderRoute.LOCAL_OFFLINE, url = ""), "Where your messages go").first()
        assertTrue(offline.contains("isn't installed"))
        // No "only this conversation is sent" or memory line next to "nothing is sent".
        assertEquals(1, lines(facts(url = ""), "Where your messages go").size)
        assertEquals(1, lines(facts(route = ProviderRoute.LOCAL_OFFLINE, url = ""), "Where your messages go").size)
    }

    @Test
    fun memoryLineFollowsTheSwitchAndCount() {
        val where = "Where your messages go"
        assertTrue(lines(facts(memory = 2), where).any { it.startsWith("Your 2 memory notes are added") })
        assertTrue(lines(facts(memory = 1), where).any { it.startsWith("Your memory note is added") })
        assertTrue(lines(facts(memory = 2, memoryOn = false), where).any { it.contains("aren't sent") })
        assertFalse(lines(facts(memory = 0), where).any { it.contains("memory") })
    }

    @Test
    fun storedCountsArePluralisedPlainly() {
        val stored = lines(facts(chats = 1, memory = 0, memoryOn = false, prompts = 12), "What stays on this phone")
        assertEquals("1 chat, including attached photos and files.", stored[0])
        assertEquals("No memory notes (memory is off).", stored[1])
        assertTrue(stored[2].startsWith("12 saved prompts"))
        assertTrue(stored.any { it.contains("Android Keystore") })
        assertEquals("No chats yet.", lines(facts(chats = 0), "What stays on this phone")[0])
    }

    @Test
    fun pcSectionDependsOnPairing() {
        assertTrue(lines(facts(paired = false), "Your PC").single().contains("No PC is paired"))
        val paired = lines(facts(paired = true), "Your PC")
        assertTrue(paired.any { it.contains("only when you ask") })
        assertTrue(paired.any { it.contains("approved there") })
    }

    @Test
    fun pageAlwaysStatesNoTracking() {
        val text = PrivacySummary.asText(facts())
        assertTrue(text.contains("no analytics, ads or crash reporting"))
        assertEquals(
            listOf("Where your messages go", "What stays on this phone", "Your PC", "Microphone", "No tracking"),
            PrivacySummary.sections(facts()).map { it.title }
        )
    }

    @Test
    fun microphoneSectionSaysAudioIsNeverSaved() {
        val mic = lines(facts(), "Microphone")
        assertTrue(mic.any { it.contains("only while the recording bar or voice chat is on screen") })
        assertTrue(mic.any { it.contains("never saves the audio") })
    }
}
