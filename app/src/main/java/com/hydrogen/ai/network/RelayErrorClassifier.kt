package com.hydrogen.ai.network

import com.hydrogen.ai.model.ProviderError
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.ProviderRoute
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Turns an error reply from any AI service into a [ProviderError] a person can act on.
 *
 * Covers the OpenAI format (and the many services that copy it), Google's format and Anthropic's format. Messages name
 * the service when it is known ("OpenAI didn't accept your API key"), never repeat the server's own text, and always
 * end with a next step. The server's text, with keys hidden, goes to [ProviderError.technicalDetail] for "Details".
 */
object RelayErrorClassifier {
    private val json = Json { ignoreUnknownKeys = true }

    const val LAYER_BILLING = "provider_billing"
    const val LAYER_REGION = "provider_region"
    const val LAYER_CONTEXT = "provider_context"
    const val LAYER_MODEL = "provider_model"
    const val LAYER_SIZE = "provider_size"
    const val LAYER_BUSY = "provider_busy"
    const val LAYER_KEY = "provider"

    /** Returns true if the error is transient and a retry is likely to succeed. */
    fun isTransientError(category: ProviderErrorCategory): Boolean = when (category) {
        ProviderErrorCategory.OVERLOADED -> true
        ProviderErrorCategory.QUOTA_RATE_TPM_RPM_429 -> true
        ProviderErrorCategory.QUOTA_DAILY_429 -> false
        ProviderErrorCategory.SERVER_5XX -> true
        ProviderErrorCategory.CONTENT_FILTERED -> false
        ProviderErrorCategory.BAD_REQUEST_400 -> false
        ProviderErrorCategory.FORBIDDEN_403 -> false
        ProviderErrorCategory.UNAUTHORIZED_RELAY_401 -> false
        ProviderErrorCategory.UNAUTHORIZED_UPSTREAM_401 -> false
        ProviderErrorCategory.MODEL_NOT_FOUND -> false
        ProviderErrorCategory.CONTEXT_TOO_LONG -> false
        ProviderErrorCategory.REQUEST_TOO_LARGE -> false
        ProviderErrorCategory.UNKNOWN_PROTOCOL -> false
        ProviderErrorCategory.OUT_OF_CREDITS -> false
        ProviderErrorCategory.REGION_UNAVAILABLE -> false
        else -> false
    }

    /** An error event inside a stream, or null when the event isn't an error. */
    fun classifySseErrorPayload(
        dataPayload: String,
        route: ProviderRoute = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        serviceName: String? = null
    ): ProviderError? {
        val trimmed = dataPayload.trim()
        if (!trimmed.startsWith("{")) return null
        val root = try {
            json.parseToJsonElement(trimmed) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return null
        val errorEl = root["error"]?.takeUnless { it == JsonNull } ?: return null
        val errorObj = errorEl as? JsonObject
        val codeValue = errorObj?.get("code") as? JsonPrimitive
        val code = codeValue?.intOrNull?.takeIf { it in 400..599 }
            ?: statusForErrorType(codeValue?.contentOrNull)
            ?: statusForErrorType((errorObj?.get("type") as? JsonPrimitive)?.contentOrNull)
            ?: statusForErrorType((errorObj?.get("status") as? JsonPrimitive)?.contentOrNull)
            ?: 500
        return classifyHttpError(
            statusCode = code,
            responseBody = trimmed,
            route = route,
            serviceName = serviceName
        )
    }

    /** Anthropic-format error types carry no status inside a stream; these are the statuses they come with. */
    internal fun statusForErrorType(type: String?): Int? = when (type?.lowercase()) {
        "invalid_request_error", "invalid_argument", "context_length_exceeded" -> 400
        "authentication_error", "invalid_api_key", "unauthenticated" -> 401
        "billing_error", "insufficient_quota", "insufficient_credits" -> 402
        "permission_error", "permission_denied" -> 403
        "not_found_error", "model_not_found", "not_found" -> 404
        "request_too_large" -> 413
        "rate_limit_error", "rate_limit_exceeded", "resource_exhausted" -> 429
        "api_error", "internal" -> 500
        "unavailable" -> 503
        "overloaded_error" -> 529
        else -> null
    }

    fun classifyHttpError(
        statusCode: Int,
        responseBody: String,
        retryAfterHeader: String? = null,
        route: ProviderRoute = ProviderRoute.GEMINI_HYDROGEN_RELAY,
        /** "OpenAI", "Anthropic", "Google"... or null for the user's own server or an unknown one. */
        serviceName: String? = null
    ): ProviderError {
        val bodyLower = responseBody.lowercase()
        val retryAfterSeconds = parseRetryAfterSeconds(retryAfterHeader) ?: parseRetryDelayFromBody(bodyLower)
        val svc = serviceName ?: if (route == ProviderRoute.GOOGLE_AI_STUDIO_DIRECT) "Google" else null
        val detail = detailOf(responseBody)

        fun error(
            category: ProviderErrorCategory,
            message: String,
            layer: String,
            retry: Int? = null
        ) = ProviderError(
            category = category,
            httpStatus = statusCode,
            userMessage = message,
            technicalDetail = detail,
            retryAfterSeconds = retry,
            sourceLayer = layer
        )

        if (bodyLower.contains("content_filter") ||
            bodyLower.contains("prohibited_content") ||
            bodyLower.contains("blocked by content filtering")
        ) {
            return error(
                ProviderErrorCategory.CONTENT_FILTERED,
                "The AI service's safety filter blocked this reply. Try rewording your message.",
                "provider_policy"
            )
        }

        if (looksOutOfCredits(statusCode, bodyLower)) {
            val message = if (svc != null) {
                "Your $svc account is out of credits or has no billing set up. Add credits on the $svc website, or " +
                    "pick another service. Orbit never pays for anything."
            } else {
                "Your account with this service is out of credits or has no billing set up. Add credits on its " +
                    "website, or pick another service. Orbit never pays for anything."
            }
            return error(ProviderErrorCategory.OUT_OF_CREDITS, message, LAYER_BILLING)
        }

        if (REGION_WORDS.any { bodyLower.contains(it) }) {
            return error(
                ProviderErrorCategory.REGION_UNAVAILABLE,
                "${svc ?: "This service"} isn't available in your country or region. Pick another service.",
                LAYER_REGION
            )
        }

        if (statusCode in setOf(400, 413, 422) && CONTEXT_WORDS.any { bodyLower.contains(it) }) {
            return error(
                ProviderErrorCategory.CONTEXT_TOO_LONG,
                "This chat is longer than the model can read at once. Start a new chat, remove large attachments, " +
                    "or pick a model that reads more.",
                LAYER_CONTEXT
            )
        }

        if (statusCode in setOf(400, 422) &&
            listOf("reasoning_effort", "thinking_level", "thinkinglevel", "thinking level", "output_config", "reasoning.effort")
                .any { bodyLower.contains(it) } &&
            listOf("unsupported", "not supported", "invalid", "not allowed", "unknown", "unrecognized", "not permitted")
                .any { bodyLower.contains(it) }) {
            return error(ProviderErrorCategory.BAD_REQUEST_400,
                "The model or provider rejected this reasoning setting. Select Automatic in the composer or Models & API keys, then retry.",
                "provider_reasoning")
        }

        if (looksModelNotFound(statusCode, bodyLower, hosted = svc != null)) {
            return error(
                ProviderErrorCategory.MODEL_NOT_FOUND,
                "${svc ?: "The server"} doesn't have this model, or your key can't use it. Pick another model, or " +
                    "tap Load models in Models & API keys.",
                LAYER_MODEL
            )
        }

        if (statusCode == 413 || bodyLower.contains("request_too_large")) {
            return error(
                ProviderErrorCategory.REQUEST_TOO_LARGE,
                "This message is too large for ${svc ?: "the server"}. Attach fewer or smaller images, or shorten " +
                    "the message.",
                LAYER_SIZE
            )
        }

        if (statusCode == 529 || bodyLower.contains("overloaded") ||
            (statusCode == 503 && (bodyLower.contains("high demand") || bodyLower.contains("capacity")))
        ) {
            return error(
                ProviderErrorCategory.OVERLOADED,
                "${svc ?: "The server"} is busy right now. Try again in a minute, or pick another model.",
                LAYER_BUSY,
                retryAfterSeconds
            )
        }

        val googleWords = bodyLower.contains("google") || bodyLower.contains("ai studio") ||
            bodyLower.contains("api_key_invalid") || bodyLower.contains("api key not valid")
        val keyWords = KEY_WORDS.any { bodyLower.contains(it) }
        val keyOwner = svc ?: if (googleWords) "Google" else null
        val keyLayer = if (route == ProviderRoute.GOOGLE_AI_STUDIO_DIRECT || (svc == null && googleWords)) {
            "upstream_google"
        } else {
            LAYER_KEY
        }
        val keyMessage = if (keyOwner != null) {
            "$keyOwner didn't accept your API key. Check or replace it in Models & API keys."
        } else {
            "The service didn't accept your API key. Check or replace it in Models & API keys."
        }

        return when (statusCode) {
            400 -> if (keyWords) {
                error(ProviderErrorCategory.UNAUTHORIZED_UPSTREAM_401, keyMessage, keyLayer)
            } else {
                error(
                    ProviderErrorCategory.BAD_REQUEST_400,
                    "${svc ?: "The server"} couldn't handle this request, so retrying it unchanged won't help. " +
                        "Try a shorter message, fewer attachments or another model.",
                    if (svc != null) LAYER_KEY else "provider_or_relay"
                )
            }

            401 -> {
                val looksUpstream = svc != null || googleWords || bodyLower.contains("upstream")
                if (looksUpstream) {
                    error(ProviderErrorCategory.UNAUTHORIZED_UPSTREAM_401, keyMessage, keyLayer)
                } else {
                    error(
                        ProviderErrorCategory.UNAUTHORIZED_RELAY_401,
                        "Your server didn't accept the key. Check it in Models & API keys.",
                        "local_relay"
                    )
                }
            }

            403 -> if (keyWords) {
                error(ProviderErrorCategory.UNAUTHORIZED_UPSTREAM_401, keyMessage, keyLayer)
            } else {
                error(
                    ProviderErrorCategory.FORBIDDEN_403,
                    "${svc ?: "The server"} refused access to this model or feature. Check your key's permissions " +
                        "or pick another model.",
                    "provider_or_relay"
                )
            }

            429 -> classify429(statusCode, detail, bodyLower, retryAfterSeconds, svc)

            in 500..599 -> error(
                ProviderErrorCategory.SERVER_5XX,
                "${svc ?: "The AI server"} had a temporary problem. Try again in a moment.",
                "server",
                retryAfterSeconds
            )

            404 -> error(
                ProviderErrorCategory.UNKNOWN_PROTOCOL,
                "Orbit couldn't find a chat service at this address. Check the server address in Models & API keys.",
                "relay"
            )

            else -> error(
                ProviderErrorCategory.UNKNOWN_PROTOCOL,
                if (svc != null) {
                    "$svc sent a reply Orbit didn't expect. Try again, or pick another model."
                } else {
                    "The server sent a reply Orbit didn't expect. Check the server address in Set up."
                },
                "relay"
            )
        }
    }

    private val REGION_WORDS = listOf(
        "user location is not supported",
        "unsupported_country",
        "not available in your region",
        "not available in your country",
        "country, region, or territory not supported",
        "unsupported_region"
    )

    private val CONTEXT_WORDS = listOf(
        "context_length_exceeded",
        "maximum context length",
        "context length",
        "context window",
        "prompt is too long",
        "input token count",
        "too many tokens",
        "input is too long",
        "reduce the length of the messages"
    )

    private val KEY_WORDS = listOf(
        "api_key_invalid",
        "api key not valid",
        "api_key_expired",
        "api key expired",
        "invalid api key",
        "invalid_api_key",
        "incorrect api key",
        "invalid x-api-key",
        "reported as leaked",
        "authentication_error"
    )

    private fun looksOutOfCredits(status: Int, body: String): Boolean {
        if (status == 402) return true
        if (body.contains("insufficient_quota") || body.contains("billing_error") ||
            body.contains("credit balance is too low") || body.contains("insufficient balance") ||
            body.contains("insufficient credits") || body.contains("insufficient_credits") ||
            body.contains("billing_hard_limit")
        ) {
            return true
        }
        return status == 403 && (body.contains("billing") || body.contains("credits"))
    }

    private fun looksModelNotFound(status: Int, body: String, hosted: Boolean): Boolean {
        if (body.contains("model_not_found") || body.contains("model not found") || body.contains("no such model") ||
            body.contains("unknown model") || body.contains("invalid model") || body.contains("is not a valid model")
        ) {
            return true
        }
        if (status == 404) return hosted || body.contains("model")
        return status == 400 && body.contains("model") && (body.contains("does not exist") || body.contains("not found"))
    }

    private fun classify429(
        status: Int,
        detail: String,
        bodyLower: String,
        retryAfter: Int?,
        svc: String?
    ): ProviderError {
        val isRate = bodyLower.contains("per_minute") ||
            bodyLower.contains("perminute") ||
            bodyLower.contains("requests per minute") ||
            bodyLower.contains("tokens per minute") ||
            bodyLower.contains("rate_limit") ||
            bodyLower.contains("rate limit") ||
            bodyLower.contains("too many requests") ||
            bodyLower.contains("rpm") ||
            bodyLower.contains("tpm")
        val isDaily = !isRate && (
            bodyLower.contains("daily") || bodyLower.contains("per_day") ||
                bodyLower.contains("perday") || bodyLower.contains("free_tier") ||
                bodyLower.contains("requests per day") || bodyLower.contains("quota_exhausted") ||
                bodyLower.contains("resource_exhausted")
            )
        return if (isDaily) {
            ProviderError(
                category = ProviderErrorCategory.QUOTA_DAILY_429,
                httpStatus = status,
                userMessage = "You've used today's free limit for this key. It resets tomorrow. Orbit never switches to a " +
                    "paid service on its own.",
                technicalDetail = detail,
                retryAfterSeconds = retryAfter,
                sourceLayer = "upstream_quota_daily"
            )
        } else {
            val waitHint = retryAfter?.let { if (it == 1) " Try again in 1 second." else " Try again in $it seconds." }
                ?: " Wait a minute, then try again."
            val lead = if (svc != null) "Too many requests to $svc in a short time." else "Too many requests in a short time."
            ProviderError(
                category = ProviderErrorCategory.QUOTA_RATE_TPM_RPM_429,
                httpStatus = status,
                userMessage = "$lead$waitHint",
                technicalDetail = detail,
                retryAfterSeconds = retryAfter,
                sourceLayer = "upstream_quota_rate"
            )
        }
    }

    /**
     * The server's own words for "Details": "type: message" from a JSON error when there is one, otherwise the start
     * of the body. Keys are hidden either way.
     */
    fun detailOf(responseBody: String): String {
        val parsed = runCatching { json.parseToJsonElement(responseBody.trim()) }.getOrNull()
        val text = (parsed as? JsonObject)?.let { root -> serverMessage(root) } ?: responseBody.take(400)
        return SecretRedactor.redact(text).take(400).ifBlank { responseBody.take(400).ifBlank { "(empty reply)" } }
    }

    private fun serverMessage(root: JsonObject): String? {
        val err: JsonElement? = root["error"]
        val obj = err as? JsonObject
        val message = (obj?.get("message") as? JsonPrimitive)?.contentOrNull
            ?: (err as? JsonPrimitive)?.contentOrNull
            ?: (root["message"] as? JsonPrimitive)?.contentOrNull
            ?: (root["detail"] as? JsonPrimitive)?.contentOrNull
            ?: return null
        val kind = listOf("type", "status", "code")
            .mapNotNull { (obj?.get(it) as? JsonPrimitive)?.contentOrNull }
            .firstOrNull { it.isNotBlank() && it != "null" }
        return if (kind != null) "$kind: $message" else message
    }

    fun parseRetryAfterSeconds(headerValue: String?): Int? {
        val raw = headerValue?.trim().orEmpty()
        return if (raw.isEmpty()) null else raw.toIntOrNull()?.coerceAtLeast(1)
    }

    private fun parseRetryDelayFromBody(bodyLower: String): Int? =
        Regex(""""retrydelay"\s*:\s*"(\d+)s"""").find(bodyLower)?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1)


}
