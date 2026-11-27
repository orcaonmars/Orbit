package com.hydrogen.ai.model

/**
 * How many images one request may carry. Each provider documents its own cap, and going over it fails the whole
 * request, so Orbit sends at most this many and combines any extra photos into numbered grid images.
 */
object ImageLimits {

    /** The saved setting for "use the provider's own limit". */
    const val AUTOMATIC = 0

    /** The highest number the setting accepts. */
    const val MAX_SETTING = 100

    /**
     * The documented (or a safe) image count for the current service and model. Large providers get 20, which also
     * keeps every request well under their request-size limits once photos are shrunk for sending.
     */
    fun providerCap(config: RelayConfig): Int {
        if (config.route == ProviderRoute.LOCAL_OFFLINE) return 0
        if (config.route == ProviderRoute.GOOGLE_AI_STUDIO_DIRECT) return LARGE
        val host = hostOf(config.effectiveBaseUrl)
        val model = config.modelId.trim().lowercase()
        if (config.route == ProviderRoute.ANTHROPIC_MESSAGES) {
            return if (host.isEmpty() || host == "anthropic.com" || host.endsWith(".anthropic.com")) LARGE
            else HydrogenDefaults.DEFAULT_MAX_IMAGES_PER_REQUEST
        }
        return when {
            host.isOn("openai.com") -> LARGE
            host.isOn("anthropic.com") -> LARGE
            host.isOn("googleapis.com") -> LARGE
            // Mistral documents 8 images per request for its vision models.
            host.isOn("mistral.ai") -> 8
            // Groq documents 5 images per request for its vision models.
            host.isOn("groq.com") -> 5
            host.isOn("openrouter.ai") -> openRouterCap(model)
            else -> HydrogenDefaults.DEFAULT_MAX_IMAGES_PER_REQUEST
        }
    }

    /** The count Orbit actually uses: the owner's own number when they set one, otherwise the provider's. */
    fun effectiveCap(config: RelayConfig): Int =
        if (config.maxImagesPerRequest > 0) config.maxImagesPerRequest.coerceAtMost(MAX_SETTING)
        else providerCap(config)

    /** OpenRouter passes images to the model's own provider, so its cap follows the model family. */
    private fun openRouterCap(model: String): Int = when {
        model.startsWith("anthropic/") || model.startsWith("google/") || model.startsWith("openai/") -> LARGE
        model.startsWith("mistralai/") -> 8
        else -> HydrogenDefaults.DEFAULT_MAX_IMAGES_PER_REQUEST
    }

    private const val LARGE = 20

    /** The domain itself or a subdomain of it, so "notopenai.com" doesn't count as OpenAI. */
    private fun String.isOn(domain: String): Boolean = this == domain || endsWith(".$domain")

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url.trim()).host }.getOrNull().orEmpty().lowercase()
}
