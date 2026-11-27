package com.hydrogen.ai.network

import com.hydrogen.ai.model.ErrorExplainer
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.ProviderPresets
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.ui.KEY_RELAY_CATEGORIES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Clean APIs errors, from https://cleanapis.com/docs/authentication and from live replies to a free-plan key on
 * Nov 26 2026 (every model, streamed or not, with or without max_tokens, answered the same 402).
 */
class CleanApisErrorTest {
    private val svc = "Clean APIs"

    /** The exact body Clean APIs sent for a tiny request, with the free monthly allowance unused. */
    private val liveBillingVerification402 =
        """{"error":{"code":"insufficient_quota","message":"Billing verification failed. Please check your payment method.","param":null,"type":"insufficient_funds"}}"""

    private fun classify(status: Int, body: String, retryAfter: String? = null) =
        RelayErrorClassifier.classifyHttpError(status, body, retryAfter, ProviderRoute.GEMINI_HYDROGEN_RELAY, svc)

    @Test fun cleanApisPresetIsNamedSoMessagesNameTheService() {
        assertEquals(svc, ProviderPresets.serviceNameFor(ProviderRoute.GEMINI_HYDROGEN_RELAY, "https://cleanapis.com"))
        assertEquals(
            "https://cleanapis.com/v1/chat/completions",
            ProviderPresets.openAiEndpoint("https://cleanapis.com", "chat/completions")
        )
    }

    @Test fun liveBillingVerification402IsNotReportedAsUsedUpUsage() {
        val error = classify(402, liveBillingVerification402)
        assertEquals(ProviderErrorCategory.BILLING_NOT_VERIFIED, error.category)
        assertEquals(402, error.httpStatus)
        val msg = error.userMessage
        assertTrue(msg, msg.startsWith("Clean APIs couldn't verify the billing or payment method"))
        assertTrue(msg, msg.contains("not used-up usage"))
        assertTrue(msg, msg.contains("\"Billing verification failed. Please check your payment method.\""))
        assertFalse(msg, msg.contains("out of credits"))
        assertFalse(msg, msg.contains("Add credits"))
        assertEquals("insufficient_funds: Billing verification failed. Please check your payment method.", error.technicalDetail)
        assertFalse(RelayErrorClassifier.isTransientError(error.category))
    }

    @Test fun billingVerificationInsideAStreamIsClassifiedTheSameWay() {
        val error = RelayErrorClassifier.classifySseErrorPayload(liveBillingVerification402, serviceName = svc)
        assertNotNull(error)
        assertEquals(ProviderErrorCategory.BILLING_NOT_VERIFIED, error!!.category)
        assertEquals(402, error.httpStatus)
    }

    @Test fun allowanceAndBalanceExhausted402SaysBothAreUsedUp() {
        val error = classify(
            402,
            """{"error":{"code":"insufficient_funds","message":"Out of balance and plan allowance.","param":null,"type":"insufficient_funds"}}"""
        )
        assertEquals(ProviderErrorCategory.OUT_OF_CREDITS, error.category)
        assertTrue(error.userMessage, error.userMessage.contains("plan allowance and your balance are both used up"))
        assertTrue(error.userMessage, error.userMessage.contains("zero balance alone doesn't cause this"))
        assertTrue(error.userMessage, error.userMessage.contains("\"Out of balance and plan allowance.\""))
    }

    @Test fun insufficientFundsTypeInAStreamMapsTo402() {
        assertEquals(402, RelayErrorClassifier.statusForErrorType("insufficient_funds"))
        val error = RelayErrorClassifier.classifySseErrorPayload(
            """{"error":{"message":"Out of balance and plan allowance.","type":"insufficient_funds"}}""",
            serviceName = svc
        )
        assertEquals(ProviderErrorCategory.OUT_OF_CREDITS, error!!.category)
        assertEquals(402, error.httpStatus)
    }

    /** Free allowance left, $0 balance: an upstream provider's own quota error must not read as the user's. */
    @Test fun upstreamProviderErrorQuotingQuotaIsNotTheUsersAllowance() {
        val body = """{"error":{"code":"provider_error","message":"Upstream provider returned insufficient_quota: You exceeded your current quota, please check your plan and billing details.","param":null,"type":"provider_error"}}"""
        val error = classify(502, body)
        assertEquals(ProviderErrorCategory.SERVER_5XX, error.category)
        assertEquals(RelayErrorClassifier.LAYER_UPSTREAM_PROVIDER, error.sourceLayer)
        assertTrue(error.userMessage, error.userMessage.contains("not your key or your allowance"))
        assertFalse(error.userMessage.contains("credits"))
        assertTrue(RelayErrorClassifier.isTransientError(error.category))
        assertFalse(error.category in KEY_RELAY_CATEGORIES)
    }

    @Test fun providerErrorEventInAStreamIsA502() {
        assertEquals(502, RelayErrorClassifier.statusForErrorType("provider_error"))
        val error = RelayErrorClassifier.classifySseErrorPayload(
            """{"error":{"message":"The provider failed.","type":"provider_error"}}""", serviceName = svc
        )
        assertEquals(ProviderErrorCategory.SERVER_5XX, error!!.category)
        assertEquals(502, error.httpStatus)
    }

    @Test fun anyServerErrorQuotingBillingWordsIsNotOutOfCredits() {
        for (status in listOf(500, 502, 503)) {
            val error = classify(status, """{"error":{"message":"insufficient credits on upstream account"}}""")
            assertFalse("$status", error.category == ProviderErrorCategory.OUT_OF_CREDITS)
        }
    }

    @Test fun perMinuteRateLimitIsTemporaryAndUsesRetryAfter() {
        val error = classify(
            429,
            """{"error":{"code":"rate_limit_exceeded","message":"Rate limit exceeded. Try again shortly.","param":null,"type":"rate_limit_error"}}""",
            "12"
        )
        assertEquals(ProviderErrorCategory.QUOTA_RATE_TPM_RPM_429, error.category)
        assertEquals(12, error.retryAfterSeconds)
        assertTrue(error.userMessage, error.userMessage.contains("Too many requests to Clean APIs"))
        assertTrue(RelayErrorClassifier.isTransientError(error.category))
    }

    @Test fun insufficientScopeExplainsTheMissingInferenceScope() {
        val error = classify(
            403,
            """{"error":{"code":"insufficient_scope","message":"This API key does not have the inference scope.","param":null,"type":"invalid_request_error"}}"""
        )
        assertEquals(ProviderErrorCategory.FORBIDDEN_403, error.category)
        assertTrue(error.userMessage, error.userMessage.contains("inference scope"))
        assertEquals(403, RelayErrorClassifier.statusForErrorType("insufficient_scope"))
    }

    @Test fun missingKeyIsAKeyProblemNotBilling() {
        val error = classify(
            401,
            """{"error":{"code":"missing_api_key","message":"Missing API key. Send it as a Bearer token or x-api-key header.","param":null,"type":"authentication_error"}}"""
        )
        assertEquals(ProviderErrorCategory.UNAUTHORIZED_UPSTREAM_401, error.category)
    }

    @Test fun openAiQuota429AndOpenRouter402StillReadAsOutOfCredits() {
        val openAi = RelayErrorClassifier.classifyHttpError(
            429,
            """{"error":{"message":"You exceeded your current quota, please check your plan and billing details.","type":"insufficient_quota","code":"insufficient_quota"}}""",
            serviceName = "OpenAI"
        )
        assertEquals(ProviderErrorCategory.OUT_OF_CREDITS, openAi.category)
        val openRouter = RelayErrorClassifier.classifyHttpError(
            402, """{"error":{"message":"Insufficient credits. Add more using https://openrouter.ai/settings/credits","code":402}}""",
            serviceName = "OpenRouter"
        )
        assertEquals(ProviderErrorCategory.OUT_OF_CREDITS, openRouter.category)
    }

    @Test fun billingNotVerifiedStepsPointAtThePaymentCheckNotAtAddingCredits() {
        val error = classify(402, liveBillingVerification402)
        val steps = ErrorExplainer.steps(error, serviceName = svc)
        assertTrue(steps.first(), steps.first().contains("payment method"))
        assertTrue(steps.none { it.contains("Add credits") })
        assertTrue(error.category in KEY_RELAY_CATEGORIES)
    }

    @Test fun billingMessagesFollowTheCopyRules() {
        val messages = listOf(
            classify(402, liveBillingVerification402).userMessage,
            classify(402, """{"error":{"message":"Out of balance and plan allowance.","type":"insufficient_funds"}}""").userMessage,
            classify(502, """{"error":{"message":"x","type":"provider_error"}}""").userMessage,
            classify(403, """{"error":{"code":"insufficient_scope","message":"x"}}""").userMessage
        ) + ErrorExplainer.steps(classify(402, liveBillingVerification402), serviceName = svc)
        for (m in messages) {
            assertFalse(m, m.contains('—') || m.contains('–') || m.contains('!') || m.contains('·'))
        }
    }
}
