package com.hydrogen.ai.model

import com.hydrogen.ai.network.HttpGeminiHydrogenRelayBackend

/** One-tap base URLs for private, OpenAI-compatible model servers running on the user's own PC. */
data class LocalServerPreset(
    val label: String,
    val baseUrl: String
)

object LocalServerPresets {
    /** Android emulator alias for the host PC's loopback interface. */
    const val EMULATOR_HOST = "10.0.2.2"

    /** On a real phone the PC is reached on loopback through a USB `adb reverse` tunnel. */
    const val DEVICE_HOST = "127.0.0.1"

    /** Host that reaches the user's PC; MainActivity sets [DEVICE_HOST] once at start-up on a real phone. */
    @Volatile
    var pcHost: String = EMULATOR_HOST

    fun pcUrl(port: Int): String = "http://$pcHost:$port"

    private val SERVERS = listOf("LM Studio" to 1234, "Ollama" to 11434)

    val presets: List<LocalServerPreset>
        get() = SERVERS.map { (label, port) -> LocalServerPreset(label, pcUrl(port)) }

    /** Trimmed http(s) base URL, or null when blank, not http(s), or carrying embedded credentials. */
    private fun safeHttpUrl(baseUrl: String): String? {
        val url = baseUrl.trim().trimEnd('/')
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return null
        if (url.substringAfter("://").substringBefore('/').contains('@')) return null
        return url
    }

    /** On this PC: loopback (USB `adb reverse`) or the emulator's host alias. */
    fun isLocalServer(baseUrl: String): Boolean =
        safeHttpUrl(baseUrl)?.let { HttpGeminiHydrogenRelayBackend.isLoopbackOrEmulatorHost(it) } == true

    /**
     * On the home Wi-Fi: plain http to a private IPv4 address (e.g. http://192.168.1.20:1234). Chats there are not
     * encrypted on the local network, and Orbit never sends an access key to it.
     */
    fun isWifiServer(baseUrl: String): Boolean =
        safeHttpUrl(baseUrl)?.let { HttpGeminiHydrogenRelayBackend.isWifiModelHost(it) } == true

    /** The user's own server, on this PC or on the home Wi-Fi: no key needed, nothing leaves the home network. */
    fun isPrivateServer(baseUrl: String): Boolean = isLocalServer(baseUrl) || isWifiServer(baseUrl)

    /**
     * The same servers at the PC's Wi-Fi address, which the phone learned when it paired with the PC Companion over
     * Wi-Fi (e.g. "192.168.1.20"). Empty when there is no such address.
     */
    fun wifiPresets(pcWifiHost: String?): List<LocalServerPreset> {
        val host = pcWifiHost?.trim().orEmpty()
        if (host.isEmpty() || !isWifiServer("http://$host:1")) return emptyList()
        return SERVERS.map { (label, port) -> LocalServerPreset("$label (Wi-Fi)", "http://$host:$port") }
    }

    /** Matches a preset on either PC host, so a config saved on the emulator is still recognised on a phone. */
    fun matchingPreset(baseUrl: String): LocalServerPreset? {
        val normalized = baseUrl.trim().trimEnd('/').lowercase()
        val match = SERVERS.firstOrNull { (_, port) ->
            normalized == "http://$EMULATOR_HOST:$port" || normalized == "http://$DEVICE_HOST:$port"
        } ?: return null
        return LocalServerPreset(match.first, pcUrl(match.second))
    }

    /** A Wi-Fi address on LM Studio's or Ollama's usual port, kept as typed (never rewritten to the USB address). */
    fun matchingWifiPreset(baseUrl: String): LocalServerPreset? {
        val url = baseUrl.trim().trimEnd('/')
        if (!isWifiServer(url)) return null
        val port = runCatching { java.net.URI(url).port }.getOrNull() ?: return null
        val label = SERVERS.firstOrNull { it.second == port }?.first ?: return null
        return LocalServerPreset("$label (Wi-Fi)", url)
    }

    /** Short helper text under the relay URL field, or null when nothing useful can be said yet. */
    fun hintFor(baseUrl: String): String? = when {
        baseUrl.isBlank() ->
            "Running LM Studio or Ollama on your PC? Tap a preset. A phone reaches the PC over USB " +
                "(adb reverse tcp:1234 tcp:1234) or your home Wi-Fi; other servers need an https:// address."
        isLocalServer(baseUrl) ->
            "Local server: token optional. Enter the exact model id it lists, then Test connection."
        isWifiServer(baseUrl) ->
            "Server on your Wi-Fi: no key is sent, and chats are not encrypted on your home network. " +
                "LM Studio needs \"Serve on Local Network\" on."
        else -> null
    }
}
