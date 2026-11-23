package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareIntentParserTest {

    private val send = ShareIntentParser.ACTION_SEND
    private val sendMultiple = ShareIntentParser.ACTION_SEND_MULTIPLE

    @Test
    fun sharedTextAndSubjectBecomeDraftText() {
        val shared = ShareIntentParser.parse(send, "text/plain", "https://example.com/a", "Great article", emptyList())!!
        assertEquals("Great article\n\nhttps://example.com/a", shared.text)
        assertTrue(shared.imageUris.isEmpty())
        val noDup = ShareIntentParser.parse(send, "text/plain", "Great article https://x.y", "Great article", emptyList())!!
        assertEquals("Great article https://x.y", noDup.text)
    }

    @Test
    fun nonShareActionsAndEmptySharesAreIgnored() {
        assertNull(ShareIntentParser.parse("android.intent.action.MAIN", null, "hi", null, emptyList()))
        assertNull(ShareIntentParser.parse(null, "text/plain", "hi", null, emptyList()))
        assertNull(ShareIntentParser.parse(send, "text/plain", "   ", null, emptyList()))
    }

    @Test
    fun onlyContentImageStreamsAreAcceptedAndCapped() {
        val streams = (1..12).map { "content://media/external/images/$it" } +
            listOf("file:///data/data/com.hydrogen.ai/files/secret.png", null)
        val shared = ShareIntentParser.parse(sendMultiple, "image/*", null, null, streams)!!
        assertEquals(ShareIntentParser.MAX_SHARED_IMAGES, shared.imageUris.size)
        assertTrue(shared.imageUris.all { it.startsWith("content://") })
        assertEquals(3, shared.droppedItems) // 2 over the cap + the file:// uri
        assertNull(shared.declaredImageType) // wildcard is never used as a type hint
        val png = ShareIntentParser.parse(send, "image/png", null, null, listOf("content://media/1"))!!
        assertEquals("image/png", png.declaredImageType)
    }

    @Test
    fun nonImageStreamsAreNotRead() {
        val shared = ShareIntentParser.parse(send, "application/pdf", null, null, listOf("content://docs/1"))!!
        assertTrue(shared.isEmpty)
        assertEquals(1, shared.droppedItems)
        val onlyFile = ShareIntentParser.parse(send, "image/png", null, null, listOf("FILE:///sdcard/x.png"))!!
        assertTrue(onlyFile.imageUris.isEmpty())
        assertEquals(1, onlyFile.droppedItems)
    }

    @Test
    fun longTextIsTruncated() {
        val shared = ShareIntentParser.parse(send, "text/plain", "a".repeat(30_000), null, emptyList())!!
        assertEquals(ShareIntentParser.MAX_SHARED_TEXT_CHARS, shared.text!!.length)
    }

    @Test
    fun mergeKeepsWhatTheUserAlreadyTyped() {
        assertEquals("shared", ShareIntentParser.mergeIntoDraft("", " shared "))
        assertEquals("mine\n\nshared", ShareIntentParser.mergeIntoDraft("mine  ", "shared"))
        assertEquals("mine", ShareIntentParser.mergeIntoDraft("mine", null))
    }
}
