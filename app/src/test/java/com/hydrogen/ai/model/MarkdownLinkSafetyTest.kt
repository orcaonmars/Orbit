package com.hydrogen.ai.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownLinkSafetyTest {
    @Test fun malformedWebDestinationsAreNotClickable() {
        for (url in listOf("https:///path", "https://?q=1", "https://#fragment", "https://example.invalid:99999/a",
            "https://good.invalid@other.invalid/", "https://user:password@example.invalid/", "https://example.invalid\\other.invalid")) {
            assertFalse(url, MarkdownParser.isSafeUrl(url))
        }
    }

    @Test fun ordinaryWebDestinationsRemainClickable() {
        for (url in listOf("http://x", "HTTPS://example.invalid/a_(b)?q=1#top", "https://xn--bcher-kva.example/",
            "http://localhost:8080/path", "http://[::1]:8080/path")) {
            assertTrue(url, MarkdownParser.isSafeUrl(url))
        }
    }

    @Test fun rejectedDestinationKeepsItsReadableLabel() {
        val span = MarkdownParser.parseInline("[Read more](https://trusted.invalid@other.invalid/)").single()
        assertTrue(span.text == "Read more")
        assertNull(span.url)
    }
}
