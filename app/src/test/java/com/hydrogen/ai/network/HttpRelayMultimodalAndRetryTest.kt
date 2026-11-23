package com.hydrogen.ai.network

import com.hydrogen.ai.data.EncodedImagePayload
import com.hydrogen.ai.data.FileConversationStore
import com.hydrogen.ai.data.ImageLoadResult
import com.hydrogen.ai.data.ImagePayloadLoader
import com.hydrogen.ai.data.InMemoryRelayCredentialStore
import com.hydrogen.ai.data.RelayConfigStore
import com.hydrogen.ai.model.AttachmentManifestItem
import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.MessageStatus
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import com.hydrogen.ai.model.VerificationState
import com.hydrogen.ai.ui.ChatViewModel
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HttpRelayMultimodalAndRetryTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeConfigStore(initial: RelayConfig) : RelayConfigStore {
        private var config = initial
        override fun loadConfig(): RelayConfig = config
        override fun saveConfig(config: RelayConfig) {
            this.config = config
        }
    }

    private val syntheticPngBytes = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x02, 0x00, 0x00, 0x00, 0x90.toByte(), 0x77, 0x53, 0xDE.toByte(),
        0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54,
        0x78, 0x9C.toByte(), 0x63, 0xF8.toByte(), 0xCF.toByte(), 0xC0.toByte(),
        0x00, 0x00, 0x03, 0x01, 0x01, 0x00, 0x18, 0xDD.toByte(), 0x8D.toByte(), 0xB0.toByte(),
        0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
        0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
    )

    private suspend fun TestScope.awaitBackendIdle(vm: ChatViewModel) {
        advanceUntilIdle()
        repeat(200) {
            if (!vm.uiState.value.isSending && !vm.uiState.value.isCheckingHealth) return
            withContext(Dispatchers.IO) { Thread.sleep(15) }
            advanceUntilIdle()
        }
    }

    @Test
    fun relay429RateLimitWithResourceExhaustedIsClassifiedTemporaryAndPreservesAttachment() = runTest(testDispatcher) {
        val tempDir = Files.createTempDirectory("hydrogen-http-relay-429").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newSingleThreadExecutor()
        server.createContext("/v1/chat/completions") { exchange ->
            val errBody = """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED","message":"Rate limit exceeded, retry soon"}}"""
                .toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("Retry-After", "7")
            exchange.sendResponseHeaders(429, errBody.size.toLong())
            exchange.responseBody.use { it.write(errBody) }
        }
        server.start()

        try {
            val port = server.address.port
            val config = RelayConfig(
                route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
                baseUrl = "http://127.0.0.1:$port",
                modelId = "hydrogen-qa-mock",
                streamingEnabled = true
            )
            val creds = InMemoryRelayCredentialStore(mapOf(ProviderRoute.GEMINI_HYDROGEN_RELAY to "hydrogen-qa-test-only"))
            val loader = object : ImagePayloadLoader {
                override suspend fun loadImage(localUri: String, expectedMimeType: String): ImageLoadResult =
                    ImageLoadResult.Success(
                        EncodedImagePayload(
                            mimeType = "image/png",
                            base64Data = Base64.getEncoder().encodeToString(syntheticPngBytes),
                            sizeBytes = syntheticPngBytes.size.toLong()
                        )
                    )
            }
            val store = FileConversationStore(tempDir, testDispatcher)
            val vm = ChatViewModel(store, FakeConfigStore(config), creds, HttpGeminiHydrogenRelayBackend(loader)) { 2_000L }
            advanceUntilIdle()

            vm.addStagedAttachment(
                AttachmentManifestItem("att-1", "snap.png", "image/png", syntheticPngBytes.size.toLong(), "file:/tmp/snap.png")
            )
            vm.updateDraft("Rate limited check")
            vm.sendMessage()
            awaitBackendIdle(vm)

            val conv = vm.uiState.value.activeConversation!!
            val assistant = conv.messages[1]
            assertEquals(MessageStatus.FAILED, assistant.status)
            assertEquals(ProviderErrorCategory.QUOTA_RATE_TPM_RPM_429, assistant.error?.category)
            assertEquals(7, assistant.error?.retryAfterSeconds)
            assertFalse(assistant.hasSideEffectsInProgress)
            assertEquals(1, conv.messages[0].attachments.size)
            assertEquals(VerificationState.PASS, vm.uiState.value.readiness.relayReachable)
            assertEquals(VerificationState.PASS, vm.uiState.value.readiness.authenticated)
        } finally {
            server.stop(0)
            tempDir.deleteRecursively()
        }
    }

    private fun testImageLoader() = object : ImagePayloadLoader {
        override suspend fun loadImage(localUri: String, expectedMimeType: String): ImageLoadResult =
            ImageLoadResult.Success(
                EncodedImagePayload("image/png", Base64.getEncoder().encodeToString(syntheticPngBytes), syntheticPngBytes.size.toLong())
            )
    }

    @Test
    fun relayMultimodalStream503AndManualRetryPreservesImageAndIdempotency() = runTest(testDispatcher) {
        val tempDir = Files.createTempDirectory("hydrogen-http-relay-retry").toFile()
        var return503 = true
        val seenKeys = mutableListOf<String>()
        var lastImages = 0
        var lastStream = false
        var lastSystemFirst = false
        val sentinel = "[MOCK QA ONLY - NOT A REAL AI ANSWER] Image and chat transport received."

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newSingleThreadExecutor()
        server.createContext("/v1/gemini/status") { ex ->
            val b = """{"status":"ok","model":"hydrogen-qa-mock","mock":true}""".toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.createContext("/v1/chat/completions") { ex ->
            if (ex.requestHeaders.getFirst("Authorization") != "Bearer hydrogen-qa-test-only") {
                ex.sendResponseHeaders(401, 0)
                ex.responseBody.close()
                return@createContext
            }
            ex.requestHeaders.getFirst("Idempotency-Key")?.let { seenKeys += it }
            val req = Json.parseToJsonElement(ex.requestBody.bufferedReader().readText()).jsonObject
            lastStream = req["stream"]?.jsonPrimitive?.booleanOrNull == true
            val messages = req["messages"]!!.jsonArray.map { it.jsonObject }
            // Orbit's system message (default reply style) always leads; the user's turn with the image follows.
            lastSystemFirst = messages.first()["role"]?.jsonPrimitive?.contentOrNull == "system"
            val parts = messages.first { it["role"]?.jsonPrimitive?.contentOrNull == "user" }["content"] as JsonArray
            lastImages = parts.count {
                val o = it.jsonObject
                o["type"]?.jsonPrimitive?.contentOrNull == "image_url" &&
                    o["image_url"]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull?.startsWith("data:image/png;base64,") == true
            }
            if (return503) {
                val err = """{"error":{"code":503,"status":"UNAVAILABLE","message":"Synthetic QA 503"}}""".toByteArray()
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(503, err.size.toLong())
                ex.responseBody.use { it.write(err) }
            } else {
                val sse = ("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"$sentinel\"},\"finish_reason\":null}]}\n\n" +
                    "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n").toByteArray()
                ex.responseHeaders.add("Content-Type", "text/event-stream")
                ex.sendResponseHeaders(200, sse.size.toLong())
                ex.responseBody.use { it.write(sse) }
            }
        }
        server.start()

        try {
            val config = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, "http://127.0.0.1:${server.address.port}", "hydrogen-qa-mock", streamingEnabled = true)
            val creds = InMemoryRelayCredentialStore(mapOf(ProviderRoute.GEMINI_HYDROGEN_RELAY to "hydrogen-qa-test-only"))
            val store = FileConversationStore(tempDir, testDispatcher)
            val vm = ChatViewModel(store, FakeConfigStore(config), creds, HttpGeminiHydrogenRelayBackend(testImageLoader())) { 1_000L }
            advanceUntilIdle()

            vm.checkRelayHealth()
            awaitBackendIdle(vm)
            assertEquals(VerificationState.PASS, vm.uiState.value.readiness.relayReachable)

            vm.addStagedAttachment(AttachmentManifestItem("att-png", "hydrogen-qa-synthetic.png", "image/png", syntheticPngBytes.size.toLong(), "file:/tmp/s.png"))
            vm.updateDraft("Check image over mock relay")
            vm.sendMessage()
            awaitBackendIdle(vm)

            val failed = vm.uiState.value.activeConversation!!
            assertEquals(2, failed.messages.size)
            assertEquals(1, failed.messages[0].attachments.size)
            assertEquals(MessageStatus.FAILED, failed.messages[1].status)
            assertEquals(ProviderErrorCategory.SERVER_5XX, failed.messages[1].error?.category)
            assertEquals(VerificationState.PASS, vm.uiState.value.readiness.relayReachable)
            assertNotEquals(VerificationState.PASS, vm.uiState.value.readiness.textGeneration)
            assertEquals(1, lastImages)
            assertTrue(lastStream)
            assertTrue(lastSystemFirst)

            return503 = false
            vm.retryMessage(failed.messages[1].id)
            awaitBackendIdle(vm)

            val ok = vm.uiState.value.activeConversation!!
            assertEquals(2, ok.messages.size)
            assertEquals(1, ok.messages[0].attachments.size)
            assertEquals(MessageStatus.COMPLETED, ok.messages[1].status)
            assertEquals(sentinel, ok.messages[1].content)
            assertEquals(2, seenKeys.size)
            assertEquals(seenKeys[0], seenKeys[1])
            assertEquals(VerificationState.PASS, vm.uiState.value.readiness.vision)
            assertEquals(VerificationState.PASS, vm.uiState.value.readiness.streaming)
            assertEquals(MessageStatus.COMPLETED, store.loadConversations().first().messages[1].status)
        } finally {
            server.stop(0)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun localOpenAiServerHealthFallsBackToV1ModelsWithoutFakeAuthOrFalsePositive() = runTest(testDispatcher) {
        // Emulates LM Studio on loopback: there is no /v1/gemini/status route. Unknown routes answer
        // {"error":"Unexpected endpoint or method. ..."} with HTTP 200 ("returning 200 anyway") or 404.
        val localModel = "local-private/gemma-4-26b-a4b-it"
        var unknownRouteCode = 200
        var modelsRouteSupported = true
        var listedModelsJson = """[{"id":"$localModel","object":"model","owned_by":"organization_owner"}]"""
        val modelsAuthHeaders = mutableListOf<String?>()
        fun replyJson(ex: HttpExchange, code: Int, json: String) {
            val b = json.toByteArray(Charsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(code, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newSingleThreadExecutor()
        server.createContext("/") { ex ->
            replyJson(ex, unknownRouteCode, """{"error":"Unexpected endpoint or method. (${ex.requestMethod} ${ex.requestURI.path})"}""")
        }
        server.createContext("/v1/models") { ex ->
            modelsAuthHeaders += ex.requestHeaders.getFirst("Authorization")
            if (modelsRouteSupported) {
                replyJson(ex, 200, """{"object":"list","data":$listedModelsJson}""")
            } else {
                replyJson(ex, unknownRouteCode, """{"error":"Unexpected endpoint or method. (GET /v1/models)"}""")
            }
        }
        server.start()

        try {
            val backend = HttpGeminiHydrogenRelayBackend()
            val config = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, "http://127.0.0.1:${server.address.port}", localModel, streamingEnabled = true)

            // 200 catch-all on /v1/gemini/status: not relay health, and the placeholder token must not become auth PASS.
            val catchAll = backend.checkHealth(config, "hydrogen-qa-test-only")
            assertTrue("expected /v1/models fallback, got $catchAll", catchAll is RelayHealthResult.Reachable)
            val reachable = catchAll as RelayHealthResult.Reachable
            assertFalse(reachable.authenticated)
            assertTrue(reachable.detail, reachable.detail.contains("/v1/models"))
            assertTrue(reachable.detail, reachable.detail.contains("'$localModel' is listed"))

            // 404 on /v1/gemini/status with no token at all: still truthful health, no fake token required.
            unknownRouteCode = 404
            val notFound = backend.checkHealth(config, null)
            assertTrue("expected /v1/models fallback, got $notFound", notFound is RelayHealthResult.Reachable)
            assertFalse((notFound as RelayHealthResult.Reachable).authenticated)
            assertEquals(listOf("Bearer hydrogen-qa-test-only", null), modelsAuthHeaders)

            // Server up but nothing loaded (LM Studio with JIT loading off lists {"data":[]}): no model can answer,
            // so the fallback must fail instead of reporting a reachable provider.
            listedModelsJson = "[]"
            val noModels = backend.checkHealth(config, null)
            assertTrue("expected failure for an empty model list, got $noModels", noModels is RelayHealthResult.Failure)
            // Not UNCONFIGURED: URL + model are saved, and that category would hide this diagnosis behind setup text.
            val noModelsError = (noModels as RelayHealthResult.Failure).error
            assertEquals(ProviderErrorCategory.UNKNOWN_PROTOCOL, noModelsError.category)
            assertTrue(noModelsError.userMessage, noModelsError.userMessage.contains("lists no models"))
            assertEquals("empty-list failure must come from the /v1/models probe", 3, modelsAuthHeaders.size)

            // Ollama with nothing pulled (404 on unknown routes) lists {"object":"list","data":null}: same "load a model"
            // diagnosis as an empty list, not a misleading "not an OpenAI-compatible model list".
            listedModelsJson = "null"
            val nullModels = backend.checkHealth(config, null)
            assertTrue("expected failure for a null model list, got $nullModels", nullModels is RelayHealthResult.Failure)
            val nullModelsError = (nullModels as RelayHealthResult.Failure).error
            assertEquals(ProviderErrorCategory.UNKNOWN_PROTOCOL, nullModelsError.category)
            assertTrue(nullModelsError.userMessage, nullModelsError.userMessage.contains("lists no models"))
            assertEquals("null-list failure must come from the /v1/models probe", 4, modelsAuthHeaders.size)

            // No OpenAI-compatible model list either: must fail instead of reporting a false "reachable".
            unknownRouteCode = 200
            modelsRouteSupported = false
            val notOpenAi = backend.checkHealth(config, "hydrogen-qa-test-only")
            assertTrue("expected failure, got $notOpenAi", notOpenAi is RelayHealthResult.Failure)
            assertEquals(ProviderErrorCategory.UNKNOWN_PROTOCOL, (notOpenAi as RelayHealthResult.Failure).error.category)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun localServerWithNoModelLoadedShowsLoadModelDiagnosisInReadiness() = runTest(testDispatcher) {
        // LM Studio (JIT loading off) with nothing loaded, saved as URL + model with no token: health reaches it and
        // /v1/models lists {"data":[]}. Readiness must show that "load a model" diagnosis, not "setup incomplete" or
        // "hasn't been tested yet", and must not badge "Relay Ready", "Relay Unreachable" or auth PASS.
        val localModel = "local-private/gemma-4-26b-a4b-it"
        fun replyJson(ex: HttpExchange, json: String) {
            val b = json.toByteArray(Charsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newSingleThreadExecutor()
        server.createContext("/") { ex ->
            replyJson(ex, """{"error":"Unexpected endpoint or method. (${ex.requestMethod} ${ex.requestURI.path})"}""")
        }
        server.createContext("/v1/models") { ex -> replyJson(ex, """{"object":"list","data":[]}""") }
        server.start()
        val dir = Files.createTempDirectory("hydrogen-local-no-model").toFile()
        try {
            val config = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, "http://127.0.0.1:${server.address.port}", localModel, streamingEnabled = false)
            val vm = ChatViewModel(FileConversationStore(dir, testDispatcher), FakeConfigStore(config), InMemoryRelayCredentialStore(), HttpGeminiHydrogenRelayBackend()) { 3_000L }
            advanceUntilIdle()
            vm.checkRelayHealth()
            awaitBackendIdle(vm)

            val readiness = vm.uiState.value.readiness
            assertTrue(readiness.statusDetail, readiness.statusDetail.contains("lists no models"))
            assertTrue(readiness.statusDetail, readiness.statusDetail.contains("Load '$localModel'"))
            assertTrue("a local URL + model needs no token, so setup is complete", readiness.configured)
            assertNotEquals("no loaded model must not badge Relay Ready", VerificationState.PASS, readiness.relayReachable)
            assertNotEquals("the server answered, so it is not unreachable", VerificationState.FAIL, readiness.relayReachable)
            assertNotEquals("an unchecked token must not become auth PASS", VerificationState.PASS, readiness.authenticated)
        } finally {
            server.stop(0)
            dir.deleteRecursively()
        }
    }

    @Test
    fun nonLoopbackHost200ErrorBodyIsNeitherRelayHealthNorAuthPass() = runTest(testDispatcher) {
        // A LAN-hosted LM Studio (e.g. a physical phone pointed at the PC's LAN IP) answers /v1/gemini/status with
        // HTTP 200 + {"error":"Unexpected endpoint or method..."}. That is not relay status and the token was never
        // checked, so it must not become Reachable/authenticated, and the /v1/models fallback stays loopback-only.
        // The IPv4-mapped literal is outside the loopback guard yet reaches this 127.0.0.1-bound server: no LAN bind.
        var statusJson = """{"error":"Unexpected endpoint or method. (GET /v1/gemini/status)"}"""
        val requestedPaths = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newSingleThreadExecutor()
        server.createContext("/") { ex ->
            requestedPaths += ex.requestURI.path
            val b = statusJson.toByteArray(Charsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.start()
        try {
            val baseUrl = "http://[::ffff:127.0.0.1]:${server.address.port}"
            assertFalse(HttpGeminiHydrogenRelayBackend.isLoopbackOrEmulatorHost(baseUrl))
            val backend = HttpGeminiHydrogenRelayBackend()
            val config = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, baseUrl, "local-private/gemma-4-26b-a4b-it", streamingEnabled = true)

            val errorBody = backend.checkHealth(config, "hydrogen-qa-test-only")
            assertTrue("expected failure, got $errorBody", errorBody is RelayHealthResult.Failure)
            assertEquals(ProviderErrorCategory.UNKNOWN_PROTOCOL, (errorBody as RelayHealthResult.Failure).error.category)
            assertEquals("no /v1/models fallback for non-loopback hosts", listOf("/v1/gemini/status"), requestedPaths)

            // A genuine relay status on the same non-loopback host keeps its existing Reachable result.
            statusJson = """{"status":"ok","model":"hydrogen-qa-mock","mock":true}"""
            val relayOk = backend.checkHealth(config, "hydrogen-qa-test-only")
            assertTrue("expected reachable, got $relayOk", relayOk is RelayHealthResult.Reachable)
            assertTrue((relayOk as RelayHealthResult.Reachable).authenticated)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun nonJson200CatchAllIsNeitherRelayHealthNorAuthPass() = runTest(testDispatcher) {
        // A local web UI / dev server (or a captive portal) answers every unknown GET with HTTP 200 and an HTML page.
        // That is not relay status and never checked the token: a loopback host must take the /v1/models fallback with
        // auth left unverified, and a non-loopback host must fail instead of a fake reachable/auth PASS.
        val localModel = "local-private/gemma-4-26b-a4b-it"
        val requestedPaths = mutableListOf<String>()
        fun reply(ex: HttpExchange, contentType: String, body: String) {
            requestedPaths += ex.requestURI.path
            val b = body.toByteArray(Charsets.UTF_8)
            ex.responseHeaders.add("Content-Type", contentType)
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newSingleThreadExecutor()
        server.createContext("/") { ex ->
            reply(ex, "text/html; charset=utf-8", "<!doctype html><html><body>Local web UI</body></html>")
        }
        server.createContext("/v1/models") { ex ->
            reply(ex, "application/json", """{"object":"list","data":[{"id":"$localModel","object":"model"}]}""")
        }
        server.start()
        try {
            val backend = HttpGeminiHydrogenRelayBackend()
            val local = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, "http://127.0.0.1:${server.address.port}", localModel, streamingEnabled = true)
            val localHealth = backend.checkHealth(local, "hydrogen-qa-test-only")
            assertTrue("expected /v1/models fallback, got $localHealth", localHealth is RelayHealthResult.Reachable)
            val reachable = localHealth as RelayHealthResult.Reachable
            assertFalse("an HTML 200 must not turn the placeholder token into auth PASS", reachable.authenticated)
            assertTrue(reachable.detail, reachable.detail.contains("'$localModel' is listed"))
            assertEquals(listOf("/v1/gemini/status", "/v1/models"), requestedPaths)

            // Same page via a non-loopback literal (no LAN bind): no fallback, no reachable/auth PASS.
            requestedPaths.clear()
            val remote = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, "http://[::ffff:127.0.0.1]:${server.address.port}", localModel, streamingEnabled = true)
            val remoteHealth = backend.checkHealth(remote, "hydrogen-qa-test-only")
            assertTrue("expected failure, got $remoteHealth", remoteHealth is RelayHealthResult.Failure)
            assertEquals(ProviderErrorCategory.UNKNOWN_PROTOCOL, (remoteHealth as RelayHealthResult.Failure).error.category)
            assertEquals("no /v1/models fallback for non-loopback hosts", listOf("/v1/gemini/status"), requestedPaths)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun localServerChatNeedsNoFakeTokenButRemoteRelayStillDoes() = runTest(testDispatcher) {
        val localModel = "local-private/gemma-4-26b-a4b-it"
        val chatAuthHeaders = mutableListOf<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newSingleThreadExecutor()
        server.createContext("/v1/chat/completions") { ex ->
            chatAuthHeaders += ex.requestHeaders.getFirst("Authorization")
            ex.requestBody.readBytes()
            val b = """{"id":"x","object":"chat.completion","model":"$localModel","choices":[{"index":0,"message":{"role":"assistant","content":"Red"},"finish_reason":"stop"}]}"""
                .toByteArray(Charsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.start()
        val request = ChatDispatchRequest(
            correlationId = "corr-local-1",
            modelId = localModel,
            messages = listOf(
                ChatMessage(
                    id = "u1",
                    correlationId = "corr-local-1",
                    role = MessageRole.USER,
                    content = "What colour?",
                    createdAtEpochMs = 1L,
                    attachments = emptyList()
                )
            ),
            attachments = emptyList(),
            stream = false
        )
        try {
            val backend = HttpGeminiHydrogenRelayBackend()
            val local = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, "http://127.0.0.1:${server.address.port}", localModel, streamingEnabled = false)
            val ok = withContext(Dispatchers.IO) { backend.sendChat(request, local, null) { } }
            assertTrue("expected success, got $ok", ok is ChatDispatchResult.Success)
            assertEquals("Red", (ok as ChatDispatchResult.Success).assistantText)
            assertEquals("no fake Authorization header for tokenless local chat", listOf<String?>(null), chatAuthHeaders)

            // Remote hosts never get the tokenless exemption, and no request is sent. (A private Wi-Fi address takes
            // the keyless Wi-Fi path instead; see WifiModelServerTest.)
            for (url in listOf("https://relay.example.com", "http://my-pc.local:1234")) {
                val remote = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, url, localModel, streamingEnabled = false)
                val denied = backend.sendChat(request, remote, "  ") { }
                assertTrue("expected UNCONFIGURED for $url, got $denied", denied is ChatDispatchResult.Failure)
                assertEquals(ProviderErrorCategory.UNCONFIGURED, (denied as ChatDispatchResult.Failure).error.category)
            }
            assertEquals(1, chatAuthHeaders.size)
            assertTrue(HttpGeminiHydrogenRelayBackend.isLoopbackOrEmulatorHost("http://10.0.2.2:1234"))
            assertFalse(HttpGeminiHydrogenRelayBackend.isLoopbackOrEmulatorHost("http://10.0.2.3:1234"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun localServerChatSuccessKeepsPlaceholderTokenUnverifiedButRemoteRelayEarnsAuthPass() = runTest(testDispatcher) {
        // LM Studio (auth off) ignores Authorization. Health already reports the placeholder token as NOT verified,
        // so a "Verified Chat" against the same local server must not flip it to auth PASS. A remote relay always
        // requires the bearer token, so its successful chat still proves auth.
        val localModel = "local-private/gemma-4-26b-a4b-it"
        val chatAuthHeaders = mutableListOf<String?>()
        fun reply(ex: HttpExchange, contentType: String, body: String) {
            val b = body.toByteArray(Charsets.UTF_8)
            ex.responseHeaders.add("Content-Type", contentType)
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newSingleThreadExecutor()
        server.createContext("/") { ex ->
            reply(ex, "application/json", """{"error":"Unexpected endpoint or method. (${ex.requestMethod} ${ex.requestURI.path})"}""")
        }
        server.createContext("/v1/models") { ex ->
            reply(ex, "application/json", """{"object":"list","data":[{"id":"$localModel","object":"model"}]}""")
        }
        server.createContext("/v1/chat/completions") { ex ->
            chatAuthHeaders += ex.requestHeaders.getFirst("Authorization")
            val req = Json.parseToJsonElement(ex.requestBody.bufferedReader().readText()).jsonObject
            if (req["stream"]?.jsonPrimitive?.booleanOrNull == true) {
                reply(
                    ex, "text/event-stream",
                    "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Red\"},\"finish_reason\":null}]}\n\n" +
                        "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
                )
            } else {
                reply(
                    ex, "application/json",
                    """{"id":"x","object":"chat.completion","model":"$localModel","choices":[{"index":0,"message":{"role":"assistant","content":"Red"},"finish_reason":"stop"}]}"""
                )
            }
        }
        server.start()
        val tempDirs = mutableListOf<java.io.File>()
        fun vmFor(baseUrl: String): ChatViewModel {
            val dir = Files.createTempDirectory("hydrogen-local-auth").toFile().also { tempDirs += it }
            val config = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, baseUrl, localModel, streamingEnabled = false)
            val creds = InMemoryRelayCredentialStore(mapOf(ProviderRoute.GEMINI_HYDROGEN_RELAY to "hydrogen-qa-test-only"))
            return ChatViewModel(FileConversationStore(dir, testDispatcher), FakeConfigStore(config), creds, HttpGeminiHydrogenRelayBackend()) { 3_000L }
        }
        try {
            val local = vmFor("http://127.0.0.1:${server.address.port}")
            advanceUntilIdle()
            local.checkRelayHealth()
            awaitBackendIdle(local)
            assertEquals(VerificationState.PASS, local.uiState.value.readiness.relayReachable)
            assertEquals(VerificationState.UNKNOWN, local.uiState.value.readiness.authenticated)

            local.updateDraft("What colour?")
            local.sendMessage()
            awaitBackendIdle(local)
            val localAnswer = local.uiState.value.activeConversation!!.messages[1]
            assertEquals("local chat failed: ${localAnswer.error}", MessageStatus.COMPLETED, localAnswer.status)
            assertEquals("Red", localAnswer.content)
            assertEquals(VerificationState.PASS, local.uiState.value.readiness.textGeneration)
            assertEquals(
                "local chat success must not turn an unchecked placeholder token into auth PASS",
                VerificationState.UNKNOWN, local.uiState.value.readiness.authenticated
            )

            // Same server via a non-loopback literal follows remote-relay rules: token required, success earns auth PASS.
            val remote = vmFor("http://[::ffff:127.0.0.1]:${server.address.port}")
            advanceUntilIdle()
            remote.updateDraft("What colour?")
            remote.sendMessage()
            awaitBackendIdle(remote)
            val remoteAnswer = remote.uiState.value.activeConversation!!.messages[1]
            assertEquals("remote chat failed: ${remoteAnswer.error}", MessageStatus.COMPLETED, remoteAnswer.status)
            assertEquals(VerificationState.PASS, remote.uiState.value.readiness.authenticated)
            assertEquals(listOf("Bearer hydrogen-qa-test-only", "Bearer hydrogen-qa-test-only"), chatAuthHeaders)
        } finally {
            server.stop(0)
            tempDirs.forEach { it.deleteRecursively() }
        }
    }
}
