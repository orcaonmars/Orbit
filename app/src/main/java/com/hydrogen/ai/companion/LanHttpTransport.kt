package com.hydrogen.ai.companion

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

internal data class LanHttpResponse(val status: Int, val body: String)

/** The PC could not be reached at all (refused, no route, connect timeout). */
internal class LanConnectException(cause: Throwable) : IOException(cause.message, cause)

/** One reply from [LanHttpTransport.open], read as it arrives. Closing it closes the connection. */
internal class LanHttpStream internal constructor(
    val status: Int,
    /** Lower-case header names; a repeated header keeps its last value. */
    val headers: Map<String, String>,
    val body: InputStream,
    private val socket: Socket?
) : Closeable {
    val contentType: String? get() = headers["content-type"]

    fun readText(): String = body.readBytes().toString(Charsets.UTF_8)

    override fun close() {
        runCatching { body.close() }
        runCatching { socket?.close() }
    }
}

/**
 * A tiny HTTP/1.1 client for private-network addresses on the home Wi-Fi (docs/WIFI_MODE_SPEC.md).
 *
 * Android's network security config keeps plain HTTP blocked for everything except loopback/emulator hosts, and it
 * cannot allow IP ranges, so Wi-Fi requests to a private IPv4 literal come here instead of relaxing it app-wide.
 * Two users:
 * - The PC Companion ([exchange]): every body is AES-GCM encrypted end to end, the secret-free /health check, or the
 *   v2 pairing request, which carries a proof and never the pairing code. Chunked replies are refused.
 * - The user's own model server on the PC (LM Studio/Ollama, [open]): plain chat, models and health requests. The
 *   caller never sends an access key here (HttpGeminiHydrogenRelayBackend refuses to); chunked replies are accepted
 *   because those servers stream chat that way.
 *
 * No DNS: the caller passes the four address bytes. No redirects, no keep-alive.
 */
internal object LanHttpTransport {
    const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024
    /** A long streamed chat reply is far below this; it only stops a server that never ends. */
    const val MAX_STREAM_BYTES = 32L * 1024 * 1024
    private const val MAX_HEADER_BYTES = 64 * 1024
    private const val MAX_CHUNK_LINE_BYTES = 1024
    private val STATUS_LINE = Regex("""^HTTP/1\.[01] (\d{3})(?: .*)?$""")
    private val TOKEN = Regex("""^[A-Za-z0-9-]+$""")
    private val CHUNK_SIZE = Regex("""^[0-9A-Fa-f]{1,8}$""")

    fun exchange(
        address: ByteArray,
        port: Int,
        method: String,
        pathAndQuery: String,
        headers: Map<String, String>,
        body: ByteArray?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): LanHttpResponse {
        val request = buildRequest(address, port, method, pathAndQuery, headers, body)
        Socket().use { socket ->
            try {
                socket.connect(InetSocketAddress(InetAddress.getByAddress(address), port), connectTimeoutMs)
            } catch (e: IOException) {
                throw LanConnectException(e)
            }
            socket.soTimeout = readTimeoutMs
            socket.tcpNoDelay = true
            val out = socket.getOutputStream()
            out.write(request)
            out.flush()
            return readResponse(socket.getInputStream(), MAX_RESPONSE_BYTES)
        }
    }

    /**
     * Sends one request and returns the reply as soon as its headers arrive; the body is read from
     * [LanHttpStream.body] as it streams in. The caller must close the result. `readTimeoutMs` applies to each read.
     */
    fun open(
        address: ByteArray,
        port: Int,
        method: String,
        pathAndQuery: String,
        headers: Map<String, String>,
        body: ByteArray?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        maxBodyBytes: Long = MAX_STREAM_BYTES
    ): LanHttpStream {
        val request = buildRequest(address, port, method, pathAndQuery, headers, body)
        val socket = Socket()
        try {
            try {
                socket.connect(InetSocketAddress(InetAddress.getByAddress(address), port), connectTimeoutMs)
            } catch (e: IOException) {
                throw LanConnectException(e)
            }
            socket.soTimeout = readTimeoutMs
            socket.tcpNoDelay = true
            val out = socket.getOutputStream()
            out.write(request)
            out.flush()
            return readStreamingResponse(BufferedInputStream(socket.getInputStream()), maxBodyBytes, socket)
        } catch (e: Throwable) {
            runCatching { socket.close() }
            throw e
        }
    }

    internal fun buildRequest(
        address: ByteArray,
        port: Int,
        method: String,
        pathAndQuery: String,
        headers: Map<String, String>,
        body: ByteArray?
    ): ByteArray {
        require(address.size == 4) { "Wi-Fi addresses must be IPv4." }
        require(port in 1..65535) { "Invalid port." }
        require(method == "GET" || method == "POST") { "Unsupported method." }
        require(pathAndQuery.startsWith("/") && pathAndQuery.none { it.isWhitespace() || it.code < 0x21 || it.code > 0x7e }) {
            "Invalid request path."
        }
        val host = address.joinToString(".") { (it.toInt() and 0xff).toString() }
        val lines = ArrayList<String>()
        lines += "$method $pathAndQuery HTTP/1.1"
        lines += "Host: $host:$port"
        lines += "Connection: close"
        val reserved = setOf("host", "connection", "content-length", "transfer-encoding")
        for ((name, value) in headers) {
            require(TOKEN.matches(name)) { "Invalid header name." }
            require(name.lowercase() !in reserved) { "Header $name is set by the transport." }
            require(value.none { it == '\r' || it == '\n' || it.code == 0 }) { "Invalid header value." }
            lines += "$name: $value"
        }
        if (body != null) lines += "Content-Length: ${body.size}"
        val head = (lines.joinToString("\r\n") + "\r\n\r\n").toByteArray(Charsets.ISO_8859_1)
        return if (body == null) head else head + body
    }

    /** Reads one HTTP/1.x response. Honors Content-Length, else reads to EOF; refuses chunked bodies. */
    internal fun readResponse(input: InputStream, maxBodyBytes: Int): LanHttpResponse {
        val head = parseHead(readHead(input))
        val transferEncoding = head.headers["transfer-encoding"]
        if (transferEncoding != null && !transferEncoding.equals("identity", ignoreCase = true)) {
            throw IOException("The PC sent a chunked reply, which Orbit doesn't accept.")
        }
        val contentLength = head.contentLength
        val bodyBytes = if (contentLength != null) {
            if (contentLength > maxBodyBytes) throw IOException("The PC's reply is too large.")
            readExactly(input, contentLength.toInt())
        } else {
            readToEof(input, maxBodyBytes)
        }
        return LanHttpResponse(status = head.status, body = String(bodyBytes, Charsets.UTF_8))
    }

    /** Reads the status line and headers, then wraps the body: chunked, Content-Length or until the server closes. */
    internal fun readStreamingResponse(input: InputStream, maxBodyBytes: Long, socket: Socket? = null): LanHttpStream {
        val head = parseHead(readHead(input))
        val transferEncoding = head.headers["transfer-encoding"]?.trim()
        val contentLength = head.contentLength
        val body: InputStream = when {
            // RFC 9112 6.3: chunked wins over any Content-Length.
            transferEncoding.equals("chunked", ignoreCase = true) -> ChunkedBody(input, maxBodyBytes)
            transferEncoding != null && !transferEncoding.equals("identity", ignoreCase = true) ->
                throw IOException("The server sent a reply encoding Orbit doesn't support ($transferEncoding).")
            contentLength != null -> {
                if (contentLength > maxBodyBytes) throw IOException("The server's reply is too large.")
                LimitedBody(input, contentLength, mustReachLimit = true)
            }
            else -> LimitedBody(input, maxBodyBytes, mustReachLimit = false)
        }
        return LanHttpStream(head.status, head.headers, body, socket)
    }

    private class Head(val status: Int, val headers: Map<String, String>, val contentLength: Long?)

    private fun parseHead(headBytes: ByteArray): Head {
        val headerLines = String(headBytes, Charsets.ISO_8859_1).split("\r\n")
        val status = STATUS_LINE.matchEntire(headerLines.first())?.groupValues?.get(1)?.toInt()
            ?: throw IOException("The PC sent a reply Orbit doesn't understand.")
        val headers = LinkedHashMap<String, String>()
        var contentLength: Long? = null
        for (line in headerLines.drop(1)) {
            if (line.isEmpty()) continue
            val colon = line.indexOf(':')
            if (colon <= 0) throw IOException("The PC sent a malformed header.")
            val name = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            if (name == "content-length") {
                val parsed = value.toLongOrNull()?.takeIf { it >= 0 }
                    ?: throw IOException("The PC sent an invalid Content-Length.")
                if (contentLength != null && contentLength != parsed) {
                    throw IOException("The PC sent conflicting Content-Length headers.")
                }
                contentLength = parsed
            }
            headers[name] = value
        }
        return Head(status, headers, contentLength)
    }

    private fun readHead(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        var matched = 0 // progress through "\r\n\r\n"
        while (true) {
            val b = input.read()
            if (b == -1) throw IOException("The PC closed the connection before replying.")
            if (out.size() >= MAX_HEADER_BYTES) throw IOException("The PC's reply headers are too large.")
            out.write(b)
            matched = when {
                (matched == 0 || matched == 2) && b == '\r'.code -> matched + 1
                (matched == 1 || matched == 3) && b == '\n'.code -> matched + 1
                b == '\r'.code -> 1
                else -> 0
            }
            if (matched == 4) {
                val bytes = out.toByteArray()
                return bytes.copyOf(bytes.size - 4)
            }
        }
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val n = input.read(buffer, offset, length - offset)
            if (n == -1) throw IOException("The PC's reply was cut short.")
            offset += n
        }
        return buffer
    }

    private fun readToEof(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (true) {
            val n = input.read(chunk)
            if (n == -1) return out.toByteArray()
            if (out.size() + n > maxBytes) throw IOException("The PC's reply is too large.")
            out.write(chunk, 0, n)
        }
    }

    /**
     * A Content-Length body ([mustReachLimit]: ending early is an error) or a read-until-close body capped at [limit]
     * (more than that is an error, never a silent cut).
     */
    private class LimitedBody(
        private val input: InputStream,
        private val limit: Long,
        private val mustReachLimit: Boolean
    ) : InputStream() {
        private var consumed = 0L

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (mustReachLimit) {
                if (consumed >= limit) return -1
                val n = input.read(b, off, minOf(len.toLong(), limit - consumed).toInt())
                if (n == -1) throw IOException("The server's reply was cut short.")
                consumed += n
                return n
            }
            val n = input.read(b, off, len)
            if (n == -1) return -1
            consumed += n
            if (consumed > limit) throw IOException("The server's reply is too large.")
            return n
        }

        override fun close() = input.close()
    }

    /** Decodes `Transfer-Encoding: chunked` (RFC 9112 7.1) as it arrives. Trailers are read and dropped. */
    private class ChunkedBody(private val input: InputStream, private val limit: Long) : InputStream() {
        private var remainingInChunk = 0L
        private var total = 0L
        private var finished = false

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (finished) return -1
            if (remainingInChunk == 0L) {
                remainingInChunk = nextChunkSize()
                if (remainingInChunk == 0L) {
                    skipTrailers()
                    finished = true
                    return -1
                }
                total += remainingInChunk
                if (total > limit) throw IOException("The server's reply is too large.")
            }
            val n = input.read(b, off, minOf(len.toLong(), remainingInChunk).toInt())
            if (n == -1) throw IOException("The server's reply was cut short.")
            remainingInChunk -= n
            if (remainingInChunk == 0L) expectCrlf()
            return n
        }

        private fun nextChunkSize(): Long {
            val line = readLine()
            val size = line.substringBefore(';').trim()
            if (!CHUNK_SIZE.matches(size)) throw IOException("The server sent a malformed chunk.")
            return size.toLong(16)
        }

        private fun skipTrailers() {
            var count = 0
            while (readLine().isNotEmpty()) {
                if (++count > 64) throw IOException("The server sent too many trailers.")
            }
        }

        private fun expectCrlf() {
            if (input.read() != '\r'.code || input.read() != '\n'.code) {
                throw IOException("The server sent a malformed chunk.")
            }
        }

        /** One CRLF-terminated line without the CRLF. */
        private fun readLine(): String {
            val out = ByteArrayOutputStream()
            while (true) {
                val c = input.read()
                if (c == -1) throw IOException("The server's reply was cut short.")
                if (c == '\r'.code) {
                    if (input.read() != '\n'.code) throw IOException("The server sent a malformed chunk.")
                    return out.toString(Charsets.ISO_8859_1.name())
                }
                if (out.size() >= MAX_CHUNK_LINE_BYTES) throw IOException("The server sent a malformed chunk.")
                out.write(c)
            }
        }

        override fun close() = input.close()
    }
}
