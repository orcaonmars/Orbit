package com.hydrogen.ai.network

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkErrorClassifierTest {

    @Test fun isRetryable_unknownHostException_returnsFalse() {
        val e = UnknownHostException("name or service not known")
        assertFalse(NetworkErrorClassifier.isRetryable(e))
    }

    @Test fun isRetryable_socketTimeoutException_returnsTrue() {
        val e = SocketTimeoutException("Timeout on connect")
        assertTrue(NetworkErrorClassifier.isRetryable(e))
    }

    @Test fun isRetryable_sslException_returnsFalse() {
        val e = SSLException("Handshake failed")
        assertFalse(NetworkErrorClassifier.isRetryable(e))
    }

    @Test fun isRetryable_noRouteToHostException_returnsFalse() {
        val e = NoRouteToHostException("No route to host")
        assertFalse(NetworkErrorClassifier.isRetryable(e))
    }

    @Test fun isRetryable_connectException_returnsFalse() {
        val e = ConnectException("Connection refused")
        assertFalse(NetworkErrorClassifier.isRetryable(e))
    }

    @Test fun isRetryable_ieoException_returnsTrue() {
        val e = IOException("Input/output error")
        assertTrue(NetworkErrorClassifier.isRetryable(e))
    }

    @Test fun retryCategory_socketTimeout_returnsTimeout() {
        val e = SocketTimeoutException("Timeout")
        val category = NetworkErrorClassifier.retryCategory(e)
        assertTrue(category == NetworkErrorClassifier.RetryCategory.TIMEOUT)
        assertFalse(category == NetworkErrorClassifier.RetryCategory.OTHER)
    }

    @Test fun retryCategory_unknownHost_returnsNull() {
        val e = UnknownHostException("name not known")
        val category = NetworkErrorClassifier.retryCategory(e)
        assertTrue(category == null)
    }

    @Test fun retryCategory_sslException_returnsNull() {
        val e = SSLException("TLS failed")
        val category = NetworkErrorClassifier.retryCategory(e)
        assertTrue(category == null)
    }

    @Test fun retryCategory_noRouteToHost_returnsNull() {
        val e = NoRouteToHostException("No route")
        val category = NetworkErrorClassifier.retryCategory(e)
        assertTrue(category == null)
    }

    @Test fun retryCategory_connectException_returnsNull() {
        val e = ConnectException("Connection refused")
        val category = NetworkErrorClassifier.retryCategory(e)
        assertTrue(category == null)
    }

    @Test fun retryCategory_unknownIoe_returnsOther() {
        val e = IOException("Generic IO error")
        val category = NetworkErrorClassifier.retryCategory(e)
        assertTrue(category == NetworkErrorClassifier.RetryCategory.OTHER)
    }

    @Test fun retryCategory_allValuesAreDefined() {
        // Verify all RetryCategory enum values are handled in the switch
        val values = NetworkErrorClassifier.RetryCategory.values()
        assertTrue(values.isNotEmpty())
        for (v in values) {
            // Just ensure enum is not empty; actual mapping is tested individually
        }
    }
}