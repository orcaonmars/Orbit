package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileAttachPolicyTest {

    @Test
    fun classifiesByMimeThenExtension() {
        assertEquals(FileAttachPolicy.Kind.PDF, FileAttachPolicy.classify("application/pdf", "a.bin"))
        assertEquals(FileAttachPolicy.Kind.PDF, FileAttachPolicy.classify("application/octet-stream", "Report.PDF"))
        assertEquals(FileAttachPolicy.Kind.IMAGE, FileAttachPolicy.classify("image/png", "x.png"))
        assertEquals(FileAttachPolicy.Kind.TEXT, FileAttachPolicy.classify("text/plain; charset=utf-8", "notes"))
        assertEquals(FileAttachPolicy.Kind.TEXT, FileAttachPolicy.classify("application/json", "data"))
        assertEquals(FileAttachPolicy.Kind.TEXT, FileAttachPolicy.classify("application/octet-stream", "Main.kt"))
        assertEquals(FileAttachPolicy.Kind.UNSUPPORTED, FileAttachPolicy.classify("application/zip", "a.zip"))
        assertEquals(FileAttachPolicy.Kind.UNSUPPORTED, FileAttachPolicy.classify(null, "noextension"))
    }

    @Test
    fun decodesUtf8AndUtf16WithBomsAndRejectsBinary() {
        assertEquals("héllo", FileAttachPolicy.decodeText(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "héllo".toByteArray()))
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "hi".toByteArray(Charsets.UTF_16LE)
        assertEquals("hi", FileAttachPolicy.decodeText(utf16))
        assertNull(FileAttachPolicy.decodeText(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00)))
        // A multibyte char cut by the size limit must not throw.
        val cut = "é".toByteArray().copyOf(1)
        assertTrue(FileAttachPolicy.decodeText(cut)!!.isNotEmpty())
    }

    @Test
    fun formatsAFencedBlockThatContentCannotEscape() {
        val draft = FileAttachPolicy.formatForDraft("Main.kt", "fun a() {}\r\n```\nnot a fence\n", truncatedBySize = false)
        assertTrue(draft.startsWith("File: Main.kt\n````kotlin\n"))
        assertTrue(draft.endsWith("\n````"))
        assertTrue(!draft.contains("\r"))
    }

    @Test
    fun longOrTruncatedFilesSayWhatWasCut() {
        val long = FileAttachPolicy.formatForDraft("a.txt", "x".repeat(FileAttachPolicy.MAX_TEXT_CHARS + 50), false)
        assertTrue(long.endsWith("(File truncated: only the beginning is included.)"))
        assertTrue(long.length < FileAttachPolicy.MAX_TEXT_CHARS + 200)
        val bySize = FileAttachPolicy.formatForDraft("a.txt", "short", truncatedBySize = true)
        assertTrue(bySize.contains("File truncated"))
        assertTrue(!FileAttachPolicy.formatForDraft("a.txt", "short", false).contains("truncated"))
    }

    @Test
    fun pdfPagesAreCappedAndRenderSizeKeepsAspect() {
        assertEquals(3, FileAttachPolicy.pagesToRender(3))
        assertEquals(FileAttachPolicy.MAX_PDF_PAGES, FileAttachPolicy.pagesToRender(500))
        assertEquals(0, FileAttachPolicy.pagesToRender(-1))
        val (w, h) = FileAttachPolicy.renderSize(612, 792) // US Letter in points
        assertEquals(1600, h)
        assertEquals(1236, w)
    }
}
