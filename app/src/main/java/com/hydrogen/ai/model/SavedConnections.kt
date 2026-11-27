package com.hydrogen.ai.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A server + model the user has chatted with, remembered so they can switch back in one tap from the model
 * switcher. Never holds an API key: keys stay in the credential store, and a relay key is still dropped
 * whenever the server changes ([ProviderPresets.keyBelongsToOtherServer]), so switching can never send one
 * server's key to another. [needsKey] only records that a key was set, so the app can ask for it again.
 */
@Serializable
data class SavedConnection(
    val route: ProviderRoute,
    val baseUrl: String,
    val modelId: String,
    val webSearchEnabled: Boolean = false,
    val needsKey: Boolean = false,
    val lastUsedAtEpochMs: Long = 0L,
    /** A user's optional nickname for this connection. Never used for credential identity. */
    val label: String = "",
    val reasoningMode: ReasoningMode = ReasoningMode.AUTO,
    val pinned: Boolean = false
) {
    /** Same server and model, whatever the other settings. */
    fun sameTarget(other: SavedConnection): Boolean = key == other.key

    val key: String
        get() = "${route.name}|${ProviderPresets.normalizeBaseUrl(baseUrl).lowercase()}|${modelId.trim()}"
}

object SavedConnections {
    const val MAX = 60
    const val MAX_LABEL = 40
    private const val MAX_FIELD = 300

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(SavedConnection.serializer())

    /** The connection [config] describes, or null when it is not usable yet (nothing to switch back to). */
    fun from(config: RelayConfig, hasKey: Boolean, nowEpochMs: Long): SavedConnection? {
        if (config.route == ProviderRoute.LOCAL_OFFLINE || !config.isConfigured) return null
        val baseUrl = when (config.route) {
            ProviderRoute.GOOGLE_AI_STUDIO_DIRECT -> HydrogenDefaults.GOOGLE_AI_STUDIO_BASE_URL
            else -> ProviderPresets.normalizeBaseUrl(config.baseUrl)
        }
        val model = config.modelId.trim()
        if (model.isEmpty() || model.length > MAX_FIELD || baseUrl.length > MAX_FIELD) return null
        return SavedConnection(
            route = config.route,
            baseUrl = baseUrl,
            modelId = model,
            webSearchEnabled = config.route == ProviderRoute.GOOGLE_AI_STUDIO_DIRECT && config.webSearchEnabled,
            needsKey = hasKey,
            lastUsedAtEpochMs = nowEpochMs,
            reasoningMode = config.reasoningMode
        )
    }

    /** Moves [connection] to the top (most recent first), replacing an older copy, keeping at most [MAX]. */
    fun remember(list: List<SavedConnection>, connection: SavedConnection): List<SavedConnection> {
        val previous = list.firstOrNull { it.sameTarget(connection) }
        // Once a key was needed for this server, keep remembering that even if it is missing right now.
        val merged = connection.copy(
            needsKey = connection.needsKey || previous?.needsKey == true,
            label = previous?.label ?: connection.label,
            pinned = previous?.pinned ?: connection.pinned
        )
        val ordered = listOf(merged) + list.filterNot { it.sameTarget(connection) }
        val kept = (ordered.filter { it.pinned } + ordered.filterNot { it.pinned }).take(MAX).map { it.key }.toSet()
        return ordered.filter { it.key in kept }
    }

    fun setPinned(list: List<SavedConnection>, connection: SavedConnection, pinned: Boolean): List<SavedConnection> =
        list.map { if (it.sameTarget(connection)) it.copy(pinned = pinned) else it }

    fun rename(list: List<SavedConnection>, connection: SavedConnection, rawLabel: String): List<SavedConnection> {
        val clean = rawLabel.trim().take(MAX_LABEL)
        return list.map { if (it.sameTarget(connection)) it.copy(label = clean) else it }
    }

    fun remove(list: List<SavedConnection>, connection: SavedConnection): List<SavedConnection> =
        list.filterNot { it.sameTarget(connection) }

    /** Same service and endpoint, whatever the model. This is also the scope a saved key is bound to. */
    fun sameServer(a: SavedConnection, b: SavedConnection): Boolean =
        a.route == b.route && (a.route == ProviderRoute.GOOGLE_AI_STUDIO_DIRECT ||
            ProviderPresets.normalizeBaseUrl(a.baseUrl).lowercase() ==
            ProviderPresets.normalizeBaseUrl(b.baseUrl).lowercase())

    /** Every saved model on [server]'s service and endpoint is dropped. */
    fun removeServer(list: List<SavedConnection>, server: SavedConnection): List<SavedConnection> =
        list.filterNot { sameServer(it, server) }

    /**
     * What to switch to when [removing] includes the model in use: a favorite on the same service first, then the
     * most recent model on the same service, then any favorite, then the most recent model anywhere. Null when
     * nothing would be left to switch to.
     */
    fun replacementFor(list: List<SavedConnection>, removing: List<SavedConnection>): SavedConnection? {
        val gone = removing.map { it.key }.toSet()
        val left = list.filterNot { it.key in gone }
        val anchor = removing.firstOrNull()
        val sameService = if (anchor == null) emptyList() else left.filter { sameServer(it, anchor) }
        return sameService.firstOrNull { it.pinned } ?: sameService.firstOrNull()
            ?: left.firstOrNull { it.pinned } ?: left.firstOrNull()
    }

    /** Favorites first, then the rest, each keeping most recent first. */
    fun favoritesFirst(list: List<SavedConnection>): List<SavedConnection> =
        list.filter { it.pinned } + list.filterNot { it.pinned }

    /** True when [connection] is what [config] already points at. */
    fun isCurrent(connection: SavedConnection, config: RelayConfig): Boolean {
        val current = from(config, hasKey = false, nowEpochMs = 0L) ?: return false
        return current.sameTarget(connection)
    }

    /** Where the model runs, in words: "Google AI Studio", "LM Studio, 10.0.2.2", "api.example.com". */
    fun serverLabel(connection: SavedConnection): String {
        if (connection.route == ProviderRoute.GOOGLE_AI_STUDIO_DIRECT) {
            return if (connection.webSearchEnabled) "Google AI Studio, web search" else "Google AI Studio"
        }
        val url = ProviderPresets.normalizeBaseUrl(connection.baseUrl)
        val uri = runCatching { java.net.URI(url) }.getOrNull()
        val host = uri?.host ?: return url.ifEmpty { "Server" }
        val config = RelayConfig(route = connection.route, baseUrl = url, modelId = connection.modelId)
        return when (ProviderPresets.detect(config)) {
            ProviderPreset.LM_STUDIO -> "LM Studio, $host"
            ProviderPreset.OLLAMA -> "Ollama, $host"
            ProviderPreset.CLEAN_APIS -> "Clean APIs"
            ProviderPreset.HYDROGEN_RELAY -> "Relay, $host"
            else -> if (uri.port != -1) "$host:${uri.port}" else host
        }
    }

    fun encode(list: List<SavedConnection>): String = json.encodeToString(serializer, list.take(MAX))

    /** Tolerant read: a damaged or foreign file gives an empty list instead of a crash. */
    fun decode(text: String): List<SavedConnection> =
        runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyList())
            .filter { it.route != ProviderRoute.LOCAL_OFFLINE && it.modelId.isNotBlank() }
            .filter { it.modelId.length <= MAX_FIELD && it.baseUrl.length <= MAX_FIELD }
            .map { it.copy(label = it.label.trim().take(MAX_LABEL)) }
            .distinctBy { it.key }
            .take(MAX)
}
