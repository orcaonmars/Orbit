package com.hydrogen.ai.companion

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.security.GeneralSecurityException
import java.util.concurrent.Executors
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CompanionCryptoAndClientTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun x25519HkdfAndAesGcmRoundTripAndRejectsTamperedAad() {
        val clientKp = CompanionCrypto.generateX25519KeyPair()
        val serverKp = CompanionCrypto.generateX25519KeyPair()

        val clientSecret = CompanionCrypto.computeSharedSecret(clientKp.privateKey, serverKp.publicKey)
        val serverSecret = CompanionCrypto.computeSharedSecret(serverKp.privateKey, clientKp.publicKey)
        assertArrayEquals(serverSecret, clientSecret)

        val keyA = CompanionCrypto.deriveSessionKey(clientSecret, "pair_123", "123456-abcd")
        val keyWrongCode = CompanionCrypto.deriveSessionKey(clientSecret, "pair_123", "000000-wrong")
        assertFalse(keyA.contentEquals(keyWrongCode))

        val reqAad = CompanionCrypto.buildRequestAad("POST", "/approvals/request", "dev_1", "1700000000", "nonce_1")
        val envelope = CompanionCrypto.encryptBytes(
            sessionKey = keyA,
            plaintext = """{"actionType":"LIST_DIRECTORY"}""".toByteArray(Charsets.UTF_8),
            aad = reqAad
        )

        val decrypted = CompanionCrypto.decryptBytes(keyA, envelope, reqAad)
        assertEquals("""{"actionType":"LIST_DIRECTORY"}""", decrypted.toString(Charsets.UTF_8))

        val wrongPathAad = CompanionCrypto.buildRequestAad("POST", "/work/tasks/start", "dev_1", "1700000000", "nonce_1")
        try {
            CompanionCrypto.decryptBytes(keyA, envelope, wrongPathAad)
            fail("Expected AEAD tag failure when AAD path is substituted")
        } catch (_: GeneralSecurityException) {
            // Expected
        }
    }

    @Test
    fun companionUrlSafetyAllowsLoopbackAndHttpsAndBlocksRemoteCleartext() {
        assertNull(CompanionUrlSafety.validate("http://10.0.2.2:18812"))
        assertNull(CompanionUrlSafety.validate("http://127.0.0.1:18812/"))
        assertNull(CompanionUrlSafety.validate("http://localhost:18812"))
        assertNull(CompanionUrlSafety.validate("https://companion.example.com:8443"))

        // Wi-Fi mode (docs/WIFI_MODE_SPEC.md): private IPv4 literals may use plain http; host names and public IPs may not.
        assertNull(CompanionUrlSafety.validate("http://192.168.1.50:18812"))
        assertNotNull(CompanionUrlSafety.validate("http://8.8.8.8:18812"))
        assertNotNull(CompanionUrlSafety.validate("http://my-pc.local:18812"))

        assertNotNull(CompanionUrlSafety.validate(""))
        assertNotNull(CompanionUrlSafety.validate("ftp://127.0.0.1:18812"))
    }

    @Test
    fun httpClientEncryptedPairingApprovalWorkTaskAndRevokeRoundTrip() = runTest {
        val serverKp = CompanionCrypto.generateX25519KeyPair()
        var sessionKey: ByteArray? = null
        var pairedDeviceId: String? = null
        var approvalPolls = 0

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newSingleThreadExecutor()
        server.createContext("/pair/complete") { ex ->
            val obj = json.parseToJsonElement(ex.requestBody.bufferedReader(Charsets.UTF_8).readText()).jsonObject
            val clientPub = CompanionCrypto.decodeB64(obj["clientPublicKeyB64"]!!.jsonPrimitive.content)
            pairedDeviceId = obj["deviceId"]!!.jsonPrimitive.content
            val shared = CompanionCrypto.computeSharedSecret(serverKp.privateKey, clientPub)
            sessionKey = CompanionCrypto.deriveSessionKey(
                shared,
                obj["pairingId"]!!.jsonPrimitive.content,
                obj["pairingCode"]!!.jsonPrimitive.content
            )
            val resp = """{"paired":true,"deviceId":"$pairedDeviceId","keyId":"k_test1","serverPublicKeyB64":"${CompanionCrypto.encodeB64(serverKp.publicKey)}","encryptedTransport":"x25519-hkdf-aes256gcm","pairedAt":1700000000}"""
            val bytes = resp.toByteArray(Charsets.UTF_8)
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }

        fun handleEncrypted(path: String, handler: (String) -> String) = server.createContext(path) { ex ->
            val devId = ex.requestHeaders.getFirst("X-Hydrogen-Device-Id") ?: ""
            val ts = ex.requestHeaders.getFirst("X-Hydrogen-Timestamp") ?: ""
            val nonce = ex.requestHeaders.getFirst("X-Hydrogen-Nonce") ?: ""
            val key = sessionKey!!
            val reqEnv = json.decodeFromString(
                EncryptedEnvelope.serializer(),
                ex.requestBody.bufferedReader(Charsets.UTF_8).readText()
            )
            val plaintext = CompanionCrypto.decryptBytes(
                key,
                reqEnv,
                CompanionCrypto.buildRequestAad("POST", path, devId, ts, nonce)
            ).toString(Charsets.UTF_8)
            val respJson = handler(plaintext)
            val encResp = CompanionCrypto.encryptBytes(
                key,
                respJson.toByteArray(Charsets.UTF_8),
                CompanionCrypto.buildResponseAad("POST", path, devId, ts, nonce)
            )
            val outBytes = json.encodeToString(EncryptedEnvelope.serializer(), encResp).toByteArray(Charsets.UTF_8)
            ex.sendResponseHeaders(200, outBytes.size.toLong())
            ex.responseBody.use { it.write(outBytes) }
        }

        handleEncrypted("/approvals/request") { body ->
            assertEquals("LIST_DIRECTORY", json.parseToJsonElement(body).jsonObject["actionType"]!!.jsonPrimitive.content)
            """{"approvalId":"apr_1","actionType":"LIST_DIRECTORY","targetName":"apps","status":"PENDING","createdAt":1700000001,"expiresAt":1700000301}"""
        }
        handleEncrypted("/approvals/status") {
            approvalPolls += 1
            """{"approvalId":"apr_1","actionType":"LIST_DIRECTORY","targetName":"apps","status":"APPROVED","createdAt":1700000001,"expiresAt":1700000301}"""
        }
        handleEncrypted("/work/tasks/start") {
            """{"task":{"taskId":"tsk_1","approvalId":"apr_1","actionType":"LIST_DIRECTORY","targetName":"apps","status":"COMPLETED","progressPercent":100,"createdAt":1700000002,"updatedAt":1700000003,"result":{"actionType":"LIST_DIRECTORY","directoryName":"apps","entryCount":1,"entries":[{"name":"android","kind":"dir"}]}},"deduplicated":false}"""
        }
        handleEncrypted("/work/tasks/cancel") {
            """{"task":{"taskId":"tsk_1","approvalId":"apr_1","actionType":"LIST_DIRECTORY","targetName":"apps","status":"CANCELLED","progressPercent":0,"createdAt":1700000002,"updatedAt":1700000004},"deduplicated":false}"""
        }
        handleEncrypted("/pair/revoke") { """{"revoked":true,"deviceId":"$pairedDeviceId"}""" }

        server.start()
        try {
            val client = HttpCompanionClient(clockSeconds = { 1700000000L })
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val session = (client.pairDevice(baseUrl, "Pixel Test", "pair_1", "code_1") as CompanionResult.Success).value
            val apr = (client.requestApproval(session, WorkActionType.LIST_DIRECTORY, "apps", "List apps", "idem_12345678") as CompanionResult.Success).value
            assertEquals("PENDING", apr.status)
            val polled = (client.getApprovalStatus(session, "apr_1") as CompanionResult.Success).value
            assertEquals("APPROVED", polled.status)
            assertEquals(1, approvalPolls)
            val task = (client.startWorkTask(session, "apr_1", WorkActionType.LIST_DIRECTORY, "apps", "idem_87654321") as CompanionResult.Success).value.task
            assertEquals("COMPLETED", task.status)
            assertEquals("android", task.result?.entries?.firstOrNull()?.name)
            val cancelled = (client.cancelWorkTask(session, "tsk_1") as CompanionResult.Success).value.task
            assertEquals("CANCELLED", cancelled.status)
            assertTrue((client.revokePairing(session) as CompanionResult.Success).value.revoked)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun httpClientReadFilePreviewOptionsAndErrorContractsPreserveCodes() = runTest {
        val serverKp = CompanionCrypto.generateX25519KeyPair()
        var sessionKey: ByteArray? = null
        var capturedMaxBytes: String? = null
        var capturedOffsetBytes: String? = null

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newSingleThreadExecutor()
        server.createContext("/pair/complete") { ex ->
            val obj = json.parseToJsonElement(ex.requestBody.bufferedReader(Charsets.UTF_8).readText()).jsonObject
            val pairingId = obj["pairingId"]!!.jsonPrimitive.content
            val pairingCode = obj["pairingCode"]!!.jsonPrimitive.content
            val devId = obj["deviceId"]!!.jsonPrimitive.content
            val clientPub = CompanionCrypto.decodeB64(obj["clientPublicKeyB64"]!!.jsonPrimitive.content)
            sessionKey = CompanionCrypto.deriveSessionKey(
                CompanionCrypto.computeSharedSecret(serverKp.privateKey, clientPub),
                pairingId,
                pairingCode
            )
            val bytes = """{"paired":true,"deviceId":"$devId","keyId":"k_read","serverPublicKeyB64":"${CompanionCrypto.encodeB64(serverKp.publicKey)}","encryptedTransport":"x25519-hkdf-aes256gcm","pairedAt":1700000000}""".toByteArray(Charsets.UTF_8)
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        fun encryptedPost(path: String, handler: (String) -> Pair<Int, String>) {
            server.createContext(path) { ex ->
                val ts = ex.requestHeaders.getFirst("X-Hydrogen-Timestamp")
                val nonce = ex.requestHeaders.getFirst("X-Hydrogen-Nonce")
                val devId = ex.requestHeaders.getFirst("X-Hydrogen-Device-Id")
                val key = sessionKey!!
                val reqEnv = json.decodeFromString(EncryptedEnvelope.serializer(), ex.requestBody.bufferedReader(Charsets.UTF_8).readText())
                val plain = CompanionCrypto.decryptBytes(key, reqEnv, CompanionCrypto.buildRequestAad("POST", path, devId, ts, nonce)).toString(Charsets.UTF_8)
                val (code, payload) = handler(plain)
                val outBytes = if (code == 200) {
                    val enc = CompanionCrypto.encryptBytes(key, payload.toByteArray(Charsets.UTF_8), CompanionCrypto.buildResponseAad("POST", path, devId, ts, nonce))
                    json.encodeToString(EncryptedEnvelope.serializer(), enc).toByteArray(Charsets.UTF_8)
                } else {
                    payload.toByteArray(Charsets.UTF_8)
                }
                ex.sendResponseHeaders(code, outBytes.size.toLong())
                ex.responseBody.use { it.write(outBytes) }
            }
        }
        encryptedPost("/approvals/request") { body ->
            val targetPath = json.parseToJsonElement(body).jsonObject["targetPath"]!!.jsonPrimitive.content
            if (!targetPath.startsWith("C:\\")) {
                403 to """{"error":"Absolute authorized path is required.","code":"relative_path_denied"}"""
            } else {
                200 to """{"approvalId":"apr_read","actionType":"READ_FILE_PREVIEW","targetName":"approved-preview-example.txt","status":"APPROVED","createdAt":1700000001,"expiresAt":1700000301}"""
            }
        }
        encryptedPost("/work/tasks/start") { body ->
            val obj = json.parseToJsonElement(body).jsonObject
            capturedMaxBytes = obj["maxBytes"]?.jsonPrimitive?.content
            capturedOffsetBytes = obj["offsetBytes"]?.jsonPrimitive?.content
            200 to """{"task":{"taskId":"tsk_read","approvalId":"apr_read","actionType":"READ_FILE_PREVIEW","targetName":"approved-preview-example.txt","status":"COMPLETED","progressPercent":100,"createdAt":1700000002,"updatedAt":1700000003,"result":{"actionType":"READ_FILE_PREVIEW","fileName":"approved-preview-example.txt","offsetBytes":128,"bytesRead":69,"totalSizeBytes":197,"truncated":false,"isBinary":false,"encoding":"utf-8","sha256":"deadbeef","previewText":"Safe preview text"}},"deduplicated":false}"""
        }
        encryptedPost("/work/tasks/status") { 401 to """{"error":"Duplicate request nonce rejected.","code":"replay_detected"}""" }

        server.start()
        try {
            val client = HttpCompanionClient(clockSeconds = { 1700000000L })
            val session = (client.pairDevice("http://127.0.0.1:${server.address.port}", "Pixel Test", "pair_read", "code_read") as CompanionResult.Success).value
            val relErr = client.requestApproval(session, WorkActionType.READ_FILE_PREVIEW, "approved-preview-example.txt", "Rel", "idem_rel_1234") as CompanionResult.Failure
            assertEquals(403, relErr.httpStatus)
            assertEquals("relative_path_denied", relErr.code)

            val apr = (client.requestApproval(session, WorkActionType.READ_FILE_PREVIEW, "C:\\sandbox\\approved-preview-example.txt", "Abs", "idem_abs_1234") as CompanionResult.Success).value
            val task = (client.startWorkTask(session, apr.approvalId, WorkActionType.READ_FILE_PREVIEW, "C:\\sandbox\\approved-preview-example.txt", "idem_tsk_1234", maxBytes = 4096, offsetBytes = 128) as CompanionResult.Success).value.task
            assertEquals("4096", capturedMaxBytes)
            assertEquals("128", capturedOffsetBytes)
            assertEquals("Safe preview text", task.result?.previewText)

            val replayErr = client.getWorkTaskStatus(session, "tsk_read") as CompanionResult.Failure
            assertEquals(401, replayErr.httpStatus)
            assertEquals("replay_detected", replayErr.code)
        } finally {
            server.stop(0)
        }
    }
}
