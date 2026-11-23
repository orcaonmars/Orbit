package com.hydrogen.ai.network

import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Local fixture proves the HTTP client cannot forward a bearer token on a 302. No paid calls. */
class CredentialRedirectSafetyTest {
    private fun withRedirectServers(block: (source: Int, destinationRequests: AtomicInteger) -> Unit) {
        val destination = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val received = AtomicInteger(0)
        destination.createContext("/") { exchange ->
            received.incrementAndGet()
            val payload = "{}".toByteArray()
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        destination.start()
        val origin = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        origin.createContext("/") { exchange ->
            // Redirecting even to another port on loopback must not forward the key.
            exchange.responseHeaders.set("Location", "http://127.0.0.1:${destination.address.port}/collect")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        origin.start()
        try {
            block(origin.address.port, received)
        } finally {
            origin.stop(0)
            destination.stop(0)
        }
    }

    @Test fun openai_format_model_listing_never_follows_authenticated_redirect() {
        withRedirectServers { source, seen ->
            val config = RelayConfig(
                ProviderRoute.GEMINI_HYDROGEN_RELAY, "http://127.0.0.1:$source", "mock-model"
            )
            runBlocking { HttpGeminiHydrogenRelayBackend().listModels(config, "fixture-secret") }
            assertEquals("No request should arrive at redirected service", 0, seen.get())
        }
    }

    @Test fun anthropic_format_model_listing_never_follows_authenticated_redirect() {
        withRedirectServers { source, seen ->
            val config = RelayConfig(
                ProviderRoute.ANTHROPIC_MESSAGES, "http://127.0.0.1:$source", "mock-model"
            )
            val result = runBlocking {
                HttpAnthropicMessagesBackend().listModels(config, "fixture-secret")
            }
            assertTrue(result is ModelListResult.Failure)
            assertEquals("Anthropic x-api-key must not be redirected", 0, seen.get())
        }
    }
}
