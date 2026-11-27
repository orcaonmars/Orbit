package com.hydrogen.ai.network

import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.OpenAiProtocol
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import com.hydrogen.ai.model.ProviderError
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Responses protocol against a local, deterministic HTTP server. Every request goes to 127.0.0.1; no external
 * endpoint, key or paid call is involved. Server paths are recorded so tests can prove which endpoint was hit.
 */
class ResponsesBackendMockServerTest {

    private lateinit var server: HttpServer
    private val paths = CopyOnWriteArrayList<String>()
    private val bodies = CopyOnWriteArrayList<String>()
    private val authHeaders = CopyOnWriteArrayList<String?>()
    private val backend = HttpGeminiHydrogenRelayBackend(connectTimeoutMs = 5_000, readTimeoutMs = 5_000)

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.start()
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    private fun route(path: String, handler: (HttpExchange) -> Unit) {
        server.createContext(path) { ex ->
            paths += ex.requestURI.path
            bodies += ex.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
            authHeaders += ex.requestHeaders.getFirst("Authorization")
            handler(ex)
        }
    }

    private fun reply(
        ex: HttpExchange,
        code: Int,
        body: String,
        contentType: String = "application/json",
        extraHeaders: Map<String, String> = emptyMap()
    ) {
        ex.responseHeaders.add("Content-Type", contentType)
        extraHeaders.forEach { (k, v) -> ex.responseHeaders.add(k, v) }
        val bytes = body.toByteArray(Charsets.UTF_8)
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private fun sseHeaders(ex: HttpExchange) {
        ex.responseHeaders.add("Content-Type", "text/event-stream")
        ex.sendResponseHeaders(200, 0)
    }

    private fun completedJson(text: String, status: String = "completed") = """
        {"id":"resp_1","object":"response","status":"$status","output":[{"type":"message","role":"assistant",
        "content":[{"type":"output_text","text":"$text"}]}],
        "usage":{"input_tokens":12,"output_tokens":3,"output_tokens_details":{"reasoning_tokens":1}}}
    """.trimIndent()

    private fun config(
        path: String = "",
        protocol: OpenAiProtocol = OpenAiProtocol.RESPONSES,
        streaming: Boolean = true
    ) = RelayConfig(
        route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        baseUrl = "http://127.0.0.1:${server.address.port}$path",
        modelId = "test-model",
        streamingEnabled = streaming,
        openAiProtocol = protocol
    )

    private fun request(stream: Boolean = true) = com.hydrogen.ai.network.ChatDispatchRequest(
        correlationId = "corr-1",
        modelId = "test-model",
        messages = listOf(
            ChatMessage(id = "u1", correlationId = "corr-1", role = MessageRole.USER, content = "Say hello", createdAtEpochMs = 0)
        ),
        attachments = emptyList(),
        stream = stream
    )

    private fun frame(json: String) = json.lineSequence().joinToString("\n") { "data: $it" } + "\n\n"

    @Test
    fun nonStreamedReplyReturnsTextUsageAndUsesResponsesEndpoint() = runBlocking {
        route("/v1/responses") { ex -> reply(ex, 200, completedJson("Hello there")) }

        val result = backend.sendChat(request(stream = false), config(streaming = false), "test-token")

        assertTrue("expected success, got $result", result is ChatDispatchResult.Success)
        val success = result as ChatDispatchResult.Success
        assertEquals("Hello there", success.assistantText)
        assertFalse(success.wasStreamed)
        assertEquals(12, success.usage?.inputTokens)
        assertEquals(3, success.usage?.outputTokens)
        assertEquals(1, success.usage?.reasoningTokens)
        assertEquals(listOf("/v1/responses"), paths)
        assertEquals("Bearer test-token", authHeaders.single())

        val sent = Json.parseToJsonElement(bodies.single()).jsonObject
        assertTrue(sent["input"] != null)
        assertEquals("false", sent["store"]!!.jsonPrimitive.content)
        assertEquals("false", sent["stream"]!!.jsonPrimitive.content)
        assertFalse(sent.containsKey("messages"))
    }

    @Test
    fun streamedTextDeltasArriveInOrderBeforeCompletion() = runBlocking {
        route("/v1/responses") { ex ->
            sseHeaders(ex)
            ex.responseBody.write((
                frame("""{"type":"response.created","response":{"status":"in_progress"}}""") +
                    frame("""{"type":"response.output_text.delta","delta":"Hel"}""") +
                    frame("""{"type":"response.output_text.delta","delta":"lo"}""") +
                    frame("""{"type":"response.completed","response":${completedJson("Hello")}}""")
                ).toByteArray(Charsets.UTF_8))
            ex.responseBody.close()
        }
        val deltas = mutableListOf<String>()

        val result = backend.sendChat(request(), config(), "test-token") { delta ->
            if (delta.textDelta.isNotEmpty()) deltas += delta.textDelta
        }

        assertEquals(listOf("Hel", "lo"), deltas)
        val success = result as ChatDispatchResult.Success
        assertEquals("Hello", success.assistantText)
        assertTrue(success.wasStreamed)
        assertEquals("stop", success.turnMetadata?.finishReason)
        assertEquals(3, success.usage?.outputTokens)
        assertEquals(listOf("/v1/responses"), paths)
    }

    @Test
    fun reasoningSummaryDeltasGoToTheThinkingLineNotTheAnswer() = runBlocking {
        route("/v1/responses") { ex ->
            sseHeaders(ex)
            ex.responseBody.write((
                frame("""{"type":"response.reasoning_summary_text.delta","delta":"Plan"}""") +
                    frame("""{"type":"response.output_text.delta","delta":"Ok"}""") +
                    frame("""{"type":"response.completed","response":${completedJson("Ok")}}""")
                ).toByteArray(Charsets.UTF_8))
            ex.responseBody.close()
        }
        val thinking = mutableListOf<String>()
        val answer = mutableListOf<String>()

        val result = backend.sendChat(request(), config(), "test-token") { delta ->
            if (delta.thinkingDelta.isNotEmpty()) thinking += delta.thinkingDelta
            if (delta.textDelta.isNotEmpty()) answer += delta.textDelta
        }

        assertEquals(listOf("Plan"), thinking)
        assertEquals(listOf("Ok"), answer)
        assertEquals("Plan", (result as ChatDispatchResult.Success).turnMetadata?.thinkingSummary)
    }

    @Test
    fun streamErrorEventAfterPartialTextFailsAndKeepsTheRetryableCategory() = runBlocking {
        route("/v1/responses") { ex ->
            sseHeaders(ex)
            ex.responseBody.write((
                frame("""{"type":"response.output_text.delta","delta":"Par"}""") +
                    frame("""{"type":"error","code":"rate_limit_exceeded","message":"Too many requests"}""")
                ).toByteArray(Charsets.UTF_8))
            ex.responseBody.close()
        }
        val seen = mutableListOf<String>()

        val result = backend.sendChat(request(), config(), "test-token") { delta ->
            if (delta.textDelta.isNotEmpty()) seen += delta.textDelta
        }

        assertEquals(listOf("Par"), seen)
        val failure = failureOf(result)
        assertEquals(429, failure.httpStatus)
        assertTrue(RelayErrorClassifier.isTransientError(failure.category))
    }

    @Test
    fun unauthorizedKeyIsAnAuthErrorNotATransientOne() = runBlocking {
        route("/v1/responses") { ex ->
            reply(ex, 401, """{"error":{"message":"Incorrect API key provided","type":"invalid_request_error","code":"invalid_api_key"}}""")
        }

        val failure = failureOf(backend.sendChat(request(stream = false), config(streaming = false), "bad-token"))

        assertEquals(401, failure.httpStatus)
        assertTrue(failure.category.name.startsWith("UNAUTHORIZED"))
        assertFalse(RelayErrorClassifier.isTransientError(failure.category))
    }

    @Test
    fun rateLimitHonorsRetryAfterHeader() = runBlocking {
        route("/v1/responses") { ex ->
            reply(
                ex, 429,
                """{"error":{"message":"Rate limit reached for requests","type":"requests","code":"rate_limit_exceeded"}}""",
                extraHeaders = mapOf("Retry-After" to "7")
            )
        }

        val failure = failureOf(backend.sendChat(request(stream = false), config(streaming = false), "test-token"))

        assertEquals(429, failure.httpStatus)
        assertEquals(7, failure.retryAfterSeconds)
        assertTrue(RelayErrorClassifier.isTransientError(failure.category))
    }

    @Test
    fun serverWithoutResponsesEndpointIsReportedHonestly() = runBlocking {
        route("/v1/responses") { ex -> reply(ex, 404, "Unexpected endpoint or method.", "text/plain") }

        val failure = failureOf(backend.sendChat(request(stream = false), config(streaming = false), "test-token"))

        assertEquals(ProviderErrorCategory.UNSUPPORTED_CAPABILITY, failure.category)
        assertTrue(failure.userMessage.contains("/v1/responses"))
        assertTrue(failure.userMessage.contains("Chat Completions"))
    }

    @Test
    fun failedStatusInAnOkBodyIsAnErrorNeverEmptyText() = runBlocking {
        route("/v1/responses") { ex ->
            reply(ex, 200, """{"id":"resp_2","status":"failed","error":{"code":"server_error","message":"boom"}}""")
        }

        val failure = failureOf(backend.sendChat(request(stream = false), config(streaming = false), "test-token"))

        assertEquals(500, failure.httpStatus)
    }

    @Test
    fun outputTokenLimitKeepsThePartialAnswerAndMarksItTruncated() = runBlocking {
        route("/v1/responses") { ex ->
            sseHeaders(ex)
            ex.responseBody.write((
                frame("""{"type":"response.output_text.delta","delta":"Partial"}""") +
                    frame("""{"type":"response.incomplete","response":{"status":"incomplete",""" +
                    """"incomplete_details":{"reason":"max_output_tokens"},"output":[]}}""")
                ).toByteArray(Charsets.UTF_8))
            ex.responseBody.close()
        }

        val success = backend.sendChat(request(), config(), "test-token") { } as ChatDispatchResult.Success

        assertEquals("Partial", success.assistantText)
        assertTrue(success.turnMetadata!!.isTruncated)
        assertEquals("length", success.turnMetadata!!.finishReason)
    }

    @Test
    fun streamEndingWithoutTerminalEventKeepsPartialTextAsInterrupted() = runBlocking {
        route("/v1/responses") { ex ->
            sseHeaders(ex)
            ex.responseBody.write(frame("""{"type":"response.output_text.delta","delta":"Half"}""").toByteArray(Charsets.UTF_8))
            ex.responseBody.close()
        }

        val success = backend.sendChat(request(), config(), "test-token") { }
            as ChatDispatchResult.Success

        assertEquals("Half", success.assistantText)
        assertEquals("stream_interrupted", success.turnMetadata!!.finishReason)
        assertTrue(success.turnMetadata!!.isTruncated)
    }

    @Test
    fun emptyStreamWithoutTerminalEventIsAnUnexpectedReply() = runBlocking {
        route("/v1/responses") { ex ->
            sseHeaders(ex)
            ex.responseBody.close()
        }

        val failure = failureOf(backend.sendChat(request(), config(), "test-token") { })

        assertEquals(ProviderErrorCategory.UNKNOWN_PROTOCOL, failure.category)
    }

    @Test
    fun malformedFramesWithoutTextAreAnUnexpectedReplyNotASuccess() = runBlocking {
        route("/v1/responses") { ex ->
            sseHeaders(ex)
            ex.responseBody.write("data: {oops\n\nevent: response.output_text.delta\ndata: not-json\n\n".toByteArray(Charsets.UTF_8))
            ex.responseBody.close()
        }

        val failure = failureOf(backend.sendChat(request(), config(), "test-token") { })

        assertEquals(ProviderErrorCategory.UNKNOWN_PROTOCOL, failure.category)
    }

    @Test
    fun modelDiscoveryAndChatUseSeparateEndpoints() = runBlocking {
        route("/v1/models") { ex ->
            reply(ex, 200, """{"object":"list","data":[{"id":"zeta-model"},{"id":"alpha-model"}]}""")
        }
        route("/v1/responses") { ex -> reply(ex, 200, completedJson("Done")) }
        val cfg = config(streaming = false)

        val models = backend.listModels(cfg, "test-token")
        val chat = backend.sendChat(request(stream = false), cfg, "test-token")

        assertTrue("expected model list, got $models", models is ModelListResult.Success)
        assertEquals(listOf("alpha-model", "zeta-model"), (models as ModelListResult.Success).modelIds)
        assertTrue(chat is ChatDispatchResult.Success)
        assertEquals(listOf("/v1/models", "/v1/responses"), paths)
        assertFalse(paths.any { it.contains("chat/completions") })
    }

    @Test
    fun pastedEndpointAddressesDoNotDuplicateTheSuffix() = runBlocking {
        route("/v1/responses") { ex -> reply(ex, 200, completedJson("One")) }

        backend.sendChat(request(stream = false), config(path = "/v1/responses", streaming = false), "test-token")
        backend.sendChat(request(stream = false), config(path = "/v1/chat/completions", streaming = false), "test-token")

        assertEquals(listOf("/v1/responses", "/v1/responses"), paths)
    }

    @Test
    fun chatCompletionsIsStillTheDefaultEndpoint() = runBlocking {
        route("/v1/chat/completions") { ex ->
            reply(ex, 200, """{"choices":[{"message":{"role":"assistant","content":"Hi"},"finish_reason":"stop"}]}""")
        }

        val result = backend.sendChat(
            request(stream = false), config(protocol = OpenAiProtocol.CHAT_COMPLETIONS, streaming = false), "test-token"
        )

        assertEquals("Hi", (result as ChatDispatchResult.Success).assistantText)
        assertEquals(listOf("/v1/chat/completions"), paths)
        assertFalse(Json.parseToJsonElement(bodies.single()).jsonObject.containsKey("input"))
    }

    @Test
    fun plainHttpWifiServerRefusesResponsesWithoutSendingAnything() = runBlocking {
        val wifi = RelayConfig(
            route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
            baseUrl = "http://192.168.1.20:1234",
            modelId = "test-model",
            openAiProtocol = OpenAiProtocol.RESPONSES
        )

        val failure = failureOf(backend.sendChat(request(stream = false), wifi, "test-token"))

        assertEquals(ProviderErrorCategory.UNSUPPORTED_CAPABILITY, failure.category)
        assertTrue(failure.userMessage.contains("Chat Completions"))
        assertTrue(paths.isEmpty())
    }

    @Test
    fun cancellingMidStreamStopsReadingAndAddsNoLaterDeltas() = runBlocking {
        val release = CountDownLatch(1)
        route("/v1/responses") { ex ->
            sseHeaders(ex)
            runCatching {
                ex.responseBody.write(frame("""{"type":"response.output_text.delta","delta":"One"}""").toByteArray(Charsets.UTF_8))
                ex.responseBody.flush()
                release.await(10, TimeUnit.SECONDS)
                ex.responseBody.write(frame("""{"type":"response.output_text.delta","delta":"Two"}""").toByteArray(Charsets.UTF_8))
                ex.responseBody.flush()
                ex.responseBody.close()
            }
        }
        val seen = CopyOnWriteArrayList<String>()
        var result: ChatDispatchResult? = null
        val job = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            result = backend.sendChat(request(), config(), "test-token") { delta ->
                if (delta.textDelta.isNotEmpty()) {
                    seen += delta.textDelta
                    // Cancel before the server's second event is read; the reader checks cancellation on every line.
                    coroutineContext[Job]?.cancel()
                    release.countDown()
                }
            }
        }
        job.start()
        job.join()

        assertTrue("the coroutine should end cancelled", job.isCancelled)
        assertEquals(listOf("One"), seen.toList())
        assertEquals(null, result)
    }

    private fun failureOf(result: ChatDispatchResult): ProviderError {
        assertTrue("expected failure, got $result", result is ChatDispatchResult.Failure)
        return (result as ChatDispatchResult.Failure).error
    }
}
