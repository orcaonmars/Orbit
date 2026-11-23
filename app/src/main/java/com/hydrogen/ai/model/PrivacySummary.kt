package com.hydrogen.ai.model

import java.net.URI

/** Everything the Privacy & data page needs to describe what Orbit keeps and what it sends. */
data class PrivacyFacts(
    val route: ProviderRoute,
    /** The base URL Orbit actually sends chats to (RelayConfig.effectiveBaseUrl). */
    val endpointUrl: String,
    val companionPaired: Boolean,
    val chatCount: Int,
    val memoryCount: Int,
    val memoryEnabled: Boolean,
    val savedPromptCount: Int
)

data class PrivacySection(val title: String, val lines: List<String>)

/** Where chat messages end up, from most private to least. */
enum class MessageDestination { NOWHERE, THIS_DEVICE, EMULATOR_HOST, LOCAL_NETWORK, INTERNET }

/**
 * Plain-language text for the Privacy & data page. Pure Kotlin so every sentence is unit-tested: the page must
 * never claim "stays on this device" unless the endpoint really is this device.
 */
object PrivacySummary {

    /** Lower-case host of [url], without IPv6 brackets, or null when it can't be parsed. */
    fun hostOf(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
        val host = runCatching { URI(withScheme).host }.getOrNull() ?: return null
        return host.lowercase().trim('[', ']').ifBlank { null }
    }

    fun destinationOf(route: ProviderRoute, endpointUrl: String): MessageDestination {
        if (route == ProviderRoute.LOCAL_OFFLINE) return MessageDestination.NOWHERE
        val host = hostOf(endpointUrl) ?: return MessageDestination.NOWHERE
        return when {
            isLoopback(host) -> MessageDestination.THIS_DEVICE
            host == "10.0.2.2" -> MessageDestination.EMULATOR_HOST
            isPrivateNetwork(host) -> MessageDestination.LOCAL_NETWORK
            else -> MessageDestination.INTERNET
        }
    }

    fun sections(facts: PrivacyFacts): List<PrivacySection> = listOf(
        PrivacySection("Where your messages go", messageLines(facts)),
        PrivacySection("What stays on this phone", storedLines(facts)),
        PrivacySection("Your PC", pcLines(facts.companionPaired)),
        PrivacySection("Microphone", MICROPHONE_LINES),
        PrivacySection(
            "No tracking",
            listOf(
                "Orbit has no analytics, ads or crash reporting, and sends nothing to Orbit's makers.",
                "Android cloud backup is turned off for Orbit, so your chats aren't copied to a backup account."
            )
        )
    )

    /** Plain text of the whole page, e.g. for TalkBack or copying. */
    fun asText(facts: PrivacyFacts): String =
        sections(facts).joinToString("\n\n") { section ->
            section.title + "\n" + section.lines.joinToString("\n") { "- $it" }
        }

    private fun messageLines(facts: PrivacyFacts): List<String> {
        val host = hostOf(facts.endpointUrl)
        val destination = destinationOf(facts.route, facts.endpointUrl)
        val first = when (destination) {
            MessageDestination.NOWHERE ->
                if (facts.route == ProviderRoute.LOCAL_OFFLINE) {
                    "The on-device model isn't installed, so no messages are sent anywhere."
                } else {
                    "No server is set up yet, so no messages are sent anywhere."
                }
            MessageDestination.THIS_DEVICE ->
                "Your messages go to a model server running on this phone ($host). They stay on this device."
            MessageDestination.EMULATOR_HOST ->
                "Your messages go to 10.0.2.2, which in the Android emulator is the computer running it. They " +
                    "don't leave that computer unless the server there forwards them."
            MessageDestination.LOCAL_NETWORK ->
                "Your messages go to $host on your local network. They leave this phone but not your network, " +
                    "unless that server forwards them." +
                    when {
                        LocalServerPresets.isWifiServer(facts.endpointUrl) ->
                            " Plain http is not encrypted, so others on the same Wi-Fi could read them. Orbit sends " +
                                "no access key there."
                        facts.endpointUrl.trim().lowercase().startsWith("http://") ->
                            " Plain http is not encrypted, so others on the same Wi-Fi could read them."
                        else -> ""
                    }
            MessageDestination.INTERNET ->
                if (facts.route == ProviderRoute.GOOGLE_AI_STUDIO_DIRECT) {
                    "Your messages, and any photos or files you attach, are sent to Google ($host) with your own " +
                        "API key. Google's terms for that key apply."
                } else {
                    "Your messages, and any photos or files you attach, are sent over the internet to $host. " +
                        "That service's own privacy terms apply."
                }
        }
        // Nothing is sent, so "only this conversation is sent" and "notes go to the same place" would
        // contradict the first line.
        if (destination == MessageDestination.NOWHERE) return listOf(first)
        val memory = when {
            facts.memoryCount == 0 -> null
            !facts.memoryEnabled -> "Memory is off, so your memory notes aren't sent."
            facts.memoryCount == 1 -> "Your memory note is added to each chat, so it goes to the same place."
            else -> "Your ${facts.memoryCount} memory notes are added to each chat, so they go to the same place."
        }
        return listOfNotNull(
            first,
            "Only the conversation you're in is sent, never your other chats.",
            memory
        )
    }

    private fun storedLines(facts: PrivacyFacts): List<String> = listOf(
        if (facts.chatCount == 0) "No chats yet." else "${plural(facts.chatCount, "chat")}, including attached photos and files.",
        "${plural(facts.memoryCount, "memory note")}" + if (facts.memoryEnabled) "." else " (memory is off).",
        "${plural(facts.savedPromptCount, "saved prompt")}, plus your personas, appearance and connection settings.",
        "API keys are encrypted with the Android Keystore and are never shown again after you save them."
    )

    /** True for any phone: Orbit has no recording of its own, only the recognizer's text. */
    internal val MICROPHONE_LINES = listOf(
        "Orbit uses the microphone only while the recording bar or voice chat is on screen.",
        "Your phone's speech recognizer turns what you say into text, on the phone when it can. Orbit never " +
            "saves the audio.",
        "Dictated words stay in the message box until you send them."
    )

    private fun pcLines(paired: Boolean): List<String> =
        if (paired) {
            listOf(
                "Your PC is paired. Orbit contacts it only when you ask, over an encrypted session.",
                "Nothing happens on the PC until it's approved there."
            )
        } else {
            listOf("No PC is paired, so Orbit doesn't talk to any computer.")
        }

    private fun plural(count: Int, noun: String): String =
        when (count) {
            0 -> "No ${noun}s"
            1 -> "1 $noun"
            else -> "$count ${noun}s"
        }

    private fun isLoopback(host: String): Boolean =
        host == "localhost" || host.endsWith(".localhost") || host == "::1" || host == "0:0:0:0:0:0:0:1" ||
            host.startsWith("127.")

    private fun isPrivateNetwork(host: String): Boolean {
        if (host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home.arpa")) return true
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        return octets[0] == 10 ||
            (octets[0] == 192 && octets[1] == 168) ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 169 && octets[1] == 254)
    }
}
