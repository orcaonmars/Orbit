package com.hydrogen.ai.network

import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.SystemPromptPolicy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAiStudioGemmaPayloadTest {

    private val request = ChatDispatchRequest(
        correlationId = "c1",
        modelId = "gemma-3-27b-it",
        messages = listOf(
            ChatMessage(
                id = SystemPromptPolicy.MESSAGE_ID,
                correlationId = SystemPromptPolicy.MESSAGE_ID,
                role = MessageRole.SYSTEM,
                content = "Be brief.",
                createdAtEpochMs = 1L
            ),
            ChatMessage(id = "u1", correlationId = "c1", role = MessageRole.USER, content = "Hi", createdAtEpochMs = 2L),
            ChatMessage(id = "a1", correlationId = "c1", role = MessageRole.ASSISTANT, content = "Hello", createdAtEpochMs = 3L),
            ChatMessage(id = "u2", correlationId = "c2", role = MessageRole.USER, content = "Again", createdAtEpochMs = 4L)
        ),
        attachments = emptyList()
    )

    private fun texts(content: JsonObject): List<String> =
        content["parts"]!!.jsonArray.map { it.jsonObject["text"]!!.jsonPrimitive.content }

    @Test
    fun gemmaGetsInstructionsAtTheTopOfTheFirstUserTurnOnly() = runTest {
        val body = HttpGoogleAiStudioBackend().buildPayload(request, instructionsInFirstUserTurn = true).body!!

        assertFalse(body.containsKey("systemInstruction"))
        val contents = body["contents"]!!.jsonArray.map { it.jsonObject }
        assertEquals(3, contents.size)
        assertEquals(listOf("Be brief.", "Hi"), texts(contents[0]))
        assertEquals(listOf("Hello"), texts(contents[1]))
        assertEquals(listOf("Again"), texts(contents[2]))
    }

    @Test
    fun geminiKeepsSystemInstruction() = runTest {
        val body = HttpGoogleAiStudioBackend().buildPayload(request).body!!

        val system = body["systemInstruction"]!!.jsonObject
        assertEquals(listOf("Be brief."), texts(system))
        val first = body["contents"]!!.jsonArray.first().jsonObject
        assertEquals(listOf("Hi"), texts(first))
    }

    @Test
    fun recognisesGemmaModelNames() {
        assertTrue(HttpGoogleAiStudioBackend.isGemmaModel("gemma-3-27b-it"))
        assertTrue(HttpGoogleAiStudioBackend.isGemmaModel("models/gemma-3n-e4b-it"))
        assertTrue(HttpGoogleAiStudioBackend.isGemmaModel("  Gemma-3-12b-it "))
        assertFalse(HttpGoogleAiStudioBackend.isGemmaModel("gemini-2.5-flash"))
        assertFalse(HttpGoogleAiStudioBackend.isGemmaModel("models/gemini-2.5-pro"))
    }
}
