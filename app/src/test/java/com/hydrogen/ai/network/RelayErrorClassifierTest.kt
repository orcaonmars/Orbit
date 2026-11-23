package com.hydrogen.ai.network

import com.hydrogen.ai.model.ProviderErrorCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayErrorClassifierTest {
    @Test fun dailyRequestQuotaIsDistinguishedFromRateLimit() {
        val error = RelayErrorClassifier.classifyHttpError(
            429, """{"error":{"message":"Quota exceeded for metric generate_content_free_tier_requests, requests per day limit exceeded"}}"""
        )
        assertEquals(ProviderErrorCategory.QUOTA_DAILY_429, error.category)
        assertEquals("upstream_quota_daily", error.sourceLayer)
    }

    @Test fun temporaryRateLimitPreservesRetryAfter() {
        val error = RelayErrorClassifier.classifyHttpError(
            429, """{"error":{"message":"Requests per minute exceeded"}}""", "12"
        )
        assertEquals(ProviderErrorCategory.QUOTA_RATE_TPM_RPM_429, error.category)
        assertEquals(12, error.retryAfterSeconds)
    }

    @Test fun localRelay401DoesNotMasqueradeAsGoogleOutage() {
        val error = RelayErrorClassifier.classifyHttpError(401, """{"error":"Local authentication required"}""")
        assertEquals(ProviderErrorCategory.UNAUTHORIZED_RELAY_401, error.category)
        assertEquals("local_relay", error.sourceLayer)
    }

    @Test fun upstream401IsClassifiedSeparately() {
        val error = RelayErrorClassifier.classifyHttpError(401, """{"error":"Google API_KEY_INVALID"}""")
        assertEquals(ProviderErrorCategory.UNAUTHORIZED_UPSTREAM_401, error.category)
    }

    @Test fun invalidRequestDoesNotBecomeTransientRetry() {
        assertEquals(ProviderErrorCategory.BAD_REQUEST_400,
            RelayErrorClassifier.classifyHttpError(400, "Invalid JSON schema").category)
    }

    @Test fun retryAfterInvalidHeaderIsSafelyIgnored() {
        assertNull(RelayErrorClassifier.parseRetryAfterSeconds("not-a-number"))
        assertEquals(1, RelayErrorClassifier.parseRetryAfterSeconds("0"))
    }

    @Test fun googleAiStudio400ApiKeyInvalidIsClassifiedAsUpstreamAuthFailure() {
        val error = RelayErrorClassifier.classifyHttpError(
            statusCode = 400,
            responseBody = """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}""",
            route = com.hydrogen.ai.model.ProviderRoute.GOOGLE_AI_STUDIO_DIRECT
        )
        assertEquals(ProviderErrorCategory.UNAUTHORIZED_UPSTREAM_401, error.category)
        assertEquals(400, error.httpStatus)
        assertEquals("upstream_google", error.sourceLayer)
    }

    @Test fun freeTierPerMinute429IsClassifiedAsRateNotDaily() {
        val error = RelayErrorClassifier.classifyHttpError(
            statusCode = 429,
            responseBody = """{"error":{"code":429,"message":"Quota exceeded for quota metric 'GenerateRequestsPerMinutePerProjectPerModel-FreeTier'."}}""",
            route = com.hydrogen.ai.model.ProviderRoute.GOOGLE_AI_STUDIO_DIRECT
        )
        assertEquals(ProviderErrorCategory.QUOTA_RATE_TPM_RPM_429, error.category)
    }

    @Test fun sseEmbeddedErrorJsonIsClassified() {
        val error = RelayErrorClassifier.classifySseErrorPayload(
            dataPayload = """{"error":{"code":429,"message":"Requests per minute exceeded"}}""",
            route = com.hydrogen.ai.model.ProviderRoute.GOOGLE_AI_STUDIO_DIRECT
        )
        assertEquals(ProviderErrorCategory.QUOTA_RATE_TPM_RPM_429, error?.category)
    }
}
