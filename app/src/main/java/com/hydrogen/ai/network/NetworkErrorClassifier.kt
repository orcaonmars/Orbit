package com.hydrogen.ai.network

import com.hydrogen.ai.model.LocalServerPresets
import com.hydrogen.ai.model.ProviderError
import com.hydrogen.ai.model.ProviderErrorCategory
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Turns a connection failure into a plain message that says what went wrong and what to check. All of these stay in
 * [ProviderErrorCategory.NETWORK_TIMEOUT] (the chat offers Retry); [ProviderError.sourceLayer] tells them apart for
 * the error log and the step-by-step help ([com.hydrogen.ai.model.ErrorExplainer]).
 */
object NetworkErrorClassifier {
    const val DNS = "network_dns"
    const val REFUSED = "network_refused"
    const val TIMEOUT = "network_timeout"
    const val TLS = "network_tls"
    const val UNREACHABLE = "network_unreachable"
    const val OTHER = "network"

    /**
     * [serviceName] is the hosted service ("OpenAI"), or null for the user's own server, which is then named by its
     * host. [forChat] adds that the message is saved, for a failed send.
     */
    fun classify(e: IOException, baseUrl: String, serviceName: String?, forChat: Boolean = false): ProviderError {
        val host = hostOf(baseUrl)
        val who = serviceName ?: host.ifEmpty { "the server" }
        val ownServer = serviceName == null && LocalServerPresets.isPrivateServer(baseUrl)
        val (layer, message) = when (e) {
            is UnknownHostException ->
                DNS to if (serviceName != null) {
                    "Orbit couldn't look up $who. Check that this phone is online, then try again."
                } else {
                    "Orbit couldn't find $who. Check that this phone is online and the address is spelled right."
                }
            is SocketTimeoutException ->
                TIMEOUT to "$who took too long to answer. Try again, or pick a smaller model."
            is SSLException ->
                TLS to "The secure connection to $who failed. Check the phone's date and time, and try another network."
            is NoRouteToHostException ->
                UNREACHABLE to "This phone can't reach $who from this network. Check the Wi-Fi or the address."
            is ConnectException ->
                REFUSED to if (ownServer) {
                    "Nothing answered at $who. Check that the server is running and that this phone can reach it."
                } else {
                    "$who refused the connection. Check the address, or try again in a moment."
                }
            else ->
                OTHER to "Orbit couldn't reach $who. Check your connection, then try again."
        }
        val sentence = message.replaceFirstChar { if (it == 't' && message.startsWith("the server")) 'T' else it }
        return ProviderError(
            category = ProviderErrorCategory.NETWORK_TIMEOUT,
            userMessage = if (forChat) "$sentence Your message is saved." else sentence,
            technicalDetail = SecretRedactor.redact("${e::class.java.simpleName}: ${e.message.orEmpty()}".trimEnd(' ', ':')),
            sourceLayer = layer
        )
    }

    fun hostOf(baseUrl: String): String = runCatching { java.net.URI(baseUrl.trim()).host }.getOrNull().orEmpty()

    /** Returns true if this IO error is likely retryable (network congestion, transient timeouts). */
    fun isRetryable(e: IOException): Boolean = when (e) {
        is UnknownHostException -> false
        is SocketTimeoutException -> true
        is SSLException -> false
        is NoRouteToHostException -> false
        is ConnectException -> false
        else -> true
    }

    /** Categories for errors that may be retried safely before a response begins. */
    enum class RetryCategory {
        TIMEOUT,
        OTHER
    }

    /** Non-retryable DNS, TLS, unreachable and refused failures return null. */
    fun retryCategory(e: IOException): RetryCategory? = when {
        !isRetryable(e) -> null
        e is SocketTimeoutException -> RetryCategory.TIMEOUT
        else -> RetryCategory.OTHER
    }
}
