package com.hydrogen.ai.ui

import com.hydrogen.ai.data.ConversationStore
import com.hydrogen.ai.data.InMemoryRelayCredentialStore
import com.hydrogen.ai.data.RelayConfigStore
import com.hydrogen.ai.model.Conversation
import com.hydrogen.ai.model.HydrogenDefaults
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import com.hydrogen.ai.model.SavedConnection
import com.hydrogen.ai.model.SavedConnections
import com.hydrogen.ai.network.ChatBackend
import com.hydrogen.ai.network.ChatDispatchRequest
import com.hydrogen.ai.network.ChatDispatchResult
import com.hydrogen.ai.network.RelayHealthResult
import com.hydrogen.ai.network.StreamDelta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Deleting a model or a whole provider from the model switcher, and how providers are ordered there. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderDeleteTest {
    private val studio = "http://10.0.2.2:1234/v1"
    private val other = "http://192.168.1.20:11434/v1"

    private fun relay(url: String, model: String, pinned: Boolean = false) =
        SavedConnection(ProviderRoute.GEMINI_HYDROGEN_RELAY, url, model, pinned = pinned)

    private fun google(model: String, pinned: Boolean = false) = SavedConnection(
        ProviderRoute.GOOGLE_AI_STUDIO_DIRECT, HydrogenDefaults.GOOGLE_AI_STUDIO_BASE_URL, model, pinned = pinned
    )

    @Test fun sameServerIgnoresModelTrailingSlashAndCaseButNotRoute() {
        assertTrue(SavedConnections.sameServer(relay(studio, "a"), relay("HTTP://10.0.2.2:1234/v1/", "b")))
        assertFalse(SavedConnections.sameServer(relay(studio, "a"), relay(other, "a")))
        assertFalse(SavedConnections.sameServer(relay(HydrogenDefaults.GOOGLE_AI_STUDIO_BASE_URL, "a"), google("a")))
        // Web search on or off, Google direct is one service with one key.
        assertTrue(SavedConnections.sameServer(google("a"), google("b").copy(webSearchEnabled = true)))
    }

    @Test fun removeServerDropsEveryModelOnThatProviderOnly() {
        val list = listOf(relay(studio, "a"), relay(other, "x"), relay(studio, "b"), google("g"))
        val left = SavedConnections.removeServer(list, relay(studio, "anything"))
        assertEquals(listOf("x", "g"), left.map { it.modelId })
    }

    @Test fun replacementPrefersAFavoriteOnTheSameProvider() {
        val list = listOf(relay(studio, "a"), relay(other, "x", pinned = true), relay(studio, "b"), relay(studio, "c", pinned = true))
        assertEquals("c", SavedConnections.replacementFor(list, listOf(list[0]))?.modelId)
    }

    @Test fun replacementThenTakesTheMostRecentOnTheSameProvider() {
        val list = listOf(relay(studio, "a"), relay(other, "x", pinned = true), relay(studio, "b"), relay(studio, "c"))
        assertEquals("b", SavedConnections.replacementFor(list, listOf(list[0]))?.modelId)
    }

    @Test fun replacementForAWholeProviderPrefersAFavoriteElsewhereThenTheMostRecent() {
        val list = listOf(relay(studio, "a"), relay(other, "x"), google("g", pinned = true), relay(studio, "b"))
        val studioModels = list.filter { SavedConnections.sameServer(it, list[0]) }
        assertEquals("g", SavedConnections.replacementFor(list, studioModels)?.modelId)
        val noFavorites = list.map { it.copy(pinned = false) }
        assertEquals("x", SavedConnections.replacementFor(noFavorites, studioModels)?.modelId)
    }

    @Test fun nothingToSwitchToWhenEverythingWouldBeGone() {
        val list = listOf(relay(studio, "a"), relay(studio, "b"))
        assertNull(SavedConnections.replacementFor(list, list))
        assertNull(SavedConnections.replacementFor(listOf(list[0]), listOf(list[0])))
    }

    @Test fun favoritesComeFirstAndKeepMostRecentOrder() {
        val list = listOf(relay(studio, "a"), relay(studio, "b", pinned = true), relay(studio, "c"), relay(studio, "d", pinned = true))
        assertEquals(listOf("b", "d", "a", "c"), SavedConnections.favoritesFirst(list).map { it.modelId })
    }

    @Test fun theProviderInUseIsListedFirst() {
        val list = listOf(relay(other, "x"), relay(studio, "a"), google("g"))
        val current = RelayConfig(route = ProviderRoute.GEMINI_HYDROGEN_RELAY, baseUrl = studio, modelId = "a")
        val ordered = orderProviders(groupByServer(list), current)
        assertEquals(listOf("a", "x", "g"), ordered.map { it.second.first().modelId })
        // Nothing in use: the most recent order stays.
        val none = orderProviders(groupByServer(list), RelayConfig(route = ProviderRoute.LOCAL_OFFLINE))
        assertEquals(listOf("x", "a", "g"), none.map { it.second.first().modelId })
    }

    @Test fun deleteTextSaysWhatHappensAndFollowsTheCopyRules() {
        val texts = listOf(
            deleteModelText("Gemma 3", inUse = false, replacement = null),
            deleteModelText("Gemma 3", inUse = true, replacement = "Qwen 2.5"),
            deleteModelText("Gemma 3", inUse = true, replacement = null),
            deleteProviderText("LM Studio, 10.0.2.2", count = 1, inUse = false, replacement = null),
            deleteProviderText("LM Studio, 10.0.2.2", count = 3, inUse = true, replacement = "Gemini 2.5 Flash"),
            deleteProviderText("LM Studio, 10.0.2.2", count = 3, inUse = true, replacement = null)
        )
        assertTrue(texts[0], texts[0].contains("chats and the saved key stay"))
        assertTrue(texts[1], texts[1].contains("switch to Qwen 2.5 first"))
        assertTrue(texts[2], texts[2].contains("Add another model first"))
        assertTrue(texts[3], texts[3].contains("its saved model and any API key saved for LM Studio, 10.0.2.2"))
        assertTrue(texts[4], texts[4].contains("its 3 saved models") && texts[4].endsWith("switch to Gemini 2.5 Flash first."))
        assertTrue(texts[5], texts[5].contains("Add a model from another provider first"))
        for (t in texts) {
            assertFalse(t, t.contains('—') || t.contains('–') || t.contains('!') || t.contains('·') || t.contains('&'))
        }
    }

    // View model: deleting a provider clears the key bound to it, never the key in use.

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(testDispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private class Conversations : ConversationStore {
        override suspend fun loadConversations(): List<Conversation> = emptyList()
        override suspend fun saveConversations(conversations: List<Conversation>) = Unit
    }

    private class Configs(private var config: RelayConfig) : RelayConfigStore {
        override fun loadConfig(): RelayConfig = config
        override fun saveConfig(config: RelayConfig) {
            this.config = config
        }
    }

    private object Unused : ChatBackend {
        override suspend fun checkHealth(config: RelayConfig, bearerToken: String?) =
            RelayHealthResult.Reachable(authenticated = true, detail = "ok")

        override suspend fun sendChat(
            request: ChatDispatchRequest,
            config: RelayConfig,
            bearerToken: String?,
            onStreamDelta: suspend (StreamDelta) -> Unit
        ): ChatDispatchResult = error("No chat is sent in these tests")
    }

    private val active = RelayConfig(route = ProviderRoute.GEMINI_HYDROGEN_RELAY, baseUrl = studio, modelId = "gemma-3")
    private val otherServer = active.copy(baseUrl = other, modelId = "llama3")

    @Test fun deletingAnotherProviderClearsItsMainAndBackupKeys() = runTest(testDispatcher) {
        val creds = InMemoryRelayCredentialStore().apply {
            setToken(active, "test-active")
            setToken(otherServer, "test-other")
            setBackupTokens(otherServer, listOf("test-other-2"))
        }
        val vm = ChatViewModel(Conversations(), Configs(active), creds, Unused) { 5_000L }
        advanceUntilIdle()
        vm.deleteProviderCredential(ProviderRoute.GEMINI_HYDROGEN_RELAY, other)
        assertNull(creds.getToken(otherServer))
        assertEquals(emptyList<String>(), creds.getBackupTokens(otherServer))
        assertEquals("test-active", creds.getToken(active))
    }

    @Test fun theProviderInUseKeepsItsKeyUntilOrbitHasSwitchedAway() = runTest(testDispatcher) {
        val creds = InMemoryRelayCredentialStore().apply {
            setToken(active, "test-active")
            setBackupTokens(active, listOf("test-active-2"))
        }
        val vm = ChatViewModel(Conversations(), Configs(active), creds, Unused) { 5_000L }
        advanceUntilIdle()
        vm.deleteProviderCredential(ProviderRoute.GEMINI_HYDROGEN_RELAY, "$studio/")
        assertEquals("test-active", creds.getToken(active))
        assertEquals(listOf("test-active-2"), creds.getBackupTokens(active))
    }
}
