package com.hydrogen.ai.data

import android.content.Context
import com.hydrogen.ai.model.HydrogenDefaults
import com.hydrogen.ai.model.ImageLimits
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import java.util.concurrent.ConcurrentHashMap

interface RelayConfigStore {
    fun loadConfig(): RelayConfig
    fun saveConfig(config: RelayConfig)
}

interface RelayCredentialStore {
    fun getToken(route: ProviderRoute = ProviderRoute.GEMINI_HYDROGEN_RELAY): String?
    fun setToken(route: ProviderRoute, token: String?)
    fun setToken(token: String?) = setToken(ProviderRoute.GEMINI_HYDROGEN_RELAY, token)
    fun hasToken(route: ProviderRoute = ProviderRoute.GEMINI_HYDROGEN_RELAY): Boolean = !getToken(route).isNullOrBlank()
    /** Context-aware APIs are mandatory in production; a route is not a unique service. */
    fun getToken(config: RelayConfig): String? = getToken(config.route)
    fun setToken(config: RelayConfig, token: String?) = setToken(config.route, token)
    fun hasToken(config: RelayConfig): Boolean = !getToken(config).isNullOrBlank()
    /**
     * Backup keys for the same service, tried in order when the main key hits a limit, runs out of credits or
     * stops working. Bound to one service like the main key, so they are never sent anywhere else.
     */
    fun getBackupTokens(config: RelayConfig): List<String> = emptyList()
    fun setBackupTokens(config: RelayConfig, tokens: List<String>) {}
    fun clearPersistedTokens() {}
    fun clearAllTokens() {
        ProviderRoute.entries.forEach { setToken(it, null) }
        clearPersistedTokens()
    }
}

class InMemoryRelayCredentialStore(
    initialTokens: Map<ProviderRoute, String> = emptyMap()
) : RelayCredentialStore {
    private val tokens = ConcurrentHashMap<ProviderRoute, String>()
    private val scopedTokens = ConcurrentHashMap<String, String>()
    private val backupTokens = ConcurrentHashMap<String, List<String>>()
    // Only test-injected legacy tokens can fall back to route lookup.
    private val allowLegacyFallback = initialTokens.isNotEmpty()

    init {
        initialTokens.forEach { (route, value) ->
            val clean = value.trim()
            if (clean.isNotEmpty()) tokens[route] = clean
        }
    }

    constructor(initialToken: String?) : this(
        if (initialToken.isNullOrBlank()) emptyMap()
        else mapOf(ProviderRoute.GEMINI_HYDROGEN_RELAY to initialToken)
    )

    override fun getToken(route: ProviderRoute): String? = tokens[route]

    override fun getToken(config: RelayConfig): String? =
        CredentialBinding.forConfig(config)?.let { scopedTokens[it] }
            ?: if (allowLegacyFallback) tokens[config.route] else null

    override fun setToken(config: RelayConfig, token: String?) {
        val binding = CredentialBinding.forConfig(config) ?: return
        val clean = token?.trim().orEmpty()
        if (clean.isEmpty()) scopedTokens.remove(binding) else scopedTokens[binding] = clean
    }

    override fun getBackupTokens(config: RelayConfig): List<String> =
        CredentialBinding.forConfig(config)?.let { backupTokens[it] }.orEmpty()

    override fun setBackupTokens(config: RelayConfig, tokens: List<String>) {
        val binding = CredentialBinding.forConfig(config) ?: return
        val clean = cleanBackupTokens(tokens)
        if (clean.isEmpty()) backupTokens.remove(binding) else backupTokens[binding] = clean
    }

    override fun clearAllTokens() {
        tokens.clear()
        scopedTokens.clear()
        backupTokens.clear()
    }

    override fun clearPersistedTokens() = clearAllTokens()

    override fun setToken(route: ProviderRoute, token: String?) {
        val clean = token?.trim().orEmpty()
        if (clean.isEmpty()) {
            tokens.remove(route)
        } else {
            tokens[route] = clean
        }
    }
}

class CompositeRelayCredentialStore(
    private val memoryStore: RelayCredentialStore,
    private val persistentStore: RelayCredentialStore,
    @Volatile var persistEnabled: Boolean = false
) : RelayCredentialStore {

    override fun getToken(config: RelayConfig): String? {
        return memoryStore.getToken(config) ?: if (persistEnabled) {
            persistentStore.getToken(config)?.also { memoryStore.setToken(config, it) }
        } else null
    }

    override fun setToken(config: RelayConfig, token: String?) {
        memoryStore.setToken(config, token)
        persistentStore.setToken(config, if (persistEnabled) token else null)
    }

    override fun getBackupTokens(config: RelayConfig): List<String> {
        val inMemory = memoryStore.getBackupTokens(config)
        if (inMemory.isNotEmpty() || !persistEnabled) return inMemory
        return persistentStore.getBackupTokens(config).also {
            if (it.isNotEmpty()) memoryStore.setBackupTokens(config, it)
        }
    }

    override fun setBackupTokens(config: RelayConfig, tokens: List<String>) {
        memoryStore.setBackupTokens(config, tokens)
        persistentStore.setBackupTokens(config, if (persistEnabled) tokens else emptyList())
    }

    override fun clearAllTokens() {
        memoryStore.clearAllTokens()
        persistentStore.clearPersistedTokens()
    }

    override fun getToken(route: ProviderRoute): String? {
        return memoryStore.getToken(route) ?: if (persistEnabled) {
            persistentStore.getToken(route)?.also { memoryStore.setToken(route, it) }
        } else {
            null
        }
    }

    override fun setToken(route: ProviderRoute, token: String?) {
        memoryStore.setToken(route, token)
        if (persistEnabled) {
            persistentStore.setToken(route, token)
        } else {
            persistentStore.setToken(route, null)
        }
    }

    override fun clearPersistedTokens() {
        persistentStore.clearPersistedTokens()
    }
}

class SharedPrefsRelayConfigStore(context: Context) : RelayConfigStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadConfig(): RelayConfig {
        val routeName = prefs.getString(KEY_ROUTE, ProviderRoute.GEMINI_HYDROGEN_RELAY.name)
        val route = ProviderRoute.entries.firstOrNull { it.name == routeName }
            ?: ProviderRoute.GEMINI_HYDROGEN_RELAY
        val defaultModel = when (route) {
            ProviderRoute.GEMINI_HYDROGEN_RELAY -> HydrogenDefaults.PRIMARY_MODEL_ID
            ProviderRoute.GOOGLE_AI_STUDIO_DIRECT -> HydrogenDefaults.GOOGLE_AI_STUDIO_DEFAULT_MODEL
            ProviderRoute.ANTHROPIC_MESSAGES -> "claude-opus-5-5"
            ProviderRoute.LOCAL_OFFLINE -> HydrogenDefaults.PRIMARY_MODEL_ID
        }
        val baseUrl = prefs.getString(KEY_BASE_URL, "") ?: ""
        val modelId = prefs.getString(KEY_MODEL_ID, defaultModel) ?: defaultModel
        val maxTokens = prefs.getInt(KEY_MAX_TOKENS, HydrogenDefaults.DEFAULT_CONTEXT_BUDGET_TOKENS)
        // Older builds always saved 8, their default, so a saved 8 there means "never changed": use automatic.
        val maxImages = if (prefs.contains(KEY_MAX_IMAGES_V2)) {
            prefs.getInt(KEY_MAX_IMAGES_V2, ImageLimits.AUTOMATIC)
        } else {
            prefs.getInt(KEY_MAX_IMAGES, ImageLimits.AUTOMATIC)
                .takeIf { it != HydrogenDefaults.DEFAULT_MAX_IMAGES_PER_REQUEST } ?: ImageLimits.AUTOMATIC
        }
        val streamingEnabled = prefs.getBoolean(KEY_STREAMING_ENABLED, true)
        val rememberInKeystore = prefs.getBoolean(KEY_REMEMBER_TOKEN, false)
        return RelayConfig(
            route = route,
            baseUrl = baseUrl.trim().trimEnd('/'),
            modelId = modelId.trim().ifBlank { defaultModel },
            maxContextTokensBudget = maxTokens.coerceAtLeast(4_000),
            maxImagesPerRequest = maxImages.coerceIn(0, ImageLimits.MAX_SETTING),
            streamingEnabled = streamingEnabled,
            rememberTokenInKeystore = rememberInKeystore,
            webSearchEnabled = prefs.getBoolean(KEY_WEB_SEARCH, false),
            reasoningMode = com.hydrogen.ai.model.ReasoningMode.fromStored(runCatching { prefs.getString("reasoning_mode", null) }.getOrNull()),
            openRouterReasoningModels = if (com.hydrogen.ai.model.ReasoningPolicy.isOpenRouter(baseUrl))
                runCatching { prefs.getStringSet("openrouter_reasoning_models", emptySet())?.toSet().orEmpty() }.getOrDefault(emptySet())
                else emptySet(),
            // Missing from configs saved before 0.3.5: those were all Chat Completions.
            openAiProtocol = com.hydrogen.ai.model.OpenAiProtocol.fromStored(
                runCatching { prefs.getString(KEY_OPENAI_PROTOCOL, null) }.getOrNull()
            )
        )
    }

    override fun saveConfig(config: RelayConfig) {
        prefs.edit()
            .putString(KEY_ROUTE, config.route.name)
            .putString(KEY_BASE_URL, config.baseUrl.trim().trimEnd('/'))
            .putString(KEY_MODEL_ID, config.modelId.trim())
            .putInt(KEY_MAX_TOKENS, config.maxContextTokensBudget.coerceAtLeast(4_000))
            .putInt(KEY_MAX_IMAGES_V2, config.maxImagesPerRequest.coerceIn(0, ImageLimits.MAX_SETTING))
            .putBoolean(KEY_STREAMING_ENABLED, config.streamingEnabled)
            .putBoolean(KEY_REMEMBER_TOKEN, config.rememberTokenInKeystore)
            .putBoolean(KEY_WEB_SEARCH, config.webSearchEnabled)
            .putString("reasoning_mode", config.reasoningMode.name)
            .putStringSet("openrouter_reasoning_models", config.openRouterReasoningModels.take(2000).toSet())
            .putString(KEY_OPENAI_PROTOCOL, config.openAiProtocol.name)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "hydrogen_relay_config"
        private const val KEY_ROUTE = "provider_route"
        private const val KEY_BASE_URL = "relay_base_url"
        private const val KEY_MODEL_ID = "model_id"
        private const val KEY_MAX_TOKENS = "max_context_tokens"
        private const val KEY_MAX_IMAGES = "max_images_per_request"
        private const val KEY_MAX_IMAGES_V2 = "max_images_per_request_v2"
        private const val KEY_WEB_SEARCH = "web_search_enabled"
        private const val KEY_STREAMING_ENABLED ="streaming_enabled"
        private const val KEY_REMEMBER_TOKEN = "remember_token_keystore"
        private const val KEY_OPENAI_PROTOCOL = "openai_protocol"
    }
}

/** Up to this many backup keys per service. */
const val MAX_BACKUP_KEYS = 9

/** Trims, drops blanks and repeats, keeps the order the owner chose. Keys never contain spaces or line breaks. */
fun cleanBackupTokens(tokens: List<String>, mainKey: String? = null): List<String> =
    tokens.asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.none(Char::isWhitespace) && it != mainKey?.trim() }
        .distinct()
        .take(MAX_BACKUP_KEYS)
        .toList()

fun maskSecretToken(token: String?): String {
    val clean = token?.trim().orEmpty()
    if (clean.isEmpty()) return "Not set"
    if (clean.length <= 6) return "••••"
    return "••••" + clean.takeLast(4)
}

