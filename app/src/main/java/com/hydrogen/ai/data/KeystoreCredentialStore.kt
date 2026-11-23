package com.hydrogen.ai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreCredentialStore(context: Context) : RelayCredentialStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)


    override fun getToken(config: RelayConfig): String? {
        val binding = CredentialBinding.forConfig(config) ?: return null
        val packed = prefs.getString(CredentialBinding.preferenceKey(binding), null)
        if (packed != null) return decryptScoped(packed, binding)

        // Only Google had a unique, immutable origin in the previous route-only storage.
        // OpenAI-compatible and Anthropic route keys may have belonged to *any* host.
        if (config.route == ProviderRoute.GOOGLE_AI_STUDIO_DIRECT) {
            val legacy = getToken(config.route) ?: return null
            setToken(config, legacy)
            setToken(config.route, null)
            return legacy
        }
        return null
    }

    override fun setToken(config: RelayConfig, token: String?) {
        val binding = CredentialBinding.forConfig(config) ?: return
        val key = CredentialBinding.preferenceKey(binding)
        val clean = token?.trim().orEmpty()
        if (clean.isEmpty()) {
            prefs.edit().remove(key).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        cipher.updateAAD(binding.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(
            key,
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
        ).apply()
    }

    override fun getBackupTokens(config: RelayConfig): List<String> {
        val binding = CredentialBinding.forConfig(config) ?: return emptyList()
        val scope = binding + BACKUP_SUFFIX
        val packed = prefs.getString(CredentialBinding.preferenceKey(scope), null) ?: return emptyList()
        return decryptScoped(packed, scope)?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    }

    override fun setBackupTokens(config: RelayConfig, tokens: List<String>) {
        val binding = CredentialBinding.forConfig(config) ?: return
        val scope = binding + BACKUP_SUFFIX
        val key = CredentialBinding.preferenceKey(scope)
        val clean = cleanBackupTokens(tokens)
        if (clean.isEmpty()) {
            prefs.edit().remove(key).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        // Bound to this one service, like the main key, and kept apart from it.
        cipher.updateAAD(scope.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(clean.joinToString("\n").toByteArray(Charsets.UTF_8))
        prefs.edit().putString(
            key,
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
        ).apply()
    }

    private fun decryptScoped(packed: String, binding: String): String? = try {
        val parts = packed.split(':', limit = 2)
        if (parts.size != 2) null else {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val data = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(binding.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(data), Charsets.UTF_8).trim().ifBlank { null }
        }
    } catch (_: Exception) {
        null
    }

    override fun getToken(route: ProviderRoute): String? {
        val packed = prefs.getString(keyFor(route), null) ?: return null
        return try {
            val parts = packed.split(':', limit = 2)
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8).trim().ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    override fun setToken(route: ProviderRoute, token: String?) {
        val clean = token?.trim().orEmpty()
        if (clean.isEmpty()) {
            prefs.edit().remove(keyFor(route)).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val dataB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString(keyFor(route), "$ivB64:$dataB64").apply()
    }

    override fun clearPersistedTokens() {
        prefs.edit().clear().apply()
    }

    private fun keyFor(route: ProviderRoute): String = "enc_token_${route.name}"

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "hydrogen_credential_aes_key"
        private const val PREFS_NAME = "hydrogen_encrypted_credentials"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val BACKUP_SUFFIX = "|backup-keys"
    }
}
