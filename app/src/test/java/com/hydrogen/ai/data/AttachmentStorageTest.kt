package com.hydrogen.ai.data

import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentStorageTest {

    @Test
    fun importsImageToAppPrivateStorageAndReadsBackDeterministically() = runTest {
        val tempRoot = Files.createTempDirectory("hydrogen-att-test").toFile()
        try {
            val storage = LocalAttachmentStorage(File(tempRoot, "attachments"))
            val sampleBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

            val result = storage.importFromStream(
                displayName = "photo ../test?.png",
                mimeType = "image/png",
                reportedSizeBytes = sampleBytes.size.toLong(),
                openInput = { ByteArrayInputStream(sampleBytes) }
            )

            assertTrue(result is AttachmentImportResult.Success)
            val item = (result as AttachmentImportResult.Success).item
            assertEquals("image/png", item.mimeType)
            assertEquals(8L, item.sizeBytes)
            assertTrue(item.localUri.startsWith("file:"))

            val persistedFile = File(URI.create(item.localUri))
            assertTrue(persistedFile.exists())
            assertEquals(8L, persistedFile.length())

            val encoded = persistedFile.inputStream().use { input ->
                readAndEncodeImageStream(
                    input = input,
                    resolvedMime = item.mimeType,
                    maxBytes = 1024L,
                    encodeBase64 = { Base64.getEncoder().encodeToString(it) }
                )
            }
            assertTrue(encoded is ImageLoadResult.Success)
            val payload = (encoded as ImageLoadResult.Success).payload
            assertEquals(Base64.getEncoder().encodeToString(sampleBytes), payload.base64Data)

            assertTrue(storage.deleteIfOwned(item.localUri))
            assertFalse(persistedFile.exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun rejectsNonImageOversizedAndOutsideDeleteTargets() = runTest {
        val tempRoot = Files.createTempDirectory("hydrogen-att-guard").toFile()
        try {
            val attachmentsDir = File(tempRoot, "attachments")
            val storage = LocalAttachmentStorage(attachmentsDir, maxPersistBytes = 10L)

            val nonImage = storage.importFromStream(
                displayName = "notes.txt",
                mimeType = "text/plain",
                openInput = { ByteArrayInputStream("hello".toByteArray()) }
            )
            assertTrue(nonImage is AttachmentImportResult.Failure)

            val oversized = storage.importFromStream(
                displayName = "huge.jpg",
                mimeType = "image/jpeg",
                reportedSizeBytes = 5L,
                openInput = { ByteArrayInputStream(ByteArray(32) { 9 }) }
            )
            assertTrue(oversized is AttachmentImportResult.Failure)
            assertEquals(0, attachmentsDir.listFiles()?.size ?: 0)

            val externalFile = File(tempRoot, "keep-me.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            assertFalse(storage.deleteIfOwned(externalFile.toURI().toString()))
            assertTrue(externalFile.exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun computeSampleSizeAndLocalFileResolutionAreSafe() = runTest {
        val tempRoot = Files.createTempDirectory("hydrogen-att-thumb").toFile()
        try {
            assertEquals(1, computeSampleSize(200, 150, 256, 256))
            assertEquals(4, computeSampleSize(2400, 1800, 256, 256))
            assertEquals(1, computeSampleSize(0, 100, 256, 256))

            val storage = LocalAttachmentStorage(File(tempRoot, "attachments"))
            val imported = storage.importFromStream(
                displayName = "thumb.png",
                mimeType = "image/png",
                reportedSizeBytes = 4L,
                openInput = { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) }
            ) as AttachmentImportResult.Success

            val resolved = resolveLocalAttachmentFile(imported.item.localUri)
            assertTrue(resolved != null && resolved.exists())
            assertEquals(null, resolveLocalAttachmentFile("content://media/external/images/1"))
            assertEquals(null, resolveLocalAttachmentFile("file:///nonexistent/hydrogen_missing_image.png"))
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
