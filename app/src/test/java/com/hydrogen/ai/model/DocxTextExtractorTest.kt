package com.hydrogen.ai.model

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxTextExtractorTest {

    private val ns = """xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" """ +
        """xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006""""

    private fun doc(body: String) =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" + "\r\n" +
            """<w:document $ns><w:body>$body<w:sectPr/></w:body></w:document>"""

    private fun p(text: String, pPr: String = "") =
        "<w:p>${if (pPr.isEmpty()) "" else "<w:pPr>$pPr</w:pPr>"}<w:r><w:t xml:space=\"preserve\">$text</w:t></w:r></w:p>"

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, data) in entries) {
                z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun docx(documentXml: String) = zip(
        "[Content_Types].xml" to "<Types/>".toByteArray(),
        "word/styles.xml" to "<w:styles/>".toByteArray(),
        "word/document.xml" to documentXml.toByteArray(Charsets.UTF_8)
    )

    @Test
    fun paragraphsHeadingsAndListsBecomeMarkdownLines() {
        val xml = doc(
            p("Trip plan", "<w:pStyle w:val=\"Title\"/>") +
                p("Day one", "<w:pStyle w:val=\"Heading2\"/>") +
                p("Pack light &amp; early.") +
                p("Passport", "<w:pStyle w:val=\"ListParagraph\"/><w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"3\"/></w:numPr>") +
                p("Visa copy", "<w:numPr><w:ilvl w:val=\"1\"/><w:numId w:val=\"3\"/></w:numPr>") +
                "<w:p/>" +
                p("Café &#x2615; at 9&#58;00")
        )
        val result = DocxTextExtractor.extract(docx(xml))
        assertNull(result.reason)
        assertFalse(result.truncated)
        assertEquals(
            "# Trip plan\n\n## Day one\n\nPack light & early.\n\n- Passport\n\n  - Visa copy\n\nCafé ☕ at 9:00",
            result.text
        )
    }

    @Test
    fun runsJoinTabsAndBreaksAreKeptAndTabStopsAreNot() {
        val xml = doc(
            "<w:p><w:pPr><w:tabs><w:tab w:val=\"left\" w:pos=\"720\"/></w:tabs></w:pPr>" +
                "<w:r><w:t>Name</w:t></w:r><w:r><w:tab/><w:t>Sam</w:t></w:r>" +
                "<w:r><w:br/><w:t xml:space=\"preserve\">Second line</w:t></w:r></w:p>"
        )
        assertEquals("Name\tSam\nSecond line", DocxTextExtractor.extract(docx(xml)).text)
    }

    @Test
    fun tablesBecomeMarkdownTablesWithPipesEscaped() {
        val cell = { t: String -> "<w:tc><w:tcPr/>${p(t)}</w:tc>" }
        val xml = doc(
            p("Prices") +
                "<w:tbl><w:tblPr/>" +
                "<w:tr>${cell("Item")}${cell("Cost")}</w:tr>" +
                "<w:tr>${cell("Tea | green")}${cell("3")}</w:tr>" +
                "<w:tr>${cell("Cake")}<w:tc>${p("4")}${p("each")}</w:tc></w:tr>" +
                "</w:tbl>" +
                p("After the table")
        )
        assertEquals(
            "Prices\n\n| Item | Cost |\n| --- | --- |\n| Tea \\| green | 3 |\n| Cake | 4 each |\n\nAfter the table",
            DocxTextExtractor.extract(docx(xml)).text
        )
    }

    @Test
    fun deletedTextFieldCodesAndFallbackCopiesAreLeftOut() {
        val xml = doc(
            "<w:p><w:r><w:t>Kept</w:t></w:r><w:del><w:r><w:delText>gone</w:delText></w:r></w:del>" +
                "<w:r><w:instrText> PAGE </w:instrText></w:r></w:p>" +
                "<w:p><w:r><mc:AlternateContent><mc:Choice Requires=\"wps\"><w:drawing><w:txbxContent>" +
                p("Box text") +
                "</w:txbxContent></w:drawing></mc:Choice><mc:Fallback><w:pict><w:txbxContent>" +
                p("Box text") +
                "</w:txbxContent></w:pict></mc:Fallback></mc:AlternateContent></w:r><w:r><w:t>Outer</w:t></w:r></w:p>"
        )
        val text = DocxTextExtractor.extract(docx(xml)).text
        assertEquals("Kept\n\nBox text\n\nOuter", text)
    }

    @Test
    fun longDocumentsAreCutAtTheLimit() {
        val xml = doc((1..200).joinToString("") { p("Line $it of the long file.") })
        val result = DocxTextExtractor.extract(docx(xml), maxChars = 100)
        assertNull(result.reason)
        assertTrue(result.truncated)
        assertTrue(result.text.length <= 100)
        assertTrue(result.text.startsWith("Line 1 of the long file."))
    }

    @Test
    fun notAWordFileGivesAReasonAndNeverThrows() {
        assertEquals(DocxTextExtractor.REASON_NOT_DOCX, DocxTextExtractor.extract("hello".toByteArray()).reason)
        assertEquals(DocxTextExtractor.REASON_NOT_DOCX, DocxTextExtractor.extract(ByteArray(0)).reason)
        // A ZIP without word/document.xml (an .xlsx, say).
        val xlsx = zip("xl/workbook.xml" to "<workbook/>".toByteArray())
        assertEquals(DocxTextExtractor.REASON_NOT_DOCX, DocxTextExtractor.extract(xlsx).reason)
        // Password-protected Word files and old .doc files are OLE compound files.
        val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
        assertEquals(DocxTextExtractor.REASON_ENCRYPTED, DocxTextExtractor.extract(ole + ByteArray(512)).reason)
        // A truncated ZIP.
        val cut = docx(doc(p("Hello"))).let { it.copyOf(it.size / 2) }
        val r = DocxTextExtractor.extract(cut)
        assertTrue(r.reason != null || r.text == "Hello")
    }

    @Test
    fun emptyDocumentHasNoText() {
        assertEquals(DocxTextExtractor.REASON_NO_TEXT, DocxTextExtractor.extract(docx(doc("<w:p/><w:p></w:p>"))).reason)
    }

    @Test
    fun aZipBombStopsAtTheSizeLimit() {
        // 13 MB of zeros compresses to a few KB; the reader must stop at its cap, not inflate it all.
        val huge = ByteArray(DocxTextExtractor.MAX_XML_BYTES + 1024 * 1024)
        val result = DocxTextExtractor.extract(docx(String(huge, Charsets.ISO_8859_1)))
        assertEquals(DocxTextExtractor.REASON_TOO_BIG, result.reason)
    }

    @Test
    fun entitiesDecodeAndBadOnesStayAsTyped() {
        assertEquals("<a> & \"b\" 'c' é", DocxTextExtractor.decodeEntities("&lt;a&gt; &amp; &quot;b&quot; &apos;c&apos; &#233;"))
        assertEquals("&nope; &#xD800; & x", DocxTextExtractor.decodeEntities("&nope; &#xD800; & x"))
    }

    @Test
    fun headingStylesMap() {
        assertEquals(1, DocxTextExtractor.headingLevel("Heading1"))
        assertEquals(6, DocxTextExtractor.headingLevel("heading6"))
        assertEquals(1, DocxTextExtractor.headingLevel("Title"))
        assertEquals(0, DocxTextExtractor.headingLevel("Heading7"))
        assertEquals(0, DocxTextExtractor.headingLevel("Normal"))
        assertEquals(0, DocxTextExtractor.headingLevel(null))
    }

    @Test
    fun policyClassifiesWordFilesAndExplainsOldOnes() {
        assertEquals(FileAttachPolicy.Kind.WORD, FileAttachPolicy.classify(FileAttachPolicy.DOCX_MIME, "notes"))
        assertEquals(FileAttachPolicy.Kind.WORD, FileAttachPolicy.classify("application/octet-stream", "Notes.DOCX"))
        assertEquals(FileAttachPolicy.Kind.UNSUPPORTED, FileAttachPolicy.classify("application/msword", "old.doc"))
        assertTrue(FileAttachPolicy.unsupportedMessage("old.doc").contains("Save it as .docx"))
        assertTrue(FileAttachPolicy.unsupportedMessage("a.zip").contains("a Word file"))
        for (reason in listOf(null, DocxTextExtractor.REASON_ENCRYPTED, DocxTextExtractor.REASON_NO_TEXT, DocxTextExtractor.REASON_TOO_BIG)) {
            val message = FileAttachPolicy.wordFailureMessage("a.docx", reason)
            assertFalse(message, message.contains('—') || message.contains('!'))
        }
    }

    @Test
    fun wordTextGoesIntoTheDraftFenced() {
        val result = DocxTextExtractor.extract(docx(doc(p("Use ``` fences"))))
        val draft = FileAttachPolicy.formatForDraft("Report.docx", result.text, result.truncated)
        assertTrue(draft.startsWith("File: Report.docx\n````\n"))
        assertTrue(draft.endsWith("\n````"))
    }
}
