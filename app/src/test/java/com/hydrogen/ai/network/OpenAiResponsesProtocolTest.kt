package com.hydrogen.ai.network

import com.hydrogen.ai.model.OpenAiProtocol
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.RelayConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Pure Responses API helpers: request shape, event reduction, usage, and error mapping. No network. */
class OpenAiResponsesProtocolTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun chatMessages(): JsonArray = buildJsonArray {
        add(buildJsonObject { put("role", "system"); put("content", "Be brief.") })
        add(buildJsonObject { put("role", "user"); put("content", "Hi") })
        add(buildJsonObject {
            put("role", "user")
            put("content", buildJsonArray {
                add(buildJsonObject { put("type", "text"); put("text", "What is this?") })
                add(buildJsonObject {
                    put("type", "image_url")
                    put("image_url", buildJsonObject { put("url", "data:image/png;base64,AAAA") })
                })
            })
        })
        add(buildJsonObject { put("role", "assistant"); put("content", "Hello") })
    }

    @Test
    fun inputItemsUseResponsesPartTypesAndKeepStringContent() {
        val input = OpenAiResponsesProtocol.inputItems(chatMessages())
        assertEquals(4, input.size)
        val system = input[0].jsonObject
        assertEquals("system", system["role"]!!.jsonPrimitive.content)
        assertEquals("Be brief.", system["content"]!!.jsonPrimitive.content)

        val photo = input[2].jsonObject["content"]!!.jsonArray
        assertEquals("input_text", photo[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("What is this?", photo[0].jsonObject["text"]!!.jsonPrimitive.content)
        val image = photo[1].jsonObject
        assertEquals("input_image", image["type"]!!.jsonPrimitive.content)
        assertEquals("data:image/png;base64,AAAA", image["image_url"]!!.jsonPrimitive.content)
        assertEquals("auto", image["detail"]!!.jsonPrimitive.content)

        assertEquals("Hello", input[3].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun requestBodyHasResponsesFieldsAndNoChatOnlyFields() {
        val body = OpenAiResponsesProtocol.requestBody(
            model = "gpt-5.4",
            stream = true,
            input = OpenAiResponsesProtocol.inputItems(chatMessages()),
            maxOutputTokens = 2048,
            reasoningEffort = "low"
        )
        assertEquals("gpt-5.4", body["model"]!!.jsonPrimitive.content)
        assertTrue(body["stream"]!!.jsonPrimitive.content == "true")
        assertTrue(body["store"]!!.jsonPrimitive.content == "false")
        assertEquals("2048", body["max_output_tokens"]!!.jsonPrimitive.content)
        assertEquals("low", body["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertTrue(body["input"] is JsonArray)
        listOf("messages", "max_tokens", "reasoning_effort", "tools").forEach {
            assertFalse("chat-only field $it must not be sent", body.containsKey(it))
        }
    }

    @Test
    fun requestBodyOmitsOptionalFieldsWhenUnset() {
        val body = OpenAiResponsesProtocol.requestBody("m", false, JsonArray(emptyList()), null, null)
        assertFalse(body.containsKey("reasoning"))
        assertFalse(body.containsKey("max_output_tokens"))
        assertTrue(body["stream"]!!.jsonPrimitive.content == "false")
    }

    @Test
    fun parseEventReadsTextAndReasoningDeltas() {
        val text = OpenAiResponsesProtocol.parseEvent(
            """{"type":"response.output_text.delta","delta":"Hel"}""", null, null
        )
        assertEquals("Hel", (text as OpenAiResponsesProtocol.ResponsesEvent.Text).delta)

        val thinking = OpenAiResponsesProtocol.parseEvent(
            """{"type":"response.reasoning_summary_text.delta","delta":"Weighing"}""", null, null
        )
        assertEquals("Weighing", (thinking as OpenAiResponsesProtocol.ResponsesEvent.Thinking).delta)
    }

    @Test
    fun parseEventIgnoresLifecycleEventsDoneAndEmptyDeltas() {
        listOf(
            """{"type":"response.created","response":{}}""",
            """{"type":"response.output_item.added","item":{}}""",
            """{"type":"response.output_text.delta","delta":""}""",
            "[DONE]",
            "",
            "not json at all"
        ).forEach { payload ->
            assertEquals(payload, OpenAiResponsesProtocol.ResponsesEvent.Ignored,
                OpenAiResponsesProtocol.parseEvent(payload, null, null))
        }
    }

    @Test
    fun parseEventUsesSseEventNameWhenJsonHasNoType() {
        val event = OpenAiResponsesProtocol.parseEvent(
            """{"code":"rate_limit_exceeded","message":"Slow down"}""", "error", null
        )
        val failed = event as OpenAiResponsesProtocol.ResponsesEvent.Failed
        assertEquals(429, failed.error.httpStatus)
        assertTrue(RelayErrorClassifier.isTransientError(failed.error.category))
    }

    @Test
    fun completedAndIncompleteEventsCarryTheResponse() {
        val completed = OpenAiResponsesProtocol.parseEvent(
            """{"type":"response.completed","response":{"status":"completed"}}""", null, null
        ) as OpenAiResponsesProtocol.ResponsesEvent.Finished
        assertFalse(completed.incomplete)
        assertEquals("completed", completed.response!!["status"]!!.jsonPrimitive.content)

        val incomplete = OpenAiResponsesProtocol.parseEvent(
            """{"type":"response.incomplete","response":{"status":"incomplete"}}""", null, null
        ) as OpenAiResponsesProtocol.ResponsesEvent.Finished
        assertTrue(incomplete.incomplete)
    }

    @Test
    fun responseFailedUsesNestedErrorThroughTheSharedClassifier() {
        val event = OpenAiResponsesProtocol.parseEvent(
            """{"type":"response.failed","response":{"status":"failed","error":{"code":"insufficient_quota","message":"No credit"}}}""",
            null, null
        ) as OpenAiResponsesProtocol.ResponsesEvent.Failed
        assertEquals(402, event.error.httpStatus)
        assertTrue(event.error.userMessage.isNotBlank())
    }

    @Test
    fun errorFromMissingObjectFallsBackToUnknownProtocol() {
        val error = OpenAiResponsesProtocol.errorFrom(null, null, "fallback text")
        assertEquals(ProviderErrorCategory.UNKNOWN_PROTOCOL, error.category)
        assertEquals("fallback text", error.userMessage)
    }

    @Test
    fun outputTextAndReasoningComeFromFinishedResponseObject() {
        val response = json.parseToJsonElement(
            """{"status":"completed","output":[
                {"type":"reasoning","summary":[{"type":"summary_text","text":"Plan."}]},
                {"type":"message","role":"assistant","content":[{"type":"output_text","text":"Answer."}]}
            ]}"""
        ).jsonObject
        assertEquals("Answer.", OpenAiResponsesProtocol.outputText(response))
        assertEquals("Plan.", OpenAiResponsesProtocol.reasoningText(response))
    }

    @Test
    fun usageIsReadWhenPresentAndNullWhenNot() {
        val withUsage = json.parseToJsonElement(
            """{"usage":{"input_tokens":10,"output_tokens":4,"output_tokens_details":{"reasoning_tokens":2}}}"""
        ).jsonObject
        val usage = OpenAiResponsesProtocol.usageOf(withUsage)!!
        assertEquals(10, usage.inputTokens)
        assertEquals(4, usage.outputTokens)
        assertEquals(2, usage.reasoningTokens)
        assertNull(OpenAiResponsesProtocol.usageOf(JsonObject(emptyMap())))
    }

    @Test
    fun missingResponsesEndpointIsNamedUnsupportedNotMisreadAsModelError() {
        val error = OpenAiResponsesProtocol.httpError(404, "Unexpected endpoint or method.", null, "Example")
        assertEquals(ProviderErrorCategory.UNSUPPORTED_CAPABILITY, error.category)
        assertTrue(error.userMessage.contains("/v1/responses"))
        assertTrue(error.userMessage.contains("Chat Completions"))
    }

    @Test
    fun notFoundThatNamesAModelIsNotReportedAsMissingEndpoint() {
        val error = OpenAiResponsesProtocol.httpError(
            404, """{"error":{"message":"The model gpt-x does not exist","code":"model_not_found"}}""", null, null
        )
        assertEquals(404, error.httpStatus)
        assertNotEquals(ProviderErrorCategory.UNSUPPORTED_CAPABILITY, error.category)
    }

    @Test
    fun protocolDefaultsToChatCompletionsForOldConfigs() {
        assertEquals(OpenAiProtocol.CHAT_COMPLETIONS, RelayConfig().openAiProtocol)
        assertEquals(OpenAiProtocol.CHAT_COMPLETIONS, OpenAiProtocol.fromStored(null))
        assertEquals(OpenAiProtocol.CHAT_COMPLETIONS, OpenAiProtocol.fromStored("GARBAGE"))
        assertEquals(OpenAiProtocol.RESPONSES, OpenAiProtocol.fromStored("RESPONSES"))
    }
}
