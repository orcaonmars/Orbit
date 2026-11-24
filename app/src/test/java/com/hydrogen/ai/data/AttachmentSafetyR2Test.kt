package com.hydrogen.ai.data

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AttachmentSafetyR2Test {
    @get:Rule val folder = TemporaryFolder()

    @Test fun extremeAspectRatioThumbnailsHaveBoundedPixelCount() {
        for ((w,h) in listOf(100000 to 256, 256 to 100000)) {
            val sample = computeSampleSize(w,h,256,256)
            assertTrue(sample > 1)
            assertTrue(((w.toLong()+sample-1)/sample)*((h.toLong()+sample-1)/sample) <= 5L*256*256)
        }
        assertEquals(4, computeSampleSize(2400,1800,256,256))
    }

    private fun incompleteStream() = object : InputStream() {
        var first = true
        override fun read(): Int = -1
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (!first) return 0
            first = false
            b[off] = 1
            return 1
        }
    }

    @Test fun partialZeroReadDoesNotPersistATruncatedAttachment() = runTest {
        val store = LocalAttachmentStorage(folder.root)
        val result = store.importFromStream("photo.png", "image/png") { incompleteStream() }
        assertTrue(result is AttachmentImportResult.Failure)
        assertTrue(folder.root.list()!!.isEmpty())
    }

    @Test fun partialZeroReadDoesNotUploadATruncatedImage() {
        val result = readAndEncodeImageStream(incompleteStream(), "image/png", 100) { "should not encode" }
        assertTrue(result is ImageLoadResult.MissingOrUnreadable)
    }

    @Test fun cancellationIsPropagatedAndPartialFileRemoved() = runTest {
        val store = LocalAttachmentStorage(folder.root)
        val stream = object : InputStream() {
            var first = true
            override fun read(): Int = -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (!first) throw CancellationException("cancel")
                first = false
                b[off] = 1
                return 1
            }
        }
        try {
            store.importFromStream("photo.png","image/png") { stream }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertTrue(folder.root.list()!!.isEmpty())
        }
    }

    @Test fun storageFailureDoesNotExposeExceptionPath() = runTest {
        val store = LocalAttachmentStorage(folder.root)
        val result = store.importFromStream("photo.png","image/png") {
            throw java.io.IOException("/private/secret/path")
        } as AttachmentImportResult.Failure
        assertFalse(result.reason.contains("/private"))
    }

    @Test fun nonDirectoryStorageIsRejected() = runTest {
        val blocked = folder.newFile("blocked")
        val result = LocalAttachmentStorage(blocked).importFromStream("photo.png","image/png") {
            ByteArrayInputStream(byteArrayOf(1))
        }
        assertTrue(result is AttachmentImportResult.Failure)
        assertTrue(blocked.isFile)
    }
}