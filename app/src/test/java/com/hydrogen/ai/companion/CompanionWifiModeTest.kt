package com.hydrogen.ai.companion

import com.hydrogen.ai.model.LocalServerPresets
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

/** Wi-Fi mode, phone side (docs/WIFI_MODE_SPEC.md). */
class CompanionWifiModeTest {

    private val json = Json { ignoreUnknownKeys = true }

    // The spec's test vector; the PC suite (Codex C17) asserts the same values.
    private val vId = "0123456789abcdef01234567"
    private val vCode = "123456-89abcdef"
    private val vDevice = "and_00112233445566aa"
    private val vName = "Pixel \u00e9 test"
    private val vCpub = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA="
    private val vSpub = "ISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0A="
    private val vKeyId = "a1b2c3d4e5f60718"

    @Test
    fun pairingV2MatchesTheSpecTestVector() {
        assertArrayEquals(ByteArray(32) { (it + 1).toByte() }, CompanionCrypto.decodeB64(vCpub))
        assertArrayEquals(ByteArray(32) { (it + 33).toByte() }, CompanionCrypto.decodeB64(vSpub))
        assertEquals(
            "eea0790190b8f8161275b9c2974e1c6d4385274e43fa67f2f2a378601117b8db",
            CompanionCrypto.pairingProofKey(vId, vCode).joinToString("") { "%02x".format(it) }
        )
        assertEquals(
            "KcxRn6TEXd5Og85pnfD4gXLb4UaLYOWGCSbD0YFdAOw=",
            CompanionCrypto.pairingClientProof(vId, vCode, vDevice, vName, vCpub)
        )
        assertEquals(
            "zurTPEDKIIbmhSBgbWRCGqVOYXT61tww7rMJCgJGrrw=",
            CompanionCrypto.pairingServerProof(vId, vCode, vDevice, vCpub, vSpub, vKeyId)
        )
    }

    @Test
    fun clientProofCoversEveryFieldAndServerProofRejectsAnySwap() {
        val base = CompanionCrypto.pairingClientProof(vId, vCode, vDevice, vName, vCpub)
        assertFalse(base == CompanionCrypto.pairingClientProof(vId, "123456-89abcdee", vDevice, vName, vCpub))
        assertFalse(base == CompanionCrypto.pairingClientProof(vId, vCode, "and_ffffffffffffffff", vName, vCpub))
        assertFalse(base == CompanionCrypto.pairingClientProof(vId, vCode, vDevice, "Pixel e test", vCpub))
        assertFalse(base == CompanionCrypto.pairingClientProof(vId, vCode, vDevice, vName, vSpub))
        // Length prefixes: moving a character between fields must change the proof.
        assertFalse(
            CompanionCrypto.pairingClientProof("ab", "c", "de", "f", "g") ==
                CompanionCrypto.pairingClientProof("ab", "c", "d", "ef", "g")
        )

        val good = "zurTPEDKIIbmhSBgbWRCGqVOYXT61tww7rMJCgJGrrw="
        assertTrue(CompanionCrypto.verifyPairingServerProof(good, vId, vCode, vDevice, vCpub, vSpub, vKeyId))
        assertFalse(CompanionCrypto.verifyPairingServerProof(good, vId, "000000-00000000", vDevice, vCpub, vSpub, vKeyId))
        assertFalse(CompanionCrypto.verifyPairingServerProof(good, vId, vCode, vDevice, vCpub, vCpub, vKeyId))
        assertFalse(CompanionCrypto.verifyPairingServerProof(good, vId, vCode, vDevice, vSpub, vSpub, vKeyId))
        assertFalse(CompanionCrypto.verifyPairingServerProof(good, vId, vCode, vDevice, vCpub, vSpub, "other"))
        assertFalse(CompanionCrypto.verifyPairingServerProof("", vId, vCode, vDevice, vCpub, vSpub, vKeyId))
        assertFalse(CompanionCrypto.verifyPairingServerProof("not base64!!", vId, vCode, vDevice, vCpub, vSpub, vKeyId))
    }

    @Test
    fun plainHttpIsAllowedOnlyForLoopbackAndPrivateIpv4Literals() {
        listOf(
            "http://10.0.0.5:18812",
            "http://172.16.0.1:18812",
            "http://172.31.255.255:18812",
            "http://192.168.1.23:18812",
            "http://100.64.0.1:18812",
            "http://100.127.255.254:18812"
        ).forEach { assertNull(it, CompanionUrlSafety.validate(it)) }
        listOf(
            "http://8.8.8.8:18812",
            "http://11.0.0.1:18812",
            "http://172.15.255.255:18812",
            "http://172.32.0.1:18812",
            "http://100.63.255.255:18812",
            "http://100.128.0.1:18812",
            "http://192.169.1.1:18812",
            "http://192.168.1.256:18812",
            "http://0192.168.1.1:18812",
            "http://my-pc.local:18812",
            "http://[::ffff:192.168.1.1]:18812",
            "http://[fe80::1]:18812"
        ).forEach { assertNotNull(it, CompanionUrlSafety.validate(it)) }
        assertNull(CompanionUrlSafety.validate("https://192.168.1.23:18812"))
    }

    @Test
    fun typedAddressesBecomeCompanionUrls() {
        assertEquals("http://192.168.1.23:18812", CompanionUrlSafety.normalize("192.168.1.23"))
        assertEquals("http://192.168.1.23:18812", CompanionUrlSafety.normalize(" 192.168.1.23:18812 "))
        assertEquals("http://192.168.1.23:18812", CompanionUrlSafety.normalize("http://192.168.1.23/"))
        assertEquals("http://192.168.1.23:18812", CompanionUrlSafety.normalize("HTTP://192.168.1.23"))
        assertEquals("http://192.168.1.23:9000", CompanionUrlSafety.normalize("http://192.168.1.23:9000"))
        assertEquals("http://127.0.0.1:18812", CompanionUrlSafety.normalize("127.0.0.1"))
        assertEquals("http://10.0.2.2:18812", CompanionUrlSafety.normalize("http://10.0.2.2:18812/"))
        assertEquals("https://pc.example.com", CompanionUrlSafety.normalize("https://pc.example.com/"))
        assertEquals("", CompanionUrlSafety.normalize("   "))
        // A typed host name is not silently allowed over plain http.
        assertNotNull(CompanionUrlSafety.validate(CompanionUrlSafety.normalize("my-pc.local")))
        assertNotNull(CompanionUrlSafety.validate(CompanionUrlSafety.normalize("8.8.8.8")))
    }

    @Test
    fun onlyWifiAddressesUseTheSocketTransportAndTenZeroTwoTwoIsLoopbackOnlyOnTheEmulator() {
        assertEquals("192.168.1.23", CompanionUrlSafety.lanIpv4Host("http://192.168.1.23:18812"))
        assertEquals("100.64.0.1", CompanionUrlSafety.lanIpv4Host("http://100.64.0.1:18812"))
        assertNull(CompanionUrlSafety.lanIpv4Host("https://192.168.1.23:18812"))
        assertNull(CompanionUrlSafety.lanIpv4Host("http://127.0.0.1:18812"))
        assertNull(CompanionUrlSafety.lanIpv4Host("http://8.8.8.8:18812"))
        assertNull(CompanionUrlSafety.lanIpv4Host("http://my-pc.local:18812"))
        assertTrue(CompanionUrlSafety.isLoopback("http://127.0.0.1:18812"))
        assertTrue(CompanionUrlSafety.isLoopback("http://localhost:18812"))
        assertFalse(CompanionUrlSafety.isLoopback("http://192.168.1.23:18812"))
        assertFalse(CompanionUrlSafety.isLoopback("https://pc.example.com"))

        val saved = LocalServerPresets.pcHost
        try {
            LocalServerPresets.pcHost = LocalServerPresets.EMULATOR_HOST
            assertTrue(CompanionUrlSafety.isLoopback("http://10.0.2.2:18812"))
            assertNull(CompanionUrlSafety.lanIpv4Host("http://10.0.2.2:18812"))

            LocalServerPresets.pcHost = LocalServerPresets.DEVICE_HOST // a real phone
            assertFalse(CompanionUrlSafety.isLoopback("http://10.0.2.2:18812"))
            assertEquals("10.0.2.2", CompanionUrlSafety.lanIpv4Host("http://10.0.2.2:18812"))
        } finally {
            LocalServerPresets.pcHost = saved
        }
    }

    @Test
    fun transportBuildsAStrictRequestAndRefusesHeaderInjection() {
        val request = LanHttpTransport.buildRequest(
            address = byteArrayOf(192.toByte(), 168.toByte(), 1, 23),
            port = 18812,
            method = "POST",
            pathAndQuery = "/pair/complete",
            headers = linkedMapOf("Accept" to "application/json", "Content-Type" to "application/json; charset=utf-8"),
            body = "{\"a\":\"\u00e9\"}".toByteArray(Charsets.UTF_8)
        )
        val text = String(request, Charsets.UTF_8)
        assertTrue(text.startsWith("POST /pair/complete HTTP/1.1\r\nHost: 192.168.1.23:18812\r\nConnection: close\r\n"))
        assertTrue(text.contains("\r\nContent-Length: 10\r\n\r\n{\"a\":\"\u00e9\"}"))

        fun refused(block: () -> Unit) = try {
            block(); fail("expected refusal")
        } catch (_: IllegalArgumentException) {
        }
        val addr = byteArrayOf(192.toByte(), 168.toByte(), 1, 23)
        refused { LanHttpTransport.buildRequest(addr, 18812, "GET", "/health", mapOf("X-A" to "a\r\nX-Evil: 1"), null) }
        refused { LanHttpTransport.buildRequest(addr, 18812, "GET", "/health", mapOf("Bad Name" to "x"), null) }
        refused { LanHttpTransport.buildRequest(addr, 18812, "GET", "/health", mapOf("Host" to "evil"), null) }
        refused { LanHttpTransport.buildRequest(addr, 18812, "GET", "/health", mapOf("Content-Length" to "0"), null) }
        refused { LanHttpTransport.buildRequest(addr, 18812, "GET", "/health HTTP/1.1\r\nX: y", emptyMap(), null) }
        refused { LanHttpTransport.buildRequest(addr, 18812, "DELETE", "/health", emptyMap(), null) }
        refused { LanHttpTransport.buildRequest(ByteArray(16), 18812, "GET", "/health", emptyMap(), null) }
    }

    @Test
    fun transportParsesRepliesAndFailsClosedOnBadOnes() {
        fun parse(raw: String, max: Int = 1024) =
            LanHttpTransport.readResponse(ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8)), max)

        val ok = parse("HTTP/1.0 200 OK\r\nServer: x\r\nContent-Length: 11\r\n\r\n{\"ok\":true}trailing")
        assertEquals(200, ok.status)
        assertEquals("{\"ok\":true}", ok.body)
        assertEquals(403, parse("HTTP/1.1 403 Forbidden\r\nContent-Length: 2\r\n\r\n{}").status)
        assertEquals("{\"to\":\"eof\"}", parse("HTTP/1.0 200 OK\r\n\r\n{\"to\":\"eof\"}").body)
        assertEquals("\u00e9", parse("HTTP/1.0 200 OK\r\nContent-Length: 2\r\n\r\n\u00e9").body)

        fun fails(raw: String, max: Int = 1024) = try {
            parse(raw, max); fail("expected failure for: $raw")
        } catch (_: IOException) {
        }
        fails("")
        fails("HTTP/1.0 200 OK\r\nContent-Length: 2\r\n") // no end of headers
        fails("SSH-2.0-OpenSSH\r\n\r\n")
        fails("HTTP/2 200\r\n\r\n")
        fails("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n2\r\n{}\r\n0\r\n\r\n")
        fails("HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\n{}") // cut short
        fails("HTTP/1.1 200 OK\r\nContent-Length: -1\r\n\r\n")
        fails("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nContent-Length: 3\r\n\r\n{}")
        fails("HTTP/1.1 200 OK\r\nno colon here\r\n\r\n")
        fails("HTTP/1.1 200 OK\r\nContent-Length: 2000\r\n\r\n", max = 1024)
        fails("HTTP/1.0 200 OK\r\n\r\n" + "x".repeat(2000), max = 1024)
    }

    @Test
    fun wifiPairingSendsAProofNotTheCodeAndTheEncryptedRequestsWorkOverTheSocketTransport() = runTest {
        val serverKp = CompanionCrypto.generateX25519KeyPair()
        var sessionKey: ByteArray? = null
        val code = "654321-0a1b2c3d"
        MiniHttpServer { req ->
            when (req.path) {
                "/health" -> 200 to """{"service":"hydrogen-windows-companion","status":"unpaired"}"""
                "/pair/complete" -> {
                    val obj = json.parseToJsonElement(req.body).jsonObject
                    assertFalse("the code must never cross Wi-Fi", obj.containsKey("pairingCode"))
                    assertFalse(req.body.contains(code))
                    val f = obj.fields()
                    assertEquals(
                        CompanionCrypto.pairingClientProof(f["pairingId"]!!, code, f["deviceId"]!!, f["deviceName"]!!, f["clientPublicKeyB64"]!!),
                        f["pairingProof"]
                    )
                    val shared = CompanionCrypto.computeSharedSecret(serverKp.privateKey, CompanionCrypto.decodeB64(f["clientPublicKeyB64"]!!))
                    sessionKey = CompanionCrypto.deriveSessionKey(shared, f["pairingId"]!!, code)
                    val spub = CompanionCrypto.encodeB64(serverKp.publicKey)
                    val proof = CompanionCrypto.pairingServerProof(f["pairingId"]!!, code, f["deviceId"]!!, f["clientPublicKeyB64"]!!, spub, "k_wifi")
                    200 to """{"paired":true,"deviceId":"${f["deviceId"]}","keyId":"k_wifi","serverPublicKeyB64":"$spub","encryptedTransport":"x25519-hkdf-aes256gcm","pairedAt":1700000000,"serverProof":"$proof"}"""
                }
                "/approvals/request" -> {
                    val devId = req.headers["x-hydrogen-device-id"].orEmpty()
                    val ts = req.headers["x-hydrogen-timestamp"].orEmpty()
                    val nonce = req.headers["x-hydrogen-nonce"].orEmpty()
                    val env = json.decodeFromString(EncryptedEnvelope.serializer(), req.body)
                    val plain = CompanionCrypto.decryptBytes(
                        sessionKey!!, env, CompanionCrypto.buildRequestAad("POST", req.path, devId, ts, nonce)
                    ).toString(Charsets.UTF_8)
                    assertEquals("READ_FILE_PREVIEW", json.parseToJsonElement(plain).jsonObject["actionType"]!!.jsonPrimitive.content)
                    val reply = """{"approvalId":"apr_w1","actionType":"READ_FILE_PREVIEW","targetName":"notes.txt","status":"PENDING","createdAt":1700000001,"expiresAt":1700000301}"""
                    val enc = CompanionCrypto.encryptBytes(
                        sessionKey, reply.toByteArray(Charsets.UTF_8),
                        CompanionCrypto.buildResponseAad("POST", req.path, devId, ts, nonce)
                    )
                    200 to json.encodeToString(EncryptedEnvelope.serializer(), enc)
                }
                else -> 404 to """{"error":"Not found.","code":"not_found"}"""
            }
        }.use { server ->
            val client = wifiClient()
            val base = "http://192.168.77.10:${server.port}"

            val health = client.checkHealth(base)
            assertEquals("unpaired", (health as CompanionResult.Success).value.status)

            val paired = client.pairDevice(base, "Pixel \u00e9 Wi-Fi", "pair_w1", code)
            val session = (paired as CompanionResult.Success).value
            assertEquals("k_wifi", session.keyId)
            assertArrayEquals(sessionKey, session.sessionKeyBytes)

            val approval = client.requestApproval(
                session, WorkActionType.READ_FILE_PREVIEW, "C:\\Users\\me\\Orbit-Shared\\notes.txt", "Read notes", "idem_wifi_0001"
            )
            assertEquals("PENDING", (approval as CompanionResult.Success).value.status)

            val missing = client.checkHealth("$base/nope")
            assertEquals("not_found", (missing as CompanionResult.Failure).code)
            assertEquals(404, missing.httpStatus)

            server.requests.forEach { req ->
                assertTrue(req.rawHead.startsWith("${req.method} ${req.path} HTTP/1.1\r\n"))
                assertTrue(req.headers["connection"] == "close")
            }
        }
    }

    @Test
    fun overWifiAMissingOrWrongServerProofSavesNothing() = runTest {
        val serverKp = CompanionCrypto.generateX25519KeyPair()
        val spub = CompanionCrypto.encodeB64(serverKp.publicKey)
        var mode = "missing"
        MiniHttpServer { req ->
            val f = json.parseToJsonElement(req.body).jsonObject.fields()
            val proof = when (mode) {
                "missing" -> ""
                "wrongCode" -> CompanionCrypto.pairingServerProof(f["pairingId"]!!, "000000-00000000", f["deviceId"]!!, f["clientPublicKeyB64"]!!, spub, "k1")
                else -> CompanionCrypto.pairingServerProof(f["pairingId"]!!, "111111-11111111", f["deviceId"]!!, f["clientPublicKeyB64"]!!, spub, "k1")
            }
            val deviceId = if (mode == "otherDevice") "and_ffffffffffffffff" else f["deviceId"]
            200 to """{"paired":true,"deviceId":"$deviceId","keyId":"k1","serverPublicKeyB64":"$spub","serverProof":"$proof"}"""
        }.use { server ->
            val client = wifiClient()
            val base = "http://10.20.30.40:${server.port}"

            val missing = client.pairDevice(base, "Pixel", "pair_1", "111111-11111111")
            assertEquals("pc_proof_missing", (missing as CompanionResult.Failure).code)
            assertTrue(missing.userMessage.startsWith("This PC couldn't prove it showed you that code. Nothing was saved."))

            mode = "wrongCode"
            val wrong = client.pairDevice(base, "Pixel", "pair_1", "111111-11111111")
            assertEquals("pc_proof_failed", (wrong as CompanionResult.Failure).code)

            mode = "otherDevice"
            val swapped = client.pairDevice(base, "Pixel", "pair_1", "111111-11111111")
            assertEquals("pc_proof_failed", (swapped as CompanionResult.Failure).code)
        }
    }

    @Test
    fun overUsbTheLegacyCodeStillGoesAndAnOlderCompanionWithoutAProofStillPairs() = runTest {
        val serverKp = CompanionCrypto.generateX25519KeyPair()
        val spub = CompanionCrypto.encodeB64(serverKp.publicKey)
        var sendBadProof = false
        MiniHttpServer { req ->
            val f = json.parseToJsonElement(req.body).jsonObject.fields()
            assertEquals("222222-22222222", f["pairingCode"])
            assertNotNull(f["pairingProof"])
            val proof = if (sendBadProof) "AAAA" else ""
            200 to """{"paired":true,"deviceId":"${f["deviceId"]}","keyId":"k1","serverPublicKeyB64":"$spub","serverProof":"$proof"}"""
        }.use { server ->
            val client = HttpCompanionClient(clockSeconds = { 1700000000L })
            val base = "http://127.0.0.1:${server.port}"
            assertTrue(client.pairDevice(base, "Pixel", "pair_1", "222222-22222222") is CompanionResult.Success)

            sendBadProof = true
            val bad = client.pairDevice(base, "Pixel", "pair_1", "222222-22222222")
            assertEquals("pc_proof_failed", (bad as CompanionResult.Failure).code)
        }
    }

    @Test
    fun anUnreachableWifiPcGetsAPlainEnglishMessage() = runTest {
        val closedPort = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val client = wifiClient(connectTimeoutMs = 1_500)
        val result = client.checkHealth("http://192.168.77.10:$closedPort")
        val failure = result as CompanionResult.Failure
        assertEquals("network_error", failure.code)
        assertTrue(failure.userMessage, failure.userMessage.startsWith("Can't reach your PC at 192.168.77.10."))
        assertTrue(failure.userMessage.contains("Wi-Fi mode"))
    }

    @Test
    fun longNamesNeverEndInHalfAnEmoji() = runTest {
        var sentName = ""
        MiniHttpServer { req ->
            sentName = json.parseToJsonElement(req.body).jsonObject.fields()["deviceName"].orEmpty()
            403 to """{"error":"Invalid pairing code.","code":"invalid_pairing_code"}"""
        }.use { server ->
            val name = "a".repeat(63) + "\uD83D\uDE00"
            val result = HttpCompanionClient().pairDevice("http://127.0.0.1:${server.port}", name, "p", "c")
            assertEquals("invalid_pairing_code", (result as CompanionResult.Failure).code)
            assertEquals(403, result.httpStatus)
            assertEquals("a".repeat(63), sentName)
        }
    }

    private fun wifiClient(connectTimeoutMs: Int = 6_000) = HttpCompanionClient(
        clockSeconds = { 1700000000L },
        connectTimeoutMs = connectTimeoutMs,
        readTimeoutMs = 5_000,
        lanDialAddress = { byteArrayOf(127, 0, 0, 1) }
    )

    private fun JsonObject.fields(): Map<String, String> = mapValues { it.value.jsonPrimitive.content }

    /** A one-request-per-connection HTTP/1.0 server, like Python's http.server that the Companion uses. */
    private class MiniHttpServer(private val handler: (Request) -> Pair<Int, String>) : AutoCloseable {
        class Request(val method: String, val path: String, val headers: Map<String, String>, val body: String, val rawHead: String)

        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int = socket.localPort
        val requests = CopyOnWriteArrayList<Request>()

        init {
            Thread {
                while (!socket.isClosed) {
                    val conn = try {
                        socket.accept()
                    } catch (_: IOException) {
                        break
                    }
                    conn.use { handle(it) }
                }
            }.apply { isDaemon = true }.start()
        }

        private fun handle(conn: Socket) {
            conn.soTimeout = 5_000
            val input = conn.getInputStream()
            val head = readHead(input) ?: return
            val lines = head.split("\r\n")
            val (method, path) = lines.first().split(" ").let { it[0] to it[1] }
            val headers = lines.drop(1).filter { ':' in it }.associate {
                it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim()
            }
            val length = headers["content-length"]?.toInt() ?: 0
            val body = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(body, read, length - read)
                if (n == -1) return
                read += n
            }
            val request = Request(method, path, headers, String(body, Charsets.UTF_8), head + "\r\n\r\n")
            requests += request
            val (status, reply) = try {
                handler(request)
            } catch (e: Throwable) {
                500 to """{"error":"test server: ${e.message?.replace("\"", "'")}","code":"test_assertion"}"""
            }
            val bytes = reply.toByteArray(Charsets.UTF_8)
            val out = conn.getOutputStream()
            out.write("HTTP/1.0 $status X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            out.write(bytes)
            out.flush()
        }

        private fun readHead(input: InputStream): String? {
            val out = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b == -1) return null
                out.write(b)
                val s = out.size()
                if (s >= 4) {
                    val a = out.toByteArray()
                    if (a[s - 4] == '\r'.code.toByte() && a[s - 3] == '\n'.code.toByte() &&
                        a[s - 2] == '\r'.code.toByte() && a[s - 1] == '\n'.code.toByte()
                    ) {
                        return String(a, 0, s - 4, Charsets.ISO_8859_1)
                    }
                }
            }
        }

        override fun close() {
            socket.close()
        }
    }
}
