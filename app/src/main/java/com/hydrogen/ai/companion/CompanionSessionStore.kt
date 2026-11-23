package com.hydrogen.ai.companion

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface CompanionSessionStore {
    fun loadConfig(): CompanionConfig
    fun saveConfig(config: CompanionConfig)
    fun loadSession(): CompanionSession?
    fun saveSession(session: CompanionSession)
    fun clearSession()
}

class InMemoryCompanionSessionStore(
    initialConfig: CompanionConfig = CompanionConfig(),
    initialSession: CompanionSession? = null
) : CompanionSessionStore {
    @Volatile
    private var config: CompanionConfig = initialConfig

    @Volatile
    private var session: CompanionSession? = initialSession

    override fun loadConfig(): CompanionConfig = config

    override fun saveConfig(config: CompanionConfig) {
        this.config = config.copy(
            baseUrl = config.baseUrl.trim().trimEnd('/'),
            deviceName = config.deviceName.trim().ifBlank { CompanionDefaults.DEFAULT_DEVICE_NAME }
        )
    }

    override fun loadSession(): CompanionSession? = session

    override fun saveSession(session: CompanionSession) {
        this.session = session
    }

    override fun clearSession() {
        this.session = null
    }
}

class AndroidKeystoreCompanionSessionStore(context: Context) : CompanionSessionStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadConfig(): CompanionConfig {
        val url = prefs.getString(KEY_BASE_URL, CompanionDefaults.defaultUrl())
            ?: CompanionDefaults.defaultUrl()
        val name = prefs.getString(KEY_DEVICE_NAME, CompanionDefaults.DEFAULT_DEVICE_NAME)
            ?: CompanionDefaults.DEFAULT_DEVICE_NAME
        return CompanionConfig(
            baseUrl = url.trim().trimEnd('/').ifBlank { CompanionDefaults.defaultUrl() },
            deviceName = name.trim().ifBlank { CompanionDefaults.DEFAULT_DEVICE_NAME }
        )
    }

    override fun saveConfig(config: CompanionConfig) {
        prefs.edit()
            .putString(KEY_BASE_URL, config.baseUrl.trim().trimEnd('/'))
            .putString(KEY_DEVICE_NAME, config.deviceName.trim().ifBlank { CompanionDefaults.DEFAULT_DEVICE_NAME })
            .apply()
    }

    override fun loadSession(): CompanionSession? {
        val deviceId = prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val keyId = prefs.getString(KEY_KEY_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val baseUrl = prefs.getString(KEY_SESSION_URL, null)?.takeIf { it.isNotBlank() } ?: return null
        val deviceName = prefs.getString(KEY_SESSION_DEVICE_NAME, CompanionDefaults.DEFAULT_DEVICE_NAME)
            ?: CompanionDefaults.DEFAULT_DEVICE_NAME
        val transport = prefs.getString(KEY_TRANSPORT, CompanionDefaults.ENCRYPTED_TRANSPORT_ID)
            ?: CompanionDefaults.ENCRYPTED_TRANSPORT_ID
        val pairedAt = prefs.getLong(KEY_PAIRED_AT, 0L)
        val packedKey = prefs.getString(KEY_ENC_SESSION_KEY, null) ?: return null

        val sessionKeyBytes = decryptSessionKey(packedKey) ?: return null
        if (sessionKeyBytes.size != 32) return null

        return CompanionSession(
            baseUrl = baseUrl,
            deviceId = deviceId,
            deviceName = deviceName,
            keyId = keyId,
            sessionKeyBytes = sessionKeyBytes,
            encryptedTransport = transport,
            pairedAtEpochSec = pairedAt
        )
    }

    override fun saveSession(session: CompanionSession) {
        val packedKey = encryptSessionKey(session.sessionKeyBytes)
        prefs.edit()
            .putString(KEY_SESSION_URL, session.baseUrl.trim().trimEnd('/'))
            .putString(KEY_DEVICE_ID, session.deviceId)
            .putString(KEY_SESSION_DEVICE_NAME, session.deviceName)
            .putString(KEY_KEY_ID, session.keyId)
            .putString(KEY_TRANSPORT, session.encryptedTransport)
            .putLong(KEY_PAIRED_AT, session.pairedAtEpochSec)
            .putString(KEY_ENC_SESSION_KEY, packedKey)
            .apply()
    }

    override fun clearSession() {
        prefs.edit()
            .remove(KEY_SESSION_URL)
            .remove(KEY_DEVICE_ID)
            .remove(KEY_SESSION_DEVICE_NAME)
            .remove(KEY_KEY_ID)
            .remove(KEY_TRANSPORT)
            .remove(KEY_PAIRED_AT)
            .remove(KEY_ENC_SESSION_KEY)
            .apply()
    }

    private fun encryptSessionKey(sessionKeyBytes: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(sessionKeyBytes)
        val ivB64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val dataB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        return "$ivB64:$dataB64"
    }

    private fun decryptSessionKey(packed: String): ByteArray? {
        return try {
            val parts = packed.split(':', limit = 2)
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(cipherText)
        } catch (_: Exception) {
            null
        }
    }

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
        private const val KEY_ALIAS = "hydrogen_companion_session_aes_key"
        private const val PREFS_NAME = "hydrogen_companion_session"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        private const val KEY_BASE_URL = "base_url"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_SESSION_URL = "session_base_url"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SESSION_DEVICE_NAME = "session_device_name"
        private const val KEY_KEY_ID = "key_id"
        private const val KEY_TRANSPORT = "encrypted_transport"
        private const val KEY_PAIRED_AT = "paired_at_epoch_sec"
        private const val KEY_ENC_SESSION_KEY = "enc_session_key"
    }
}

