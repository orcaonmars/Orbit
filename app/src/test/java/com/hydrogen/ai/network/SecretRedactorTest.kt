package com.hydrogen.ai.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SecretRedactorTest {

    @Test
    fun redactShortKeyShapedPathSegmentsWithoutRemovingRouteSeparators() {
        val cases = listOf(
            "https://example.invalid/sk-test01/models" to "https://example.invalid/[hidden]/models",
            "https://example.invalid/sk-test02?format=json" to "https://example.invalid/[hidden]?format=json",
            "https://example.invalid/sk-test03#details" to "https://example.invalid/[hidden]#details",
            "GET /sk-test04 HTTP/1.1" to "GET /[hidden] HTTP/1.1",
            "https://example.invalid/sk-test05" to "https://example.invalid/[hidden]",
            "\"https://example.invalid/SK-test06\"" to "\"https://example.invalid/[hidden]\""
        )
        for ((input, expected) in cases) {
            assertEquals(expected, SecretRedactor.redact(input))
        }
        val ordinary = "GET /v1/sk-model HTTP/1.1"
        assertEquals(ordinary, SecretRedactor.redact(ordinary))
    }


    @Test
    fun redactHidesKeyShapesAndTokensWithoutRevealingSecrets() {
        val sampleDiag = """
            GET /v1/chat/completions HTTP/1.1
            Authorization: Bearer sk-ant-api03-abcdef1234567890
            x-api-key: secret-diagnostic-key-1234
            url: https://api.openai.com/v1/models?api_key=AIzaSyA12345678901234567890
            body: {"token": "my-secret-token-xyz", "status": "failed"}
        """.trimIndent()

        val redacted = SecretRedactor.redact(
            text = sampleDiag,
            knownSecrets = listOf("my-secret-token-xyz")
        )

        assertFalse("Raw anthropic key leaked", redacted.contains("sk-ant-api03-abcdef1234567890"))
        assertFalse("Raw api key header leaked", redacted.contains("secret-diagnostic-key-1234"))
        assertFalse("Raw google key leaked", redacted.contains("AIzaSyA12345678901234567890"))
        assertFalse("Raw known secret leaked", redacted.contains("my-secret-token-xyz"))
        assertFalse("Internal secret structure leaked", redacted.contains("1234567890"))

        assertEquals(
            """
            GET /v1/chat/completions HTTP/1.1
            Authorization: Bearer [hidden]
            x-api-key: [hidden]
            url: https://api.openai.com/v1/models?api_key=[hidden]
            body: {"token": "[hidden]", "status": "failed"}
            """.trimIndent(),
            redacted
        )
    }

    @Test
    fun redactPreservesSafeDiagnosticText() {
        val safeText = "HTTP 401 Unauthorized: invalid authentication credentials provided"
        assertEquals(safeText, SecretRedactor.redact(safeText))
    }
}
