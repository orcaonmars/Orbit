package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundingCollectTest {
    private fun event(vararg urls: String): String {
        val chunks = urls.joinToString(",") { """{"web":{"uri":"$it","title":"t-$it"}}""" }
        return """{"candidates":[{"content":{"parts":[{"text":"hi"}]},"groundingMetadata":{"groundingChunks":[$chunks]}}]}"""
    }

    @Test
    fun eventWithoutGroundingKeepsPrevious() {
        val previous = listOf(WebSource("A", "https://a.example/", "a.example"))
        assertSame(previous, GroundingSources.collect("""{"candidates":[{"content":{"parts":[{"text":"x"}]}}]}""", previous))
    }

    @Test
    fun brokenJsonNeverThrows() {
        assertTrue(GroundingSources.collect("{groundingMetadata: oops").isEmpty())
        assertTrue(GroundingSources.collect("""{"groundingMetadata":1,"candidates":"no"}""").isEmpty())
    }

    @Test
    fun mergesAcrossStreamEventsWithoutRepeats() {
        var sources = GroundingSources.collect(event("https://a.example/1", "https://b.example/2"))
        sources = GroundingSources.collect(event("https://b.example/2", "https://c.example/3"), sources)
        assertEquals(listOf("https://a.example/1", "https://b.example/2", "https://c.example/3"), sources.map { it.url })
    }

    @Test
    fun readsAnArrayOfResponsesAndCaps() {
        val many = (1..10).map { "https://s$it.example/" }
        val payload = "[" + event(*many.take(5).toTypedArray()) + "," + event(*many.drop(5).toTypedArray()) + "]"
        val sources = GroundingSources.collect(payload)
        assertEquals(GroundingSources.MAX_SOURCES, sources.size)
        assertEquals("https://s1.example/", sources.first().url)
    }

    @Test
    fun unsafeLinksNeverReachTheFooter() {
        val sources = GroundingSources.collect(event("javascript:alert(1)", "https://ok.example/"))
        val footer = GroundingSources.toMarkdownFooter(sources)
        assertTrue(footer.contains("(https://ok.example/)"))
        assertTrue(!footer.contains("javascript"))
    }
}
