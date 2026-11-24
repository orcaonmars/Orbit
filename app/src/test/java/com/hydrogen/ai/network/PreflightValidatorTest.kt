package com.hydrogen.ai.network

import com.hydrogen.ai.model.AttachmentManifestItem
import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.RelayConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test

class PreflightValidatorTest {
    private fun image(index: Int): AttachmentManifestItem =
        AttachmentManifestItem(
            id = "image-$index",
            displayName = "image-$index.png",
            mimeType = "image/png",
            sizeBytes = 64L,
            localUri = "content://test/$index"
        )

    private fun msg(text: String, attachments: List<AttachmentManifestItem> = emptyList()) =
        ChatMessage(
            id = "message-1",
            correlationId = "request-1",
            role = MessageRole.USER,
            content = text,
            createdAtEpochMs = 1L,
            attachments = attachments
        )

    @Test fun oldImagesAreMarkedOmittedButLocalOriginalsAreUnchanged() {
        val originals = (1..10).map(::image)
        val result = PreflightValidator.assess(
            listOf(msg("What is this?", originals)),
            RelayConfig(maxImagesPerRequest = 8)
        )
        assertEquals(10, result.totalImageCount)
        assertEquals(8, result.includedImages.size)
        assertEquals(2, result.omittedImages.size)
        assertEquals("image-3", result.includedImages.first().id)
        assertTrue(result.omittedImages.all { it.omittedFromRemote })
        assertTrue(result.warnings.single().contains("not be sent remotely"))
        assertEquals(setOf("image-1", "image-2"), result.omittedAttachmentIds)
        assertFalse(originals.any { it.omittedFromRemote })
        assertEquals("content://test/1", originals.first().localUri)
    }

    @Test fun oversizedContextIsBlockedBeforeSending() {
        val result = PreflightValidator.assess(
            listOf(msg("a".repeat(700))),
            RelayConfig(maxContextTokensBudget = 10)
        )
        assertNotNull(result.blockingError)
        assertEquals(ProviderErrorCategory.PREFLIGHT_BLOCKED, result.blockingError?.category)
    }

    @Test fun withinBudgetDoesNotBlockMessage() {
        val result = PreflightValidator.assess(
            listOf(msg("small")), RelayConfig(maxContextTokensBudget = 120000)
        )
        assertEquals(null, result.blockingError)
        assertTrue(result.includedImages.isEmpty())
    }

    @Test fun unsupportedAttachmentIsBlockedEvenWhenImageCountIsBelowCap() {
        val unsupported = image(1).copy(displayName = "archive.zip", mimeType = "application/zip")
        val result = PreflightValidator.assess(
            listOf(msg("Please inspect the attachment", listOf(unsupported))),
            RelayConfig(maxImagesPerRequest = 8)
        )
        assertEquals(1, result.totalImageCount)
        assertEquals(ProviderErrorCategory.PREFLIGHT_BLOCKED, result.blockingError?.category)
        assertTrue(result.blockingError?.userMessage.orEmpty().contains("archive.zip"))
    }

    @Test fun oversizedImageIsBlockedEvenWhenImageCountIsBelowCap() {
        val tooLarge = image(1).copy(sizeBytes = 1025L)
        val result = PreflightValidator.assess(
            listOf(msg("What is this?", listOf(tooLarge))),
            RelayConfig(maxImagesPerRequest = 8, maxImageBytes = 1024L)
        )
        assertEquals(1, result.totalImageCount)
        assertEquals(ProviderErrorCategory.PREFLIGHT_BLOCKED, result.blockingError?.category)
    }
}
