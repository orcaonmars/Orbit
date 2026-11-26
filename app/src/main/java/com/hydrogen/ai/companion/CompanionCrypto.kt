package com.hydrogen.ai.companion

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class X25519KeyPair(
    val privateKey: ByteArray,
    val publicKey: ByteArray
)

/**
 * Cryptographic primitives matching apps/windows-agent/security.py (W2):
 * - RFC 7748 X25519 key agreement (pure BigInteger over GF(2^255 - 19), works on API 26+ and JVM)
 * - HKDF-SHA256 session key derivation bound to pairingId:pairingCode
 * - AES-256-GCM JSON envelope encryption/decryption bound to HTTP method/path/device/ts/nonce AAD
 */
object CompanionCrypto {

    private const val HKDF_INFO = "hydrogen-companion-w2-session-v1"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    private val P: BigInteger = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val A24: BigInteger = BigInteger.valueOf(121665)
    private val BASE_POINT: ByteArray = ByteArray(32).apply { this[0] = 9 }
    private val secureRandom = SecureRandom()

    fun generateX25519KeyPair(random: SecureRandom = secureRandom): X25519KeyPair {
        val priv = ByteArray(32)
        random.nextBytes(priv)
        clampScalar(priv)
        val pub = x25519(priv, BASE_POINT)
        return X25519KeyPair(privateKey = priv, publicKey = pub)
    }

    fun computeSharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        require(privateKey.size == 32) { "X25519 private key must be 32 bytes" }
        require(peerPublicKey.size == 32) { "X25519 public key must be 32 bytes" }
        val secret = x25519(privateKey, peerPublicKey)
        require(secret.any { it.toInt() != 0 }) { "Invalid low-order X25519 peer public key" }
        return secret
    }

    fun deriveSessionKey(sharedSecret: ByteArray, pairingId: String, pairingCode: String): ByteArray {
        require(sharedSecret.size == 32) { "Shared secret must be 32 bytes" }
        val salt = MessageDigest.getInstance("SHA-256")
            .digest("$pairingId:$pairingCode".toByteArray(Charsets.UTF_8))
        val prk = hmacSha256(key = salt, data = sharedSecret)
        val infoBytes = HKDF_INFO.toByteArray(Charsets.UTF_8)
        val t1Input = ByteArray(infoBytes.size + 1)
        System.arraycopy(infoBytes, 0, t1Input, 0, infoBytes.size)
        t1Input[infoBytes.size] = 0x01
        return hmacSha256(key = prk, data = t1Input)
    }

    /**
     * Pairing v2 (docs/WIFI_MODE_SPEC.md): proves knowledge of the pairing code without sending it, and binds
     * both public keys, so nobody on a shared Wi-Fi can swap keys during pairing.
     */
    fun pairingClientProof(
        pairingId: String,
        pairingCode: String,
        deviceId: String,
        deviceName: String,
        clientPublicKeyB64: String
    ): String = encodeB64(
        hmacSha256(
            key = pairingProofKey(pairingId, pairingCode),
            data = lengthPrefixed(PROOF_CLIENT_LABEL, pairingId, deviceId, deviceName, clientPublicKeyB64)
        )
    )

    fun pairingServerProof(
        pairingId: String,
        pairingCode: String,
        deviceId: String,
        clientPublicKeyB64: String,
        serverPublicKeyB64: String,
        keyId: String
    ): String = encodeB64(
        hmacSha256(
            key = pairingProofKey(pairingId, pairingCode),
            data = lengthPrefixed(PROOF_SERVER_LABEL, pairingId, deviceId, clientPublicKeyB64, serverPublicKeyB64, keyId)
        )
    )

    /** Constant-time check of the PC's serverProof; false for anything malformed. */
    fun verifyPairingServerProof(
        proofB64: String,
        pairingId: String,
        pairingCode: String,
        deviceId: String,
        clientPublicKeyB64: String,
        serverPublicKeyB64: String,
        keyId: String
    ): Boolean {
        val received = runCatching { decodeB64(proofB64) }.getOrNull() ?: return false
        val expected = decodeB64(
            pairingServerProof(pairingId, pairingCode, deviceId, clientPublicKeyB64, serverPublicKeyB64, keyId)
        )
        return MessageDigest.isEqual(received, expected)
    }

    internal fun pairingProofKey(pairingId: String, pairingCode: String): ByteArray {
        val codeKey = MessageDigest.getInstance("SHA-256")
            .digest("$pairingId:$pairingCode".toByteArray(Charsets.UTF_8))
        return hmacSha256(key = codeKey, data = PROOF_KEY_LABEL.toByteArray(Charsets.UTF_8))
    }

    private fun lengthPrefixed(vararg parts: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (part in parts) {
            val bytes = part.toByteArray(Charsets.UTF_8)
            val n = bytes.size
            out.write(byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte()))
            out.write(bytes)
        }
        return out.toByteArray()
    }

    private const val PROOF_KEY_LABEL = "orbit-pair-v2-proof-key"
    private const val PROOF_CLIENT_LABEL = "orbit-pair-v2-client"
    private const val PROOF_SERVER_LABEL = "orbit-pair-v2-server"

    fun buildRequestAad(
        method: String,
        path: String,
        deviceId: String,
        timestamp: String,
        nonce: String
    ): ByteArray = "REQ|${method.uppercase()}|$path|$deviceId|$timestamp|$nonce".toByteArray(Charsets.UTF_8)

    fun buildResponseAad(
        method: String,
        path: String,
        deviceId: String,
        timestamp: String,
        nonce: String
    ): ByteArray = "RESP|${method.uppercase()}|$path|$deviceId|$timestamp|$nonce".toByteArray(Charsets.UTF_8)

    fun encryptBytes(
        sessionKey: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
        random: SecureRandom = secureRandom
    ): EncryptedEnvelope {
        require(sessionKey.size == 32) { "AES-256-GCM session key must be 32 bytes" }
        val iv = ByteArray(GCM_IV_BYTES)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sessionKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedEnvelope(
            ivB64 = encodeB64(iv),
            ciphertextB64 = encodeB64(ciphertext)
        )
    }

    fun decryptBytes(
        sessionKey: ByteArray,
        envelope: EncryptedEnvelope,
        aad: ByteArray
    ): ByteArray {
        require(sessionKey.size == 32) { "AES-256-GCM session key must be 32 bytes" }
        val iv = decodeB64(envelope.ivB64)
        require(iv.size == GCM_IV_BYTES) { "Invalid GCM IV length" }
        val ciphertext = decodeB64(envelope.ciphertextB64)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sessionKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    fun generateNonce(random: SecureRandom = secureRandom): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun encodeB64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decodeB64(text: String): ByteArray = Base64.getDecoder().decode(text.trim())

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun clampScalar(k: ByteArray) {
        require(k.size == 32) { "X25519 scalar must be 32 bytes" }
        k[0] = (k[0].toInt() and 248).toByte()
        k[31] = (k[31].toInt() and 127).toByte()
        k[31] = (k[31].toInt() or 64).toByte()
    }

    private fun x25519(scalarBytes: ByteArray, uBytes: ByteArray): ByteArray {
        val kBytes = scalarBytes.copyOf()
        clampScalar(kBytes)
        val k = decodeLittleEndian(kBytes)

        val uCopy = uBytes.copyOf()
        uCopy[31] = (uCopy[31].toInt() and 127).toByte()
        val u = decodeLittleEndian(uCopy).mod(P)

        val x1 = u
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = u
        var z3 = BigInteger.ONE
        var swap = 0

        for (t in 254 downTo 0) {
            val kt = if (k.testBit(t)) 1 else 0
            swap = swap xor kt
            if (swap != 0) {
                val tx = x2; x2 = x3; x3 = tx
                val tz = z2; z2 = z3; z3 = tz
            }
            swap = kt

            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)
            val sumDaCb = da.add(cb).mod(P)
            val diffDaCb = da.subtract(cb).mod(P)
            x3 = sumDaCb.multiply(sumDaCb).mod(P)
            z3 = x1.multiply(diffDaCb.multiply(diffDaCb).mod(P)).mod(P)
            x2 = aa.multiply(bb).mod(P)
            z2 = e.multiply(aa.add(A24.multiply(e)).mod(P)).mod(P)
        }

        if (swap != 0) {
            val tx = x2; x2 = x3; x3 = tx
            val tz = z2; z2 = z3; z3 = tz
        }

        val z2Inv = z2.modPow(P.subtract(BigInteger.valueOf(2)), P) // BigInteger.TWO is API 33+
        val shared = x2.multiply(z2Inv).mod(P)
        return encodeLittleEndian32(shared)
    }

    private fun decodeLittleEndian(bytes: ByteArray): BigInteger {
        val bigEndian = bytes.reversedArray()
        return BigInteger(1, bigEndian)
    }

    private fun encodeLittleEndian32(value: BigInteger): ByteArray {
        val bigEndian = value.mod(P).toByteArray()
        val fixedBigEndian = ByteArray(32)
        val copyLen = minOf(bigEndian.size, 32)
        val srcOffset = bigEndian.size - copyLen
        val dstOffset = 32 - copyLen
        System.arraycopy(bigEndian, srcOffset, fixedBigEndian, dstOffset, copyLen)
        return fixedBigEndian.reversedArray()
    }
}

