package com.hydrogen.ai.data

import com.hydrogen.ai.model.ProviderPresets
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import java.net.URI
import java.security.MessageDigest

/**
 * Stable, non-secret binding for a *specific* API service and base path.
 * Routes alone are shared by unrelated hosts; they must never identify a credential.
 * No normalization changes the endpoint actually used by network code.
 */
object CredentialBinding {
    fun forConfig(config: RelayConfig): String? {
        if (config.route == ProviderRoute.LOCAL_OFFLINE) return null
        val url = ProviderPresets.normalizeBaseUrl(config.effectiveBaseUrl)
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host.isBlank() || scheme !in setOf("http", "https")) return null
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        // A credential must never be persisted for an unencrypted LAN or internet endpoint.
        if (scheme == "http" && host !in setOf("localhost", "127.0.0.1", "::1", "10.0.2.2")) return null
        val origin = ProviderPresets.originOf(url) ?: return null
        val path = uri.normalize().rawPath.orEmpty().trimEnd('/')
        return "${config.route.name}|$origin|$path"
    }

    fun preferenceKey(binding: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(binding.toByteArray(Charsets.UTF_8))
        return "enc_scope_" + digest.joinToString("") { "%02x".format(it) }
    }
}
