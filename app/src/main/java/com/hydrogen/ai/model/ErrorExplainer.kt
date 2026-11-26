package com.hydrogen.ai.model

import com.hydrogen.ai.network.NetworkErrorClassifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Plain, numbered "what to do" steps for a failed request, and the text behind "Copy details". The message on the
 * failed card says what happened; these steps say how to fix it, in the order most likely to work.
 */
object ErrorExplainer {

    /**
     * [serviceName] is the hosted service ("OpenAI"), or null for your own server. [keyPage] is where that service
     * hands out keys, if Orbit knows it. [ownServer] is true for LM Studio, Ollama or another server on your network.
     */
    fun steps(
        error: ProviderError,
        serviceName: String? = null,
        keyPage: String? = null,
        ownServer: Boolean = false
    ): List<String> {
        val svc = serviceName ?: "the service"
        val site = serviceName?.let { "the $it website" } ?: "the service's website"
        val keyStep = keyPage?.let { "You can make a new key at ${hostOf(it)}." }
        return when (error.category) {
            ProviderErrorCategory.UNCONFIGURED -> listOf(
                "Open Models and providers.",
                "Pick a service and paste its key, or connect a model on your PC."
            )
            ProviderErrorCategory.PREFLIGHT_BLOCKED -> listOf(
                "Remove the file or image the message names.",
                "Send the message again."
            )
            ProviderErrorCategory.UNSUPPORTED_CAPABILITY -> listOf(
                "Pick a model that can do this. For images, pick one that can see images.",
                "Or remove the attachment and send text only."
            )
            ProviderErrorCategory.BAD_REQUEST_400 -> listOf(
                "Tap Retry once.",
                "If it fails again, remove attachments or start a new chat.",
                "If it keeps happening, tap Copy details and keep it for help."
            )
            ProviderErrorCategory.UNAUTHORIZED_RELAY_401 -> listOf(
                "Open Models and providers.",
                "Paste the key your server expects. If your server doesn't use a key, clear it."
            )
            ProviderErrorCategory.UNAUTHORIZED_UPSTREAM_401 -> listOfNotNull(
                "Open Models and providers and paste your key again, with nothing before or after it.",
                "Check that the key is for $svc and that it hasn't been deleted.",
                keyStep
            )
            ProviderErrorCategory.FORBIDDEN_403 -> listOfNotNull(
                "Check that your key can use this model. Some models need a paid plan or a verified account.",
                "Pick another model, or tap Find models to see what your key can use.",
                keyStep
            )
            ProviderErrorCategory.CONTENT_FILTERED -> listOf(
                "Reword the message and send it again.",
                "Or pick another model."
            )
            ProviderErrorCategory.QUOTA_DAILY_429 -> listOf(
                "Wait until the daily limit resets, usually the next day.",
                "Or switch to another service or a model on your PC. Orbit never switches to a paid service by itself."
            )
            ProviderErrorCategory.QUOTA_RATE_TPM_RPM_429 -> listOf(
                waitStep(error.retryAfterSeconds, "Wait a minute, then tap Retry."),
                "Shorter messages and fewer images use less of the limit."
            )
            ProviderErrorCategory.SERVER_5XX -> listOf(
                "Wait a moment, then tap Retry.",
                "If it keeps failing, pick another model."
            )
            ProviderErrorCategory.NETWORK_TIMEOUT -> networkSteps(error.sourceLayer, ownServer)
            ProviderErrorCategory.CANCELLED -> listOf("Tap Retry to send it again.")
            ProviderErrorCategory.UNKNOWN_PROTOCOL -> listOf(
                "Check the server address in Models and providers.",
                "Check that the format matches the service: most use the OpenAI format, Claude models use the Anthropic format.",
                "If it keeps happening, tap Copy details and keep it for help."
            )
            ProviderErrorCategory.OUT_OF_CREDITS -> listOf(
                "Add credits or turn on billing on $site.",
                "Or pick a service with a free tier, or a model on your PC.",
                "Orbit never pays for anything and never switches services by itself."
            )
            ProviderErrorCategory.MODEL_NOT_FOUND -> if (ownServer) {
                listOf(
                    "Make sure the model is downloaded and loaded in LM Studio or Ollama.",
                    "Then open Models and providers and tap Find models."
                )
            } else {
                listOf(
                    "Open Models and providers and tap Find models to see what your key can use.",
                    "Pick one of those models, or check the spelling of the model name."
                )
            }
            ProviderErrorCategory.CONTEXT_TOO_LONG -> listOf(
                "Start a new chat, or remove large files from this one.",
                "Or pick a model that can read more at once."
            )
            ProviderErrorCategory.REQUEST_TOO_LARGE -> listOf(
                "Remove large images or files.",
                "Or split the message into smaller parts."
            )
            ProviderErrorCategory.OVERLOADED -> listOf(
                waitStep(error.retryAfterSeconds, "Wait a minute, then tap Retry."),
                "Or pick another model for now."
            )
            ProviderErrorCategory.REGION_UNAVAILABLE -> listOf(
                "This service isn't offered where you are.",
                "Pick another service, or a model on your PC."
            )
            ProviderErrorCategory.INTERRUPTED -> listOf(
                "The part that arrived is kept, but it may stop mid-sentence.",
                "Tap Retry for a full answer.",
                "If it keeps happening, check your connection or pick another model."
            )
        }
    }

    private fun networkSteps(layer: String, ownServer: Boolean): List<String> = when (layer) {
        NetworkErrorClassifier.DNS -> listOf(
            "Check that Wi-Fi or mobile data is on.",
            "If you typed the address, check its spelling in Models and providers."
        )
        NetworkErrorClassifier.REFUSED -> if (ownServer) {
            listOf(
                "Make sure the server is running on your PC, with its local server turned on.",
                "Check that this phone is on the same Wi-Fi and that the PC address is right.",
                "Then tap Retry."
            )
        } else {
            listOf(
                "Check the address in Models and providers.",
                "Wait a moment, then tap Retry."
            )
        }
        NetworkErrorClassifier.TIMEOUT -> if (ownServer) {
            listOf(
                "Big models can take a while to load on a PC. Wait for it to finish, then tap Retry.",
                "Or pick a smaller model."
            )
        } else {
            listOf(
                "Tap Retry.",
                "If it keeps timing out, try a smaller or faster model."
            )
        }
        NetworkErrorClassifier.TLS -> listOf(
            "Check that the phone's date and time are right.",
            "Try another network. Some public Wi-Fi blocks secure connections."
        )
        NetworkErrorClassifier.UNREACHABLE -> listOf(
            "Check that this phone and the server are on the same network.",
            "Check the address in Models and providers."
        )
        else -> if (ownServer) {
            listOf(
                "Make sure the server on your PC is running.",
                "Check that this phone can reach it, then tap Retry."
            )
        } else {
            listOf(
                "Check your connection.",
                "Then tap Retry."
            )
        }
    }

    private fun waitStep(retryAfterSeconds: Int?, fallback: String): String = when {
        retryAfterSeconds == null || retryAfterSeconds <= 0 -> fallback
        retryAfterSeconds < 90 -> "Wait about $retryAfterSeconds seconds, then tap Retry."
        else -> "Wait about ${(retryAfterSeconds + 59) / 60} minutes, then tap Retry."
    }

    /**
     * The text behind "Copy details": enough to understand or report the problem, with no chat text and no keys.
     * [appVersion] is the version name shown in About.
     */
    fun report(entry: ErrorLogEntry, appVersion: String, timeZone: TimeZone = TimeZone.getDefault()): String =
        buildString {
            appendLine("Orbit error report")
            appendLine("Time: ${formatTime(entry.atEpochMs, timeZone)}")
            appendLine("Problem: ${entry.title}")
            appendLine("Service: ${entry.service ?: entry.serverHost.ifEmpty { "Your server" }}")
            if (entry.modelId.isNotBlank()) appendLine("Model: ${entry.modelId}")
            entry.httpStatus?.let { appendLine("Status: $it") }
            appendLine("Where: ${entry.sourceLayer}")
            entry.retryAfterSeconds?.let { appendLine("Retry after: $it s") }
            appendLine("Message: ${entry.message}")
            if (entry.detail.isNotBlank()) appendLine("Details: ${entry.detail}")
            appendLine("App: Orbit $appVersion")
            append("Chat text and API keys are never included.")
        }

    /** Every entry, newest first, for "Copy all" and "Share" in the error log. */
    fun reportAll(entries: List<ErrorLogEntry>, appVersion: String, timeZone: TimeZone = TimeZone.getDefault()): String =
        if (entries.isEmpty()) {
            "Orbit error log is empty."
        } else {
            entries.joinToString("\n\n") { report(it, appVersion, timeZone) }
        }

    fun formatTime(epochMs: Long, timeZone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { this.timeZone = timeZone }.format(Date(epochMs))

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull()?.removePrefix("www.") ?: url
}
