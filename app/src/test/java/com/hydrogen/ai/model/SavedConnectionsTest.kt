package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedConnectionsTest {
    private fun relay(url: String, model: String) =
        RelayConfig(route = ProviderRoute.GEMINI_HYDROGEN_RELAY, baseUrl = url, modelId = model)

    private val google = RelayConfig(
        route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT,
        baseUrl = "https://evil.example.com",
        modelId = "gemini-2.5-flash",
        webSearchEnabled = true
    )

    @Test
    fun from_skips_unusable_configs_and_pins_google_to_its_official_url() {
        assertNull(SavedConnections.from(RelayConfig(route = ProviderRoute.LOCAL_OFFLINE), false, 1L))
        assertNull(SavedConnections.from(relay("", "m"), false, 1L))
        assertNull(SavedConnections.from(relay("http://10.0.2.2:1234", " "), false, 1L))
        val g = SavedConnections.from(google, hasKey = true, nowEpochMs = 5L)
        assertNotNull(g)
        assertEquals(HydrogenDefaults.GOOGLE_AI_STUDIO_BASE_URL, g!!.baseUrl)
        assertTrue(g.webSearchEnabled)
        assertTrue(g.needsKey)
        assertEquals(5L, g.lastUsedAtEpochMs)
        val lm = SavedConnections.from(relay("http://10.0.2.2:1234/v1/", " qwen "), false, 1L)!!
        assertEquals("http://10.0.2.2:1234", lm.baseUrl)
        assertEquals("qwen", lm.modelId)
        assertFalse(lm.webSearchEnabled)
    }

    @Test
    fun remember_puts_newest_first_dedupes_keeps_key_flag_and_caps() {
        val a = SavedConnections.from(relay("http://10.0.2.2:1234", "a"), hasKey = true, nowEpochMs = 1L)!!
        val b = SavedConnections.from(relay("http://10.0.2.2:11434", "b"), hasKey = false, nowEpochMs = 2L)!!
        var list = SavedConnections.remember(emptyList(), a)
        list = SavedConnections.remember(list, b)
        assertEquals(listOf("b", "a"), list.map { it.modelId })
        val aAgain = SavedConnections.from(relay("HTTP://10.0.2.2:1234/v1", "a"), hasKey = false, nowEpochMs = 3L)!!
        list = SavedConnections.remember(list, aAgain)
        assertEquals(listOf("a", "b"), list.map { it.modelId })
        assertTrue("a key was needed once, so keep asking for it", list.first().needsKey)
        assertEquals(3L, list.first().lastUsedAtEpochMs)
        repeat(SavedConnections.MAX + 8) { i ->
            list = SavedConnections.remember(list, SavedConnections.from(relay("https://h$i.example.com", "m"), false, i.toLong())!!)
        }
        assertEquals(SavedConnections.MAX, list.size)
        assertEquals("https://h${SavedConnections.MAX + 7}.example.com", list.first().baseUrl)
    }

    @Test
    fun remove_and_isCurrent_match_on_server_and_model() {
        val config = relay("http://10.0.2.2:1234", "a")
        val a = SavedConnections.from(config, false, 1L)!!
        val other = a.copy(modelId = "b")
        assertTrue(SavedConnections.isCurrent(a, config))
        assertTrue(SavedConnections.isCurrent(a.copy(needsKey = true, lastUsedAtEpochMs = 9L), config))
        assertFalse(SavedConnections.isCurrent(other, config))
        assertEquals(listOf(other), SavedConnections.remove(listOf(a, other), a))
    }

    @Test
    fun custom_profile_nickname_persists_without_changing_credential_identity() {
        val config = relay("https://api.example.com", "model-x")
        val item = SavedConnections.from(config, true, 1L)!!
        val named = SavedConnections.rename(listOf(item), item, "  Research model  ").single()
        assertEquals("Research model", named.label)
        assertEquals(item.key, named.key)
        assertTrue(named.sameTarget(item))
        val revived = SavedConnections.remember(listOf(named), item.copy(lastUsedAtEpochMs = 7L))
        assertEquals("Research model", revived.single().label)
        assertEquals(revived, SavedConnections.decode(SavedConnections.encode(revived)))
        val blank = SavedConnections.rename(revived, item, "").single()
        assertEquals("", blank.label)
        assertEquals(40, SavedConnections.rename(revived, item, "a".repeat(200)).single().label.length)
    }

    @Test
    fun server_labels_say_where_the_model_runs() {
        val g = SavedConnections.from(google, true, 1L)!!
        assertEquals("Google AI Studio, web search", SavedConnections.serverLabel(g))
        assertEquals("Google AI Studio", SavedConnections.serverLabel(g.copy(webSearchEnabled = false)))
        val lm = SavedConnections.from(relay("http://10.0.2.2:1234", "q"), false, 1L)!!
        assertEquals("LM Studio, 10.0.2.2", SavedConnections.serverLabel(lm))
        val ollama = SavedConnections.from(relay("http://10.0.2.2:11434", "q"), false, 1L)!!
        assertEquals("Ollama, 10.0.2.2", SavedConnections.serverLabel(ollama))
        val custom = SavedConnections.from(relay("https://api.example.com:8443/v1", "q"), false, 1L)!!
        assertEquals("api.example.com:8443", SavedConnections.serverLabel(custom))
    }

    @Test
    fun decode_round_trips_and_tolerates_damage() {
        val list = listOf(
            SavedConnections.from(google, true, 1L)!!,
            SavedConnections.from(relay("http://10.0.2.2:1234", "q"), false, 2L)!!
        )
        assertEquals(list, SavedConnections.decode(SavedConnections.encode(list)))
        assertEquals(emptyList<SavedConnection>(), SavedConnections.decode("not json"))
        assertEquals(emptyList<SavedConnection>(), SavedConnections.decode("{\"x\":1}"))
        val junk = """[{"route":"LOCAL_OFFLINE","baseUrl":"","modelId":"m"},
            {"route":"GEMINI_HYDROGEN_RELAY","baseUrl":"http://10.0.2.2:1234","modelId":" "},
            {"route":"GEMINI_HYDROGEN_RELAY","baseUrl":"http://10.0.2.2:1234","modelId":"ok","future":true}]"""
        assertEquals(listOf("ok"), SavedConnections.decode(junk).map { it.modelId })
    }

    @Test fun favoritesAndReasoningSurviveRefreshAndEviction() {
        val original = SavedConnections.from(relay("https://cleanapis.com", "opus").copy(reasoningMode = ReasoningMode.ULTRA), true, 1L)!!
        var list = SavedConnections.setPinned(listOf(original), original, true)
        list = SavedConnections.remember(list, original.copy(reasoningMode = ReasoningMode.HIGH, lastUsedAtEpochMs = 2L))
        assertTrue(list.single().pinned)
        assertEquals(ReasoningMode.HIGH, list.single().reasoningMode)
        repeat(SavedConnections.MAX + 5) {
            list = SavedConnections.remember(list, original.copy(modelId = "other-$it", pinned = false))
        }
        assertEquals(SavedConnections.MAX, list.size)
        assertTrue(list.any { it.modelId == "opus" && it.pinned })
        assertEquals(list, SavedConnections.decode(SavedConnections.encode(list)))
        val old = SavedConnections.decode("""[{"route":"GEMINI_HYDROGEN_RELAY","baseUrl":"https://cleanapis.com","modelId":"old"}]""").single()
        assertFalse(old.pinned)
        assertEquals(ReasoningMode.AUTO, old.reasoningMode)
    }
}
