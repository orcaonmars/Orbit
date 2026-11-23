package com.hydrogen.ai.model

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTextExtractorTest {

    private class S(val dict: String, val data: ByteArray)

    private fun content(text: String, dict: String = "") = S(dict, text.toByteArray(Charsets.ISO_8859_1))

    private fun flate(text: String): S {
        val deflater = Deflater()
        deflater.setInput(text.toByteArray(Charsets.ISO_8859_1))
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        return S("/Filter /FlateDecode", out.toByteArray())
    }

    private fun pdf(vararg streams: S, trailer: String = ""): ByteArray {
        val out = ByteArrayOutputStream()
        fun w(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        w("%PDF-1.4\n%âãÏÓ\n")
        w("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        streams.forEachIndexed { index, s ->
            w("${index + 3} 0 obj\n<< ${s.dict} /Length ${s.data.size} >>\nstream\n")
            out.write(s.data)
            w("\nendstream\nendobj\n")
        }
        w("trailer\n<< /Root 1 0 R $trailer >>\n%%EOF\n")
        return out.toByteArray()
    }

    @Test
    fun plainTjIsExtracted() {
        val r = PdfTextExtractor.extract(pdf(content("BT /F1 12 Tf 72 712 Td (Hello, world!) Tj ET")))
        assertNull(r.reason)
        assertEquals("Hello, world!", r.text)
        assertEquals(1, r.pagesWithText)
        assertFalse(r.truncated)
    }

    @Test
    fun flateDecodeStreamsAreInflatedAndCountedPerPage() {
        val r = PdfTextExtractor.extract(
            pdf(flate("BT /F1 12 Tf (First page) Tj ET"), flate("BT /F1 12 Tf (Second page) Tj ET"))
        )
        assertNull(r.reason)
        assertEquals("First page\n\nSecond page", r.text)
        assertEquals(2, r.pagesWithText)
    }

    @Test
    fun tjArrayKerningAddsSpaceOnlyForLargeGaps() {
        val r = PdfTextExtractor.extract(pdf(content("BT [(Hel) -20 (lo) -350 (there)] TJ ET")))
        assertEquals("Hello there", r.text)
    }

    @Test
    fun literalEscapesNestingAndOctal() {
        val r = PdfTextExtractor.extract(pdf(content("BT (a \\(b\\) (nested) c\\\\d \\101\\102\\103 tab\\there) Tj ET")))
        assertEquals("a (b) (nested) c\\d ABC tab\there", r.text)
    }

    @Test
    fun hexStringsIncludingUtf16() {
        val r = PdfTextExtractor.extract(pdf(content("BT <48656C6C6F> Tj T* <FEFF00480069> Tj ET")))
        assertEquals("Hello\nHi", r.text)
    }

    /** A Chrome/Word-style PDF: Type0 Identity-H font, 2-byte glyph ids, ToUnicode CMap (bfchar + bfrange). */
    private fun type0Pdf(contentText: String, withToUnicode: Boolean, flateCmap: Boolean = false): ByteArray {
        val cmap = "/CIDInit /ProcSet findresource begin 12 dict begin begincmap\n" +
            "1 begincodespacerange <0000> <FFFF> endcodespacerange\n" +
            "2 beginbfchar <0001> <0048> <0005> <0020> endbfchar\n" +
            "2 beginbfrange <0002> <0004> <0069> <0010> <0011> [<00C9> <D83DDE00>] endbfrange\n" +
            "endcmap CMapName currentdict /CMap defineresource pop end end"
        val cmapStream = if (flateCmap) flate(cmap) else content(cmap)
        val out = ByteArrayOutputStream()
        fun w(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        w("%PDF-1.7\n")
        w("1 0 obj\n<< /Type /Page /Resources << /Font << /F4 5 0 R >> >> /Contents 3 0 R >>\nendobj\n")
        w("3 0 obj\n<< /Length ${contentText.length} >>\nstream\n$contentText\nendstream\nendobj\n")
        val toUnicode = if (withToUnicode) "/ToUnicode 6 0 R" else ""
        w("5 0 obj\n<< /Type /Font /Subtype /Type0 /BaseFont /AAAAAA+Test /Encoding /Identity-H $toUnicode >>\nendobj\n")
        w("6 0 obj\n<< ${cmapStream.dict} /Length ${cmapStream.data.size} >>\nstream\n")
        out.write(cmapStream.data)
        w("\nendstream\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n")
        return out.toByteArray()
    }

    @Test
    fun type0FontsAreDecodedThroughToUnicode() {
        val r = PdfTextExtractor.extract(
            type0Pdf("BT /F4 12 Tf 1 0 0 -1 0 26 Tm <0001000200030004> Tj <0005> Tj <00100011> Tj ET", withToUnicode = true, flateCmap = true)
        )
        assertNull(r.reason)
        assertEquals("Hijk É😀", r.text)
    }

    @Test
    fun type0FontWithoutToUnicodeFallsBackAsUnreadable() {
        val r = PdfTextExtractor.extract(type0Pdf("BT /F4 12 Tf <0034005800440055> Tj ET", withToUnicode = false))
        assertEquals(PdfTextExtractor.REASON_UNREADABLE, r.reason)
    }

    @Test
    fun lineOperatorsStartNewLines() {
        val r = PdfTextExtractor.extract(
            pdf(content("BT 72 700 Td (Line one) Tj 0 -14 Td (Line two) Tj T* (Line three) Tj (Line four) ' 100 0 Td (same line) Tj ET"))
        )
        assertEquals("Line one\nLine two\nLine three\nLine four same line", r.text)
    }

    @Test
    fun kernedRunsOnOneLineAreNotSplitIntoWords() {
        // Chrome-style output: words split into runs moved with Td, real spaces written as glyphs.
        val r = PdfTextExtractor.extract(
            pdf(content("BT /F1 22 Tf 1 0 0 -1 0 26 Tm (Quar) Tj 51.2 0 Td (t) Tj 8.5 0 Td (erly Notes) Tj ET " +
                "BT /F1 11 Tf 1 0 0 -1 0 56 Tm (The date is ) Tj ET BT 1 0 0 -1 90.7 56 Tm (Nov) Tj 21.3 0 Td (ember 22) Tj ET"))
        )
        assertEquals("Quarterly Notes\nThe date is November 22", r.text)
    }

    @Test
    fun textOutsideBtEtAndCommentsAreIgnored() {
        val r = PdfTextExtractor.extract(pdf(content("% (comment) Tj\n(stray) Tj\nBT (kept) Tj ET")))
        assertEquals("kept", r.text)
    }

    @Test
    fun encryptedFilesAreRefused() {
        val r = PdfTextExtractor.extract(pdf(content("BT (secret) Tj ET"), trailer = "/Encrypt 9 0 R"))
        assertEquals(PdfTextExtractor.REASON_ENCRYPTED, r.reason)
        assertEquals("", r.text)
    }

    @Test
    fun garbageAndEmptyInputNeverThrow() {
        assertEquals(PdfTextExtractor.REASON_NOT_PDF, PdfTextExtractor.extract(ByteArray(0)).reason)
        assertEquals(PdfTextExtractor.REASON_NOT_PDF, PdfTextExtractor.extract(ByteArray(5000) { (it * 37).toByte() }).reason)
        val broken = "%PDF-1.4\n1 0 obj\n<< /Filter /FlateDecode >>\nstream\nnot zlib BT".toByteArray()
        assertEquals(PdfTextExtractor.REASON_NO_TEXT, PdfTextExtractor.extract(broken).reason)
        val unterminated = "%PDF-1.4\n1 0 obj\n<< >>\nstream\nBT (open [<4 Tj".toByteArray()
        PdfTextExtractor.extract(unterminated) // must simply not throw
    }

    @Test
    fun truncatesAtMaxChars() {
        val r = PdfTextExtractor.extract(pdf(content("BT (" + "x".repeat(500) + ") Tj ET")), maxChars = 100)
        assertTrue(r.truncated)
        assertEquals(100, r.text.length)
    }

    @Test
    fun imageAndFontStreamsAreSkipped() {
        val image = S("/Type /XObject /Subtype /Image /Filter /DCTDecode", "BT (jpegbytes) Tj ET".toByteArray())
        val font = S("/Length1 20", "BT (fontbytes) Tj ET".toByteArray())
        val r = PdfTextExtractor.extract(pdf(image, font, content("BT (real text) Tj ET")))
        assertEquals("real text", r.text)
        assertEquals(1, r.pagesWithText)
    }

    @Test
    fun inlineImageDataIsNotReadAsText() {
        val r = PdfTextExtractor.extract(pdf(content("BI /W 2 /H 2 ID (fake) Tj BT EI\nBT (after image) Tj ET")))
        assertEquals("after image", r.text)
    }

    @Test
    fun customFontEncodingIsReportedUnreadable() {
        val r = PdfTextExtractor.extract(pdf(content("BT <0102030405060708090A0B0C> Tj ET")))
        assertEquals(PdfTextExtractor.REASON_UNREADABLE, r.reason)
        assertEquals("", r.text)
    }

    @Test
    fun noTextOperatorsMeansNoText() {
        val r = PdfTextExtractor.extract(pdf(content("0 0 m 100 100 l S")))
        assertEquals(PdfTextExtractor.REASON_NO_TEXT, r.reason)
    }
}
