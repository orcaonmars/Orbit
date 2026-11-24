package com.hydrogen.ai.model

/** Where a provider card is listed in "Models & API keys". */
enum class PresetGroup(val label: String) {
    HOSTED("Online services"),
    PC("On your PC"),
    OTHER("Custom providers")
}

/**
 * User-facing provider choices for the "Models & API keys" sheet. Each preset maps onto a [ProviderRoute].
 *
 * Hosted services ([PresetGroup.HOSTED]) use the service's own official https address, which the user can't edit, so a
 * key typed into the OpenAI card can only ever go to OpenAI. Any other service is reached through [CUSTOM] (OpenAI
 * format) or [CUSTOM_ANTHROPIC] (Anthropic format) with a user-entered address. Addresses and key pages below are the
 * services' public, documented API endpoints; nothing here is a paid default or a fallback.
 */
enum class ProviderPreset(
    val title: String,
    val subtitle: String,
    val route: ProviderRoute,
    private val baseUrlProvider: () -> String,
    val defaultModelId: String,
    val keyLabel: String,
    val keyRequired: Boolean,
    val showsBaseUrl: Boolean,
    val group: PresetGroup = PresetGroup.HOSTED,
    /** The service's page for creating an API key, opened from the sheet. Null when there is no stable one. */
    val keyPage: String? = null,
    /** How this service's keys usually start. Only used to warn about a key pasted into the wrong card. */
    val keyPrefixes: List<String> = emptyList(),
    /** Model names offered as one-tap choices before the list is loaded. */
    val suggestedModels: List<String> = emptyList()
) {
    GOOGLE_GEMINI(
        title = "Google Gemini",
        subtitle = "Gemini models with your own AI Studio key",
        route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT,
        baseUrlProvider = { HydrogenDefaults.GOOGLE_AI_STUDIO_BASE_URL },
        defaultModelId = HydrogenDefaults.GOOGLE_AI_STUDIO_DEFAULT_MODEL,
        keyLabel = "Gemini API key",
        keyRequired = true,
        showsBaseUrl = false,
        keyPage = "https://aistudio.google.com/apikey"
    ),
    ANTHROPIC(
        title = "Anthropic",
        subtitle = "Claude models with your own API key",
        route = ProviderRoute.ANTHROPIC_MESSAGES,
        baseUrlProvider = { HydrogenDefaults.ANTHROPIC_BASE_URL },
        defaultModelId = "claude-opus-5-5",
        keyLabel = "Anthropic API key",
        keyRequired = true,
        showsBaseUrl = false,
        keyPage = "https://platform.claude.com/settings/keys",
        keyPrefixes = listOf("sk-ant-"),
        suggestedModels = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5", "claude-fable-5-2")
    ),
    OPENAI(
        title = "OpenAI",
        subtitle = "GPT models with your own API key",
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrlProvider = { "https://api.openai.com" },
        defaultModelId = "",
        keyLabel = "OpenAI API key",
        keyRequired = true,
        showsBaseUrl = false,
        keyPage = "https://platform.openai.com/api-keys",
        keyPrefixes = listOf("sk-proj-", "sk-svcacct-", "sk-")
    ),
    OPENROUTER(
        title = "OpenRouter",
        subtitle = "Free and paid models; :free variants need no prepaid credits",
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrlProvider = { "https://openrouter.ai/api" },
        defaultModelId = "openrouter/free",
        keyLabel = "OpenRouter API key",
        keyRequired = true,
        showsBaseUrl = false,
        keyPage = "https://openrouter.ai/keys",
        keyPrefixes = listOf("sk-or-"),
        suggestedModels = listOf("openrouter/free")
    ),
    CLEAN_APIS(
        title = "Clean APIs",
        subtitle = "CleanAPI: Claude, GPT and more with one key",
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrlProvider = { "https://cleanapis.com" },
        defaultModelId = "",
        keyLabel = "Clean APIs API key",
        keyRequired = true,
        showsBaseUrl = false,
        keyPage = "https://cleanapis.com",
        keyPrefixes = listOf("cc_")
    ),
    GROQ(
        title = "Groq",
        subtitle = "Very fast open models",
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrlProvider = { "https://api.groq.com/openai" },
        defaultModelId = "",
        keyLabel = "Groq API key",
        keyRequired = true,
        showsBaseUrl = false,
        keyPage = "https://console.groq.com/keys",
        keyPrefixes = listOf("gsk_")
    ),
    MISTRAL(
        title = "Mistral",
        subtitle = "Mistral models with your own API key",
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrlProvider = { "https://api.mistral.ai" },
        defaultModelId = "",
        keyLabel = "Mistral API key",
        keyRequired = true,
        showsBaseUrl = false,
        keyPage = "https://console.mistral.ai/api-keys"
    ),
    DEEPSEEK(
        title = "DeepSeek",
        subtitle = "DeepSeek models with your own API key",
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrlProvider = { "https://api.deepseek.com" },
        defaultModelId = "",
        keyLabel = "DeepSeek API key",
        keyRequired = true,
        showsBaseUrl = false,
        keyPage = "https://platform.deepseek.com/api_keys",
        keyPrefixes = listOf("sk-")
    ),
    XAI(
        title = "xAI",
        subtitle = "Grok models with your own API key",
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrlProvider = { "https://api.x.ai" },
        defaultModelId = "",
        keyLabel = "xAI API key",
        keyRequired = true,
        showsBaseUrl = false,
        keyPage = "https://console.x.ai",
        keyPrefixes = listOf("xai-")
    ),
    TOGETHER(
        title = "Together AI",
        subtitle = "Open models, hosted for you",
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrlProvider = { "https://api.together.xyz" },
        defaultModelId = "",
        keyLabel = "Together API key",
        keyRequired = true,
        showsBaseUrl = false
    ),
    FIREWORKS(
        title = "Fireworks AI",
        subtitle = "Open models, hosted for you",
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrlProvider = { "https://api.fireworks.ai/inference" },
        defaultModelId = "",
        keyLabel = "Fireworks API key",
        keyRequired = true,
        showsBaseUrl = false,
        keyPage = "https://fireworks.ai/account/api-keys",
        keyPrefixes = listOf("fw_")
    ),
    CEREBRAS(
        title = "Cerebras",
        subtitle = "Very fast open models",
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrlProvider = { "https://api.cerebras.ai" },
        defaultModelId = "",
        keyLabel = "Cerebras API key",
        keyRequired = true,
        showsBaseUrl = false,
        keyPage = "https://cloud.cerebras.ai",
        keyPrefixes = listOf("csk-")
    ),
    HUGGING_FACE(
        title = "Hugging Face",
        subtitle = "Models from many providers with one token",
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrlProvider = { "https://router.huggingface.co" },
        defaultModelId = "",
        keyLabel = "Hugging Face token",
        keyRequired = true,
        showsBaseUrl = false,
        keyPage = "https://huggingface.co/settings/tokens",
        keyPrefixes = listOf("hf_")
    ),
    NVIDIA(
        title = "NVIDIA",
        subtitle = "Models hosted by NVIDIA",
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrlProvider = { "https://integrate.api.nvidia.com" },
        defaultModelId = "",
        keyLabel = "NVIDIA API key",
        keyRequired = true,
        showsBaseUrl = false,
        keyPage = "https://build.nvidia.com",
        keyPrefixes = listOf("nvapi-")
    ),
    MOONSHOT(
        title = "Moonshot AI",
        subtitle = "Kimi models with your own API key",
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrlProvider = { "https://api.moonshot.ai" },
        defaultModelId = "",
        keyLabel = "Moonshot API key",
        keyRequired = true,
        showsBaseUrl = false,
        keyPage = "https://platform.moonshot.ai"
    ),
    QWEN(
        title = "Qwen",
        subtitle = "Qwen models on Alibaba Cloud",
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrlProvider = { "https://dashscope-intl.aliyuncs.com/compatible-mode" },
        defaultModelId = "",
        keyLabel = "Alibaba Cloud API key",
        keyRequired = true,
        showsBaseUrl = false
    ),
    SAMBANOVA(
        title = "SambaNova",
        subtitle = "Fast open models",
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrlProvider = { "https://api.sambanova.ai" },
        defaultModelId = "",
        keyLabel = "SambaNova API key",
        keyRequired = true,
        showsBaseUrl = false,
        keyPage = "https://cloud.sambanova.ai"
    ),
    COHERE(
        title = "Cohere",
        subtitle = "Command models with your own API key",
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrlProvider = { "https://api.cohere.ai/compatibility" },
        defaultModelId = "",
        keyLabel = "Cohere API key",
        keyRequired = true,
        showsBaseUrl = false,
        keyPage = "https://dashboard.cohere.com/api-keys"
    ),
    LM_STUDIO(
        title = "LM Studio",
        subtitle = "Runs on your PC, no key needed",
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrlProvider = { LocalServerPresets.pcUrl(1234) },
        defaultModelId = "",
        keyLabel = "API key (optional)",
        keyRequired = false,
        showsBaseUrl = true,
        group = PresetGroup.PC
    ),
    OLLAMA(
        title = "Ollama",
        subtitle = "Runs on your PC, no key needed",
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrlProvider = { LocalServerPresets.pcUrl(11434) },
        defaultModelId = "",
        keyLabel = "API key (optional)",
        keyRequired = false,
        showsBaseUrl = true,
        group = PresetGroup.PC
    ),
    HYDROGEN_RELAY(
        title = "Self-hosted relay",
        subtitle = "Your own relay server and API key",
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrlProvider = { LocalServerPresets.pcUrl(ProviderPresets.HYDROGEN_RELAY_PORT_NUMBER) },
        defaultModelId = HydrogenDefaults.PRIMARY_MODEL_ID,
        keyLabel = "Relay API key",
        keyRequired = true,
        showsBaseUrl = true,
        group = PresetGroup.PC
    ),
    CUSTOM(
        title = "Another service",
        subtitle = "Any OpenAI-compatible API, with its address and your key",
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrlProvider = { "" },
        defaultModelId = "",
        keyLabel = "API key",
        keyRequired = true,
        showsBaseUrl = true,
        group = PresetGroup.OTHER
    ),
    CUSTOM_ANTHROPIC(
        title = "Another service, Anthropic format",
        subtitle = "Any API that speaks the Anthropic Messages format",
        route = ProviderRoute.ANTHROPIC_MESSAGES,
        baseUrlProvider = { "" },
        defaultModelId = "",
        keyLabel = "API key",
        keyRequired = true,
        showsBaseUrl = true,
        group = PresetGroup.OTHER
    );

    /** Resolved when read, so local presets point at the emulator host or, on a real phone, loopback. */
    val defaultBaseUrl: String get() = baseUrlProvider()

    /** A hosted service with a fixed official address (the user never edits it). */
    val isHosted: Boolean get() = group == PresetGroup.HOSTED

    /** The host name shown under a hosted card ("Sends to api.openai.com"). */
    val hostLabel: String get() = runCatching { java.net.URI(defaultBaseUrl).host }.getOrNull().orEmpty()
}

object ProviderPresets {
    /** The free router and :free variants don't need prepaid OpenRouter credits. */
    fun isOpenRouterFreeModel(modelId: String): Boolean {
        val id = modelId.trim()
        return id == "openrouter/free" || id.endsWith(":free")
    }

    /** Make a safe zero-credit choice visible even when model listing includes paid models. */
    fun openRouterModels(modelIds: List<String>, freeOnly: Boolean): List<String> =
        if (freeOnly) {
            (ProviderPreset.OPENROUTER.suggestedModels + modelIds.filter { isOpenRouterFreeModel(it) })
                .distinct()
        } else {
            modelIds
        }

    const val HYDROGEN_RELAY_EMULATOR_URL = "http://10.0.2.2:18757"
    const val HYDROGEN_RELAY_PORT_NUMBER = 18757
    private const val HYDROGEN_RELAY_PORT = ":18757"

    /** Hosted cards shown before "More services". The rest of [PresetGroup.HOSTED] follows behind the expander. */
    val FEATURED_HOSTED: List<ProviderPreset> = listOf(
        ProviderPreset.GOOGLE_GEMINI,
        ProviderPreset.ANTHROPIC,
        ProviderPreset.OPENAI,
        ProviderPreset.OPENROUTER,
        ProviderPreset.CLEAN_APIS,
        ProviderPreset.GROQ,
        ProviderPreset.MISTRAL
    )

    fun inGroup(group: PresetGroup): List<ProviderPreset> = ProviderPreset.entries.filter { it.group == group }

    /** Routes whose key belongs to one server address; Google's key has its own slot and only goes to Google. */
    private val ADDRESS_KEYED_ROUTES = setOf(ProviderRoute.GEMINI_HYDROGEN_RELAY, ProviderRoute.ANTHROPIC_MESSAGES)

    fun usesAddressKeys(route: ProviderRoute): Boolean = route in ADDRESS_KEYED_ROUTES

    /** Best guess at which preset produced a saved config, so the sheet reopens on the right card. */
    fun detect(config: RelayConfig): ProviderPreset {
        if (config.route == ProviderRoute.GOOGLE_AI_STUDIO_DIRECT) return ProviderPreset.GOOGLE_GEMINI
        if (config.route == ProviderRoute.ANTHROPIC_MESSAGES) {
            val origin = originOf(config.effectiveBaseUrl)
            return if (origin == null || origin == originOf(HydrogenDefaults.ANTHROPIC_BASE_URL)) {
                ProviderPreset.ANTHROPIC
            } else {
                ProviderPreset.CUSTOM_ANTHROPIC
            }
        }
        val url = normalizeBaseUrl(config.baseUrl).lowercase()
        if (url.isEmpty()) return ProviderPreset.HYDROGEN_RELAY
        hostedPresetFor(url)?.let { return it }
        when (LocalServerPresets.matchingPreset(url)?.label) {
            "LM Studio" -> return ProviderPreset.LM_STUDIO
            "Ollama" -> return ProviderPreset.OLLAMA
        }
        when (LocalServerPresets.matchingWifiPreset(url)?.label) {
            "LM Studio (Wi-Fi)" -> return ProviderPreset.LM_STUDIO
            "Ollama (Wi-Fi)" -> return ProviderPreset.OLLAMA
        }
        if (url.endsWith(HYDROGEN_RELAY_PORT) || config.modelId.trim() == HydrogenDefaults.PRIMARY_MODEL_ID) {
            return ProviderPreset.HYDROGEN_RELAY
        }
        return ProviderPreset.CUSTOM
    }

    /** The OpenAI-format hosted card whose official address is exactly [baseUrl] (a pasted ".../v1" counts). */
    fun hostedPresetFor(baseUrl: String): ProviderPreset? {
        val url = normalizeBaseUrl(baseUrl).lowercase()
        if (url.isEmpty()) return null
        return ProviderPreset.entries.firstOrNull {
            it.isHosted && it.route == ProviderRoute.GEMINI_HYDROGEN_RELAY && it.defaultBaseUrl.lowercase() == url
        }
    }

    /**
     * The service's name for messages ("OpenAI didn't accept your API key"), matched on the address's host so a key
     * error from api.openai.com names OpenAI even when it was typed into "Another service". Null for the user's own
     * servers and unknown hosts.
     */
    fun serviceNameFor(route: ProviderRoute, baseUrl: String): String? {
        if (route == ProviderRoute.GOOGLE_AI_STUDIO_DIRECT) return "Google"
        val origin = originOf(if (route == ProviderRoute.ANTHROPIC_MESSAGES) {
            baseUrl.ifBlank { HydrogenDefaults.ANTHROPIC_BASE_URL }
        } else {
            baseUrl
        }) ?: return null
        return ProviderPreset.entries.firstOrNull { it.isHosted && originOf(it.defaultBaseUrl) == origin }?.title
    }

    /**
     * The relay client appends "/v1/chat/completions" itself, so a pasted ".../v1" base (the form most
     * OpenAI-compatible providers document) is trimmed to avoid "/v1/v1". Google's URL is never touched.
     */
    fun normalizeBaseUrl(raw: String): String {
        var url = raw.trim().trimEnd('/')
        // A full endpoint copied from the service's docs (".../v1/chat/completions") works as its base.
        for (endpoint in listOf("/chat/completions", "/completions", "/messages", "/models")) {
            if (url.lowercase().endsWith(endpoint)) {
                url = url.dropLast(endpoint.length).trimEnd('/')
                break
            }
        }
        if (url.lowercase().endsWith("/v1")) url = url.dropLast(3).trimEnd('/')
        return url
    }

    /**
     * The full address for an OpenAI-compatible call such as "chat/completions" or "models". Most services sit at
     * "{base}/v1/...", but some name their own version in the address (".../v1beta/openai", ".../api/paas/v4"),
     * and those take the path straight after it.
     */
    fun openAiEndpoint(baseUrl: String, path: String): String {
        val base = baseUrl.trim().trimEnd('/')
        val basePath = runCatching { java.net.URI(base).path }.getOrNull().orEmpty()
        val namesVersion = basePath.split('/').any { API_VERSION_SEGMENT.matches(it) }
        return if (namesVersion) "$base/$path" else "$base/v1/$path"
    }

    private val API_VERSION_SEGMENT = Regex("v\\d+(?:[a-z]+\\d*)?", RegexOption.IGNORE_CASE)

    /**
     * Every key in a paste: one per line, or split by spaces, commas or semicolons, each cleaned like [cleanKey].
     * "Bearer" or "x-api-key:" picked up from a curl example is dropped. When a paste holds more than one key,
     * bits shorter than 8 characters are not keys and are dropped too.
     */
    fun splitPastedKeys(raw: String): List<String> {
        val parts = raw.split(Regex("[\\s,;]+"))
            .map { cleanKey(it) }
            .filter { it.isNotEmpty() && it.lowercase().trimEnd(':') !in KEY_HEADER_WORDS }
            .distinct()
        return if (parts.size > 1) parts.filter { it.length >= 8 } else parts
    }

    private val KEY_HEADER_WORDS = setOf("bearer", "authorization", "x-api-key", "api-key", "key")

    /**
     * True when the key in the route's slot must not be reused, because the server it was entered for changed
     * (different scheme, host or port) or the route itself changed. Stops one service's key from being sent to
     * another. Coming back from another route (Google), the slot's server is no longer known, so it is not reused:
     * otherwise server A's key would reach server B after a detour. The caller then loads the key saved for the new
     * address, if any (see [originOf]).
     */
    fun keyBelongsToOtherServer(previous: RelayConfig, newRoute: ProviderRoute, newBaseUrl: String): Boolean {
        if (newRoute !in ADDRESS_KEYED_ROUTES) return false
        if (previous.route != newRoute) return true
        val before = originOf(previous.effectiveBaseUrl)
        val after = originOf(
            if (newRoute == ProviderRoute.ANTHROPIC_MESSAGES) newBaseUrl.ifBlank { HydrogenDefaults.ANTHROPIC_BASE_URL } else newBaseUrl
        )
        return before != null && before != after
    }

    /** "scheme://host:port" for a server address, or null when it can't be parsed. Keys are saved per origin. */
    fun originOf(url: String): String? {
        val parsed = runCatching { java.net.URI(normalizeBaseUrl(url)) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase() ?: return null
        val host = parsed.host?.lowercase() ?: return null
        val port = if (parsed.port != -1) parsed.port else if (scheme == "https") 443 else 80
        return "$scheme://$host:$port"
    }

    /**
     * A pasted key cleaned of what usually comes along by accident: spaces and line breaks, wrapping quotes, and a
     * leading "Bearer " or "x-api-key:" copied from a curl example.
     */
    fun cleanKey(raw: String): String {
        var key = raw.trim().trim('"', '\'', '`').trim()
        for (prefix in listOf("bearer ", "authorization:", "x-api-key:")) {
            if (key.lowercase().startsWith(prefix)) key = key.substring(prefix.length).trim()
        }
        if (key.lowercase().startsWith("bearer ")) key = key.substring(7).trim()
        return key.filterNot { it.isWhitespace() }.trim('"', '\'', '`')
    }

    /**
     * A gentle warning when a key looks like it belongs to another service (an Anthropic key in the OpenAI card).
     * Never blocks saving: key formats change, and many services have no fixed prefix.
     */
    fun keyFormatHint(preset: ProviderPreset, rawKey: String): String? {
        val key = cleanKey(rawKey)
        if (key.length < 8) return null
        val matches = ProviderPreset.entries.flatMap { p ->
            p.keyPrefixes.filter { key.startsWith(it) }.map { prefix -> p to prefix.length }
        }
        val best = matches.maxOfOrNull { it.second } ?: return null
        val owners = matches.filter { it.second == best }.map { it.first }
        if (preset in owners) return null
        val owner = owners.singleOrNull() ?: return null
        return "This looks like a key for ${owner.title}. Check that you picked the right service."
    }

    /** Null when the draft can be saved, otherwise a short reason shown under the form. */
    fun validate(preset: ProviderPreset, baseUrl: String, modelId: String, requireModel: Boolean = true): String? {
        if (preset.showsBaseUrl) {
            val url = normalizeBaseUrl(baseUrl)
            if (url.isEmpty()) return "Enter the server URL."
            val lower = url.lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                return "The URL must start with https:// (or http:// for a server on this PC or your home Wi-Fi)."
            }
            if (lower.startsWith("http://") && !LocalServerPresets.isPrivateServer(url)) {
                return "Use https:// for servers that are not on this PC or your home Wi-Fi."
            }
        }
        if (requireModel && modelId.isBlank()) {
            return if (preset == ProviderPreset.GOOGLE_GEMINI) "Enter a model name." else "Pick a model (tap Load models) or type its name."
        }
        return null
    }
}
