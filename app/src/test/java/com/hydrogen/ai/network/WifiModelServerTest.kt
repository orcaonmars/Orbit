package com.hydrogen.ai.network

import com.hydrogen.ai.companion.LanHttpTransport
import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.LocalServerPresets
import com.hydrogen.ai.model.MessageDestination
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.PrivacySummary
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.ProviderPreset
import com.hydrogen.ai.model.ProviderPresets
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import com.hydrogen.ai.ui.pcServerChoices
import com.hydrogen.ai.ui.urlHint
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * LM Studio / Ollama on the PC, reached over the home Wi-Fi at a private address (http://192.168.x.x:1234).
 * Every server here is a raw socket on 127.0.0.1: the backend is told to dial loopback instead of the LAN address, so
 * no test touches the real network. Key rule under test: no access key ever goes over plain Wi-Fi.
 */
class WifiModelServerTest {

    private class Recorded(val requestLine: String, val headers: Map<String, String>, val body: String)

    /** One-connection-at-a-time HTTP/1.1 server; [respond] writes the whole raw reply and may pause between writes. */
    private class RawServer(private val respond: (Recorded, OutputStream) -> Unit) : Closeable {
        private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = server.localPort
        val requests = CopyOnWriteArrayList<Recorded>()

        init {
            Thread {
                while (!server.isClosed) {
                    val client = try {
                        server.accept()
                    } catch (e: IOException) {
                        break
                    }
                    client.use { socket ->
                        socket.soTimeout = 10_000
                        val input = BufferedInputStream(socket.getInputStream())
                        val head = readHead(input).split("\r\n")
                        val headers = head.drop(1).filter { ':' in it }
                            .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
                        val length = headers["content-length"]?.toInt() ?: 0
                        val body = ByteArray(length)
                        var read = 0
                        while (read < length) {
                            val n = input.read(body, read, length - read)
                            if (n == -1) break
                            read += n
                        }
                        val recorded = Recorded(head.first(), headers, String(body, Charsets.UTF_8))
                        requests += recorded
                        val out = socket.getOutputStream()
                        runCatching { respond(recorded, out) }
                        runCatching { out.flush() }
                    }
                }
            }.apply { isDaemon = true; start() }
        }

        private fun readHead(input: InputStream): String {
            val out = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b == -1) break
                out.write(b)
                val bytes = out.toByteArray()
                if (bytes.size >= 4 && String(bytes, bytes.size - 4, 4, Charsets.ISO_8859_1) == "\r\n\r\n") break
            }
            return out.toString(Charsets.ISO_8859_1.name()).trimEnd('\r', '\n')
        }

        override fun close() = server.close()
    }

    private fun backend() = HttpGeminiHydrogenRelayBackend(
        connectTimeoutMs = 3_000,
        readTimeoutMs = 10_000,
        lanDialAddress = { byteArrayOf(127, 0, 0, 1) }
    )

    private fun wifiUrl(port: Int) = "http://192.168.77.10:$port"

    private fun request(stream: Boolean) = ChatDispatchRequest(
        correlationId = "corr-wifi-1",
        modelId = "qwen3-8b",
        messages = listOf(
            ChatMessage(
                id = "u1",
                correlationId = "corr-wifi-1",
                role = MessageRole.USER,
                content = "Say hello",
                createdAtEpochMs = 1L,
                attachments = emptyList()
            )
        ),
        attachments = emptyList(),
        stream = stream
    )

    private fun ascii(text: String) = text.toByteArray(Charsets.ISO_8859_1)

    private fun chunk(text: String): ByteArray {
        val data = text.toByteArray(Charsets.UTF_8)
        return ascii(Integer.toHexString(data.size) + "\r\n") + data + ascii("\r\n")
    }

    private fun sseDelta(content: String) =
        "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"$content\"},\"finish_reason\":null}]}\n\n"

    private val sseStop = "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"

    private fun jsonReply(status: Int, body: String, extraHeaders: String = ""): ByteArray {
        val bytes = body.toByteArray(Charsets.UTF_8)
        return ascii(
            "HTTP/1.1 $status X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\n$extraHeaders\r\n"
        ) + bytes
    }

    // ---------------------------------------------------------------- which addresses take the Wi-Fi path

    @Test
    fun onlyPlainHttpPrivateIpv4LiteralsAreWifiModelHosts() {
        listOf(
            "http://192.168.1.20:1234",
            "http://10.0.0.5:11434",
            "http://172.16.4.2:1234",
            "http://172.31.255.1:1234",
            "http://100.64.0.7:1234",
            "http://192.168.1.20:1234/",
            "http://192.168.1.20:1234/api",
            "http://192.168.1.20"
        ).forEach { assertTrue(it, HttpGeminiHydrogenRelayBackend.isWifiModelHost(it)) }
        listOf(
            "http://127.0.0.1:1234", // USB (adb reverse): normal path
            "http://localhost:1234",
            "http://10.0.2.2:1234", // emulator host alias: normal path
            "https://192.168.1.20:1234", // https keeps the normal path (and its certificate checks)
            "http://user:pw@192.168.1.20:1234",
            "http://192.168.1.20:1234/v1?x=1",
            "http://192.168.1.20:1234/#frag",
            "http://8.8.8.8:1234",
            "http://172.32.0.1:1234",
            "http://my-pc.local:1234",
            "http://192.168.1.256:1234",
            "http://[::ffff:192.168.1.20]:1234",
            "ftp://192.168.1.20:1234",
            ""
        ).forEach { assertFalse(it, HttpGeminiHydrogenRelayBackend.isWifiModelHost(it)) }
        val target = HttpGeminiHydrogenRelayBackend.wifiModelTarget("http://192.168.1.20/lmstudio/")!!
        assertEquals(80, target.port)
        assertEquals("/lmstudio", target.basePath)
    }

    // ---------------------------------------------------------------- chat

    @Test
    fun streamedChunkedChatArrivesPieceByPieceWithNoKey() {
        val firstDeltaSeen = CountDownLatch(1)
        val clientStreamedBeforeEnd = AtomicBoolean(false)
        RawServer { _, out ->
            out.write(ascii("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nTransfer-Encoding: chunked\r\n\r\n"))
            out.write(chunk(sseDelta("Hel")))
            out.flush()
            // The rest is sent only after the phone has shown "Hel": proves the reply is streamed, not buffered.
            clientStreamedBeforeEnd.set(firstDeltaSeen.await(5, TimeUnit.SECONDS))
            out.write(chunk(sseDelta("lo")))
            out.write(chunk(sseStop))
            out.write(ascii("0\r\n\r\n"))
        }.use { server ->
            val deltas = mutableListOf<String>()
            val config = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, wifiUrl(server.port), "qwen3-8b", streamingEnabled = true)
            val result = runBlocking {
                backend().sendChat(request(stream = true), config, "should-never-be-sent") { delta ->
                    if (delta.textDelta.isNotEmpty()) {
                        deltas += delta.textDelta
                        firstDeltaSeen.countDown()
                    }
                }
            }
            assertTrue("expected success, got $result", result is ChatDispatchResult.Success)
            result as ChatDispatchResult.Success
            assertEquals("Hello", result.assistantText)
            assertTrue(result.wasStreamed)
            assertEquals(listOf("Hel", "lo"), deltas)
            assertTrue("the first piece must reach the phone before the server finishes", clientStreamedBeforeEnd.get())

            val sent = server.requests.single()
            assertEquals("POST /v1/chat/completions HTTP/1.1", sent.requestLine)
            assertNull("no access key over plain Wi-Fi", sent.headers["authorization"])
            assertTrue(sent.body, sent.body.contains("\"qwen3-8b\""))
            assertTrue(sent.body, sent.body.contains("Say hello"))
        }
    }

    @Test
    fun nonStreamedChatWithContentLengthOrReadToCloseWorks() {
        val reply = """{"id":"x","object":"chat.completion","model":"qwen3-8b","choices":[{"index":0,"message":{"role":"assistant","content":"Blue"},"finish_reason":"stop"}]}"""
        // Content-Length body
        RawServer { _, out -> out.write(jsonReply(200, reply)) }.use { server ->
            val config = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, wifiUrl(server.port), "qwen3-8b", streamingEnabled = false)
            val result = runBlocking { backend().sendChat(request(stream = false), config, null) { } }
            assertEquals("Blue", (result as ChatDispatchResult.Success).assistantText)
            assertNull(server.requests.single().headers["authorization"])
        }
        // No length, no chunking: the body ends when the server closes the connection.
        RawServer { _, out ->
            out.write(ascii("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n"))
            out.write(reply.toByteArray(Charsets.UTF_8))
        }.use { server ->
            val config = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, wifiUrl(server.port), "qwen3-8b", streamingEnabled = false)
            val result = runBlocking { backend().sendChat(request(stream = false), config, "  ") { } }
            assertEquals("Blue", (result as ChatDispatchResult.Success).assistantText)
        }
    }

    @Test
    fun serverAskingForAKeyGetsAClearRefusalNotTheKey() {
        RawServer { _, out -> out.write(jsonReply(401, """{"error":"Unauthorized"}""")) }.use { server ->
            val config = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, wifiUrl(server.port), "qwen3-8b", streamingEnabled = true)
            val chat = runBlocking { backend().sendChat(request(stream = true), config, "secret-test-key") { } }
            val error = (chat as ChatDispatchResult.Failure).error
            assertEquals(ProviderErrorCategory.UNAUTHORIZED_RELAY_401, error.category)
            assertEquals(HttpGeminiHydrogenRelayBackend.WIFI_KEY_REFUSED_MESSAGE, error.userMessage)

            val health = runBlocking { backend().checkHealth(config, "secret-test-key") }
            assertEquals(
                HttpGeminiHydrogenRelayBackend.WIFI_KEY_REFUSED_MESSAGE,
                (health as RelayHealthResult.Failure).error.userMessage
            )
            val models = runBlocking { backend().listModels(config, "secret-test-key") }
            assertEquals(HttpGeminiHydrogenRelayBackend.WIFI_KEY_REFUSED_MESSAGE, (models as ModelListResult.Failure).message)

            assertEquals(3, server.requests.size)
            server.requests.forEach { sent ->
                assertNull(sent.requestLine, sent.headers["authorization"])
                assertFalse(sent.requestLine, sent.body.contains("secret-test-key"))
            }
        }
    }

    @Test
    fun serverErrorsAndCutShortRepliesAreFailuresNotFakeAnswers() {
        RawServer { _, out -> out.write(jsonReply(500, """{"error":"model crashed"}""")) }.use { server ->
            val config = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, wifiUrl(server.port), "qwen3-8b", streamingEnabled = false)
            val result = runBlocking { backend().sendChat(request(stream = false), config, null) { } }
            assertEquals(ProviderErrorCategory.SERVER_5XX, (result as ChatDispatchResult.Failure).error.category)
        }
        // Content-Length promises more than arrives.
        RawServer { _, out ->
            out.write(ascii("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 500\r\n\r\n{\"choices\":"))
        }.use { server ->
            val config = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, wifiUrl(server.port), "qwen3-8b", streamingEnabled = false)
            val result = runBlocking { backend().sendChat(request(stream = false), config, null) { } }
            assertTrue("expected failure, got $result", result is ChatDispatchResult.Failure)
        }
    }

    @Test
    fun unreachableWifiServerExplainsServeOnLocalNetwork() {
        val closedPort = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val config = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, wifiUrl(closedPort), "qwen3-8b", streamingEnabled = true)
        val chat = runBlocking { backend().sendChat(request(stream = true), config, null) { } }
        val error = (chat as ChatDispatchResult.Failure).error
        assertEquals(ProviderErrorCategory.NETWORK_TIMEOUT, error.category)
        assertTrue(error.userMessage, error.userMessage.contains("Serve on Local Network"))

        val health = runBlocking { backend().checkHealth(config, null) }
        val healthMessage = (health as RelayHealthResult.Failure).error.userMessage
        assertEquals(HttpGeminiHydrogenRelayBackend.wifiUnreachableMessage(wifiUrl(closedPort)), healthMessage)
        assertTrue(healthMessage.contains("OLLAMA_HOST=0.0.0.0"))
        assertTrue(healthMessage.contains("Private networks only"))
    }

    // ---------------------------------------------------------------- health and model list

    @Test
    fun healthAndModelListUseV1ModelsWithoutAKey() {
        val models = """{"object":"list","data":[{"id":"qwen3-8b","object":"model"},{"id":"gemma-3-4b","object":"model"}]}"""
        RawServer { _, out -> out.write(jsonReply(200, models)) }.use { server ->
            val config = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, wifiUrl(server.port), "qwen3-8b", streamingEnabled = true)
            val health = runBlocking { backend().checkHealth(config, "k") }
            assertTrue("expected reachable, got $health", health is RelayHealthResult.Reachable)
            health as RelayHealthResult.Reachable
            assertFalse("a key was never checked, so auth is not a PASS", health.authenticated)
            assertTrue(health.detail, health.detail.contains("'qwen3-8b' is listed"))
            assertTrue(health.detail, health.detail.contains("no access key is sent"))

            val list = runBlocking { backend().listModels(config, null) }
            assertEquals(listOf("gemma-3-4b", "qwen3-8b"), (list as ModelListResult.Success).modelIds)
            server.requests.forEach {
                assertEquals("GET /v1/models HTTP/1.1", it.requestLine)
                assertNull(it.headers["authorization"])
            }
        }
        // Ollama with nothing pulled: a real list with no models.
        RawServer { _, out -> out.write(jsonReply(200, """{"object":"list","data":null}""")) }.use { server ->
            val config = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, wifiUrl(server.port), "", streamingEnabled = true)
            val health = runBlocking { backend().checkHealth(config, null) }
            assertEquals(ProviderErrorCategory.UNKNOWN_PROTOCOL, (health as RelayHealthResult.Failure).error.category)
            assertTrue(health.error.userMessage, health.error.userMessage.contains("lists no models"))
        }
        // A web page on that port is not a model server.
        RawServer { _, out -> out.write(jsonReply(200, "<html>router login</html>")) }.use { server ->
            val config = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, wifiUrl(server.port), "m", streamingEnabled = true)
            val health = runBlocking { backend().checkHealth(config, null) }
            assertTrue("expected failure, got $health", health is RelayHealthResult.Failure)
        }
    }

    @Test
    fun basePathIsKept() {
        RawServer { _, out -> out.write(jsonReply(200, """{"object":"list","data":[{"id":"m"}]}""")) }.use { server ->
            val config = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, "${wifiUrl(server.port)}/proxy", "m", streamingEnabled = true)
            runBlocking { backend().listModels(config, null) }
            assertEquals("GET /proxy/v1/models HTTP/1.1", server.requests.single().requestLine)
        }
    }

    // ---------------------------------------------------------------- streaming transport edge cases

    private fun stream(raw: String, max: Long = LanHttpTransport.MAX_STREAM_BYTES) =
        LanHttpTransport.readStreamingResponse(ByteArrayInputStream(ascii(raw)), max)

    @Test
    fun chunkedBodiesDecodeWithExtensionsAndTrailers() {
        val reply = stream(
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nContent-Length: 999\r\n\r\n" +
                "5;name=value\r\nHello\r\n" + "1\r\n,\r\n" + "6\r\n world\r\n" + "0\r\nX-Trailer: 1\r\n\r\n"
        )
        assertEquals(200, reply.status)
        assertEquals("Hello, world", reply.readText())
    }

    @Test
    fun brokenChunkedOrOversizedBodiesAreErrors() {
        listOf(
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\nzz\r\nHello\r\n0\r\n\r\n", // bad size
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nHelloXX0\r\n\r\n", // no CRLF after data
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nHel", // cut short
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nHello\r\n", // no final chunk
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: gzip\r\n\r\nabc", // unsupported encoding
            "HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\nabc" // shorter than promised
        ).forEach { raw ->
            try {
                stream(raw).readText()
                fail("expected an error for: $raw")
            } catch (expected: IOException) {
                // ok
            }
        }
        // Over the cap: chunked, Content-Length and read-to-close all stop instead of buffering without end.
        listOf(
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n10\r\n0123456789abcdef\r\n0\r\n\r\n",
            "HTTP/1.1 200 OK\r\nContent-Length: 16\r\n\r\n0123456789abcdef",
            "HTTP/1.1 200 OK\r\n\r\n0123456789abcdef"
        ).forEach { raw ->
            try {
                stream(raw, max = 8).readText()
                fail("expected too large for: $raw")
            } catch (expected: IOException) {
                // ok
            }
        }
    }

    @Test
    fun companionPathStillRefusesChunked() {
        try {
            LanHttpTransport.readResponse(
                ByteArrayInputStream(ascii("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nHello\r\n0\r\n\r\n")),
                1024
            )
            fail("the Companion channel must keep refusing chunked replies")
        } catch (expected: IOException) {
            // ok
        }
    }

    // ---------------------------------------------------------------- settings form and presets

    @Test
    fun settingsAcceptWifiServersButNotOtherPlainHttp() {
        assertNull(ProviderPresets.validate(ProviderPreset.LM_STUDIO, "http://192.168.1.20:1234", "m"))
        assertNull(ProviderPresets.validate(ProviderPreset.OLLAMA, "http://10.0.0.5:11434/v1", "m"))
        assertNotNull(ProviderPresets.validate(ProviderPreset.CUSTOM, "http://8.8.8.8:1234", "m"))
        assertNotNull(ProviderPresets.validate(ProviderPreset.CUSTOM, "http://my-pc.local:1234", "m"))
        assertNotNull(ProviderPresets.validate(ProviderPreset.CUSTOM, "http://user:pw@192.168.1.20:1234", "m"))

        assertTrue(LocalServerPresets.isWifiServer("http://192.168.1.20:1234/"))
        assertFalse(LocalServerPresets.isLocalServer("http://192.168.1.20:1234"))
        assertTrue(LocalServerPresets.isPrivateServer("http://192.168.1.20:1234"))
        assertTrue(LocalServerPresets.isPrivateServer("http://127.0.0.1:1234"))
        assertFalse(LocalServerPresets.isPrivateServer("https://api.example.com"))
        val hint = LocalServerPresets.hintFor("http://192.168.1.20:1234")!!
        assertTrue(hint, hint.contains("no key is sent"))
        assertTrue(hint, hint.contains("not encrypted"))
    }

    @Test
    fun wifiPresetsComeFromThePairedPcAddress() {
        assertEquals(
            listOf("http://192.168.1.20:1234", "http://192.168.1.20:11434"),
            LocalServerPresets.wifiPresets("192.168.1.20").map { it.baseUrl }
        )
        assertTrue(LocalServerPresets.wifiPresets(null).isEmpty())
        assertTrue(LocalServerPresets.wifiPresets("127.0.0.1").isEmpty())
        assertTrue(LocalServerPresets.wifiPresets("8.8.8.8").isEmpty())
        assertTrue(LocalServerPresets.wifiPresets("evil.example/x?").isEmpty())

        assertEquals("LM Studio (Wi-Fi)", LocalServerPresets.matchingWifiPreset("http://192.168.1.20:1234/")?.label)
        assertEquals("Ollama (Wi-Fi)", LocalServerPresets.matchingWifiPreset("http://192.168.1.20:11434")?.label)
        assertNull(LocalServerPresets.matchingWifiPreset("http://192.168.1.20:8080"))
        assertNull(LocalServerPresets.matchingWifiPreset("http://127.0.0.1:1234"))

        val lm = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, "http://192.168.1.20:1234", "qwen3-8b")
        assertEquals(ProviderPreset.LM_STUDIO, ProviderPresets.detect(lm))
        assertEquals(ProviderPreset.OLLAMA, ProviderPresets.detect(lm.copy(baseUrl = "http://192.168.1.20:11434")))
        assertEquals(ProviderPreset.CUSTOM, ProviderPresets.detect(lm.copy(baseUrl = "http://192.168.1.20:8080")))
    }

    @Test
    fun usbAndWifiChoicesShowOnAPhoneOnlyOnceThePcAddressIsKnown() {
        val before = LocalServerPresets.pcHost
        try {
            LocalServerPresets.pcHost = LocalServerPresets.DEVICE_HOST
            assertEquals(
                listOf("USB" to "http://127.0.0.1:1234", "Wi-Fi" to "http://192.168.1.20:1234"),
                pcServerChoices(ProviderPreset.LM_STUDIO, "192.168.1.20")
            )
            assertEquals("Wi-Fi" to "http://192.168.1.20:11434", pcServerChoices(ProviderPreset.OLLAMA, "192.168.1.20")[1])
            assertTrue(pcServerChoices(ProviderPreset.LM_STUDIO, null).isEmpty())
            assertTrue(pcServerChoices(ProviderPreset.CUSTOM, "192.168.1.20").isEmpty())
            assertTrue(urlHint(ProviderPreset.LM_STUDIO, "http://192.168.1.20:1234").contains("Serve on Local Network"))
            assertTrue(urlHint(ProviderPreset.OLLAMA, "http://192.168.1.20:11434").contains("OLLAMA_HOST"))
            assertTrue(urlHint(ProviderPreset.LM_STUDIO, "http://127.0.0.1:1234").contains("adb reverse"))

            LocalServerPresets.pcHost = LocalServerPresets.EMULATOR_HOST
            assertTrue("the emulator reaches the PC at 10.0.2.2 already", pcServerChoices(ProviderPreset.LM_STUDIO, "192.168.1.20").isEmpty())
        } finally {
            LocalServerPresets.pcHost = before
        }
    }

    @Test
    fun privacyPageSaysWifiChatsAreNotEncryptedAndCarryNoKey() {
        val url = "http://192.168.1.20:1234"
        assertEquals(MessageDestination.LOCAL_NETWORK, PrivacySummary.destinationOf(ProviderRoute.GEMINI_HYDROGEN_RELAY, url))
        val text = PrivacySummary.asText(
            com.hydrogen.ai.model.PrivacyFacts(
                route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
                endpointUrl = url,
                companionPaired = false,
                chatCount = 0,
                memoryCount = 0,
                memoryEnabled = false,
                savedPromptCount = 0
            )
        )
        assertTrue(text, text.contains("not encrypted"))
        assertTrue(text, text.contains("no access key"))
    }
}
