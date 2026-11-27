package com.hydrogen.ai.network

import com.hydrogen.ai.data.EncodedImagePayload
import com.hydrogen.ai.data.ImageLoadResult
import com.hydrogen.ai.data.ImagePayloadLoader
import com.hydrogen.ai.model.AttachmentManifestItem
import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.RelayConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A photo from earlier in a chat that can no longer be read must not block every later message. */
class UnreadableHistoryPhotoTest {

    private val loader = object : ImagePayloadLoader {
        override suspend fun loadImage(localUri: String, expectedMimeType: String): ImageLoadResult =
            if (localUri.contains("gone")) {
                ImageLoadResult.MissingOrUnreadable("file removed")
            } else {
                ImageLoadResult.Success(EncodedImagePayload("image/jpeg", "AAAA", 3L))
            }
    }

    private fun photo(id: String, uri: String) = AttachmentManifestItem(
        id = id, displayName = "$id.jpg", mimeType = "image/jpeg", sizeBytes = 3L, localUri = uri
    )

    private fun user(id: String, text: String, vararg photos: AttachmentManifestItem) = ChatMessage(
        id = id, correlationId = id, role = MessageRole.USER, content = text, createdAtEpochMs = 1L,
        attachments = photos.toList()
    )

    private fun reply(id: String) = ChatMessage(
        id = id, correlationId = id, role = MessageRole.ASSISTANT, content = "Seen.", createdAtEpochMs = 2L
    )

    private fun request(vararg messages: ChatMessage) = ChatDispatchRequest(
        correlationId = "c", modelId = "m", messages = messages.toList(), attachments = emptyList()
    )

    private val followUp = request(
        user("u1", "Look", photo("old", "file:///gone.jpg"), photo("ok", "file:///ok.jpg")),
        reply("a1"),
        user("u2", "And now?")
    )
    private val sendingTheBadPhoto = request(user("u1", "Look", photo("old", "file:///gone.jpg")))

    @Test fun openAiCompatibleSkipsTheEarlierPhotoAndSaysSo() = runTest {
        val backend = HttpGeminiHydrogenRelayBackend(loader)
        val built = backend.buildPayload("m", false, followUp)
        assertNull(built.error)
        val text = built.body.toString()
        assertEquals(1, Regex("\"type\":\"image_url\"").findAll(text).count())
        assertTrue(text.contains(HistoryImages.LEFT_OUT_NOTE))
        assertNotNull(backend.buildPayload("m", false, sendingTheBadPhoto).error)
    }

    @Test fun anthropicSkipsTheEarlierPhotoAndSaysSo() = runTest {
        val backend = HttpAnthropicMessagesBackend(loader)
        val built = backend.buildPayload(followUp, "m", safe = false)
        assertNull(built.error)
        val text = built.body.toString()
        assertEquals(1, Regex("\"type\":\"image\"").findAll(text).count())
        assertTrue(text.contains(HistoryImages.LEFT_OUT_NOTE))
        assertNotNull(backend.buildPayload(sendingTheBadPhoto, "m", safe = false).error)
    }

    @Test fun googleSkipsTheEarlierPhotoAndSaysSo() = runTest {
        val backend = HttpGoogleAiStudioBackend(loader)
        val built = backend.buildPayload(followUp)
        assertNull(built.error)
        val text = built.body.toString()
        assertEquals(1, Regex("inlineData").findAll(text).count())
        assertTrue(text.contains(HistoryImages.LEFT_OUT_NOTE))
        assertNotNull(backend.buildPayload(sendingTheBadPhoto).error)
    }

    @Test fun aTextOnlyFollowUpDoesNotRepeatTheGridNotice() {
        val photos = (1..10).map { photo("p$it", "file:///p$it.jpg") }
        val config = RelayConfig(maxImagesPerRequest = 8)
        val sending = PreflightValidator.assess(listOf(user("u1", "Look", *photos.toTypedArray())), config)
        assertNotNull(sending.currentPhotosNote)
        val later = PreflightValidator.assess(
            listOf(user("u1", "Look", *photos.toTypedArray()), reply("a1"), user("u2", "And now?")),
            config
        )
        assertNull(later.currentPhotosNote)
        assertTrue(later.warnings.none { it.startsWith("This message has") })
    }
}
