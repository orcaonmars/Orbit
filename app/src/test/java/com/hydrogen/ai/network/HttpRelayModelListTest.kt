package com.hydrogen.ai.network

import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpRelayModelListTest {

    private fun withModelsServer(
        status: Int,
        body: String,
        seenAuth: AtomicReference<String?> = AtomicReference(null),
        block: (port: Int) -> Unit
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newSingleThreadExecutor()
        server.createContext("/v1/models") { exchange ->
            seenAuth.set(exchange.requestHeaders.getFirst("Authorization"))
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            block(server.address.port)
        } finally {
            server.stop(0)
        }
    }

    private fun config(baseUrl: String) = RelayConfig(ProviderRoute.GEMINI_HYDROGEN_RELAY, baseUrl, "any")

    @Test
    fun localServerListsNormalizedSortedIdsWithoutToken() {
        val auth = AtomicReference<String?>("unset")
        withModelsServer(
            200,
            """{"object":"list","data":[{"id":"qwen2.5-7b"},{"id":" Llama-3 "},{"id":"qwen2.5-7b"},{"id":""}]}""",
            auth
        ) { port ->
            val result = runBlocking {
                HttpGeminiHydrogenRelayBackend().listModels(config("http://127.0.0.1:$port"), null)
            }
            assertEquals(ModelListResult.Success(listOf("Llama-3", "qwen2.5-7b")), result)
            assertNull("No bearer header should be sent without a token", auth.get())
        }
    }

    @Test
    fun remoteHostWithoutTokenFailsWithoutNetwork() {
        val result = runBlocking {
            HttpGeminiHydrogenRelayBackend().listModels(config("https://relay.example.invalid"), "  ")
        }
        assertTrue(result is ModelListResult.Failure)
        assertTrue((result as ModelListResult.Failure).message.contains("API key", ignoreCase = true))
    }

    @Test
    fun httpErrorAndEmptyListAreReportedAsFailures() {
        withModelsServer(401, """{"error":"unauthorized"}""") { port ->
            val result = runBlocking {
                HttpGeminiHydrogenRelayBackend().listModels(config("http://127.0.0.1:$port"), "test-only")
            }
            assertTrue(result is ModelListResult.Failure)
            // Plain words, not a status code: the 401 becomes "didn't accept the key".
            val message = (result as ModelListResult.Failure).message
            assertTrue(message, message.contains("key"))
            assertFalse(message, message.contains("HTTP"))
        }
        withModelsServer(200, """{"data":[]}""") { port ->
            val result = runBlocking {
                HttpGeminiHydrogenRelayBackend().listModels(config("http://127.0.0.1:$port"), null)
            }
            assertTrue(result is ModelListResult.Failure)
        }
    }

    @Test
    fun routingReportsUnsupportedForOtherRoutes() {
        val routing = RoutingChatBackend(HttpGeminiHydrogenRelayBackend(), HttpGeminiHydrogenRelayBackend())
        val offline = runBlocking {
            routing.listModels(RelayConfig(ProviderRoute.LOCAL_OFFLINE, "", "any"), null)
        }
        assertTrue(offline is ModelListResult.Failure)
    }
}
