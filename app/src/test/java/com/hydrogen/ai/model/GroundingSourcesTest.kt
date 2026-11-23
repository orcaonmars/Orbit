package com.hydrogen.ai.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundingSourcesTest {

    private fun obj(raw: String): JsonObject = Json.parseToJsonElement(raw).jsonObject

    @Test
    fun parsesTypicalResponseWithThreeChunks() {
        val candidate = obj(
            """
            {
              "groundingMetadata": {
                "webSearchQueries": ["kotlin serialization", "android compose"],
                "groundingChunks": [
                  {"web": {"uri": "https://www.kotlinlang.org/docs/serialization.html", "title": "Kotlin Serialization"}},
                  {"web": {"uri": "https://developer.android.com/jetpack/compose", "title": "Jetpack Compose"}},
                  {"web": {"uri": "http://example.org/guide", "title": "Guide"}}
                ]
              }
            }
            """.trimIndent()
        )

        val sources = GroundingSources.parse(candidate)
        assertEquals(3, sources.size)
        assertEquals(
            WebSource(
                title = "Kotlin Serialization",
                url = "https://www.kotlinlang.org/docs/serialization.html",
                domain = "kotlinlang.org"
            ),
            sources[0]
        )
        assertEquals("developer.android.com", sources[1].domain)
        assertEquals("http://example.org/guide", sources[2].url)
    }

    @Test
    fun missingGroundingMetadataReturnsEmpty() {
        assertTrue(GroundingSources.parse(obj("""{"content":{"parts":[{"text":"hi"}]}}""")).isEmpty())
        assertTrue(GroundingSources.searchQueries(obj("""{}""")).isEmpty())
    }

    @Test
    fun wrongTypesReturnEmptyWithoutThrowing() {
        val badShapes = listOf(
            """{"groundingMetadata": "not-an-object"}""",
            """{"groundingMetadata": null}""",
            """{"groundingMetadata": {"groundingChunks": "not-an-array", "webSearchQueries": 42}}""",
            """{"groundingMetadata": {"groundingChunks": [null, 123, "str", {"web": "bad"}, {"web": {"uri": 99, "title": []}}]}}"""
        )

        for (raw in badShapes) {
            val candidate = obj(raw)
            assertTrue(GroundingSources.parse(candidate).isEmpty())
            assertTrue(GroundingSources.searchQueries(candidate).isEmpty())
        }
    }

    @Test
    fun unsafeAndHostlessUrlsAreDropped() {
        val candidate = obj(
            """
            {
              "groundingMetadata": {
                "groundingChunks": [
                  {"web": {"uri": "javascript:alert(1)", "title": "XSS"}},
                  {"web": {"uri": "data:text/html,<h1>hi</h1>", "title": "Data"}},
                  {"web": {"uri": "file:///etc/passwd", "title": "Local File"}},
                  {"web": {"uri": "https:///missing-host", "title": "No Host"}},
                  {"web": {"uri": "/relative/path", "title": "Relative"}},
                  {"web": {"uri": "https://valid.example.com/ok", "title": "Valid"}}
                ]
              }
            }
            """.trimIndent()
        )

        val sources = GroundingSources.parse(candidate)
        assertEquals(1, sources.size)
        assertEquals("https://valid.example.com/ok", sources[0].url)
    }

    @Test
    fun duplicateUrlsAreDedupedKeepingFirst() {
        val candidate = obj(
            """
            {
              "groundingMetadata": {
                "groundingChunks": [
                  {"web": {"uri": "https://example.com/a", "title": "First Title"}},
                  {"web": {"uri": "https://example.com/a", "title": "Second Title"}},
                  {"web": {"uri": "https://example.com/b", "title": "Other"}}
                ]
              }
            }
            """.trimIndent()
        )

        val sources = GroundingSources.parse(candidate)
        assertEquals(2, sources.size)
        assertEquals("First Title", sources[0].title)
        assertEquals("Other", sources[1].title)
    }

    @Test
    fun blankOrMissingTitleFallsBackToDomain() {
        val candidate = obj(
            """
            {
              "groundingMetadata": {
                "groundingChunks": [
                  {"web": {"uri": "https://www.news.example.com/1", "title": "   \t\n  "}},
                  {"web": {"uri": "https://docs.example.org/2"}},
                  {"web": {"uri": "https://api.example.net/3", "title": null}}
                ]
              }
            }
            """.trimIndent()
        )

        val sources = GroundingSources.parse(candidate)
        assertEquals(listOf("news.example.com", "docs.example.org", "api.example.net"), sources.map { it.title })
    }

    @Test
    fun longTitleIsCutAndControlCharsAreRemoved() {
        val longTitle = "\\u0000  " + "A".repeat(120) + "\\u0007\\n"
        val ctrlTitle = "  Hello\\u0001\\tWorld\\n  "
        val candidate = obj(
            """
            {
              "groundingMetadata": {
                "groundingChunks": [
                  {"web": {"uri": "https://example.com/long", "title": "$longTitle"}},
                  {"web": {"uri": "https://example.com/ctrl", "title": "$ctrlTitle"}}
                ]
              }
            }
            """.trimIndent()
        )

        val sources = GroundingSources.parse(candidate)
        assertEquals(2, sources.size)
        assertEquals(GroundingSources.MAX_TITLE, sources[0].title.length)
        assertTrue(sources[0].title.endsWith("…"))
        assertEquals("HelloWorld", sources[1].title)
    }

    @Test
    fun capsAtEightSourcesAfterDedupe() {
        val chunks = buildString {
            append("""{"web":{"uri":"https://example.com/1","title":"One"}},""")
            append("""{"web":{"uri":"https://example.com/1","title":"Duplicate One"}},""")
            for (i in 2..12) {
                if (i > 2) append(',')
                append("""{"web":{"uri":"https://example.com/$i","title":"Title $i"}}""")
            }
        }
        val candidate = obj("""{"groundingMetadata":{"groundingChunks":[$chunks]}}""")

        val sources = GroundingSources.parse(candidate)
        assertEquals(GroundingSources.MAX_SOURCES, sources.size)
        assertEquals("Title 8", sources.last().title)
    }

    @Test
    fun footerIsEmptyForNoSources() {
        assertEquals("", GroundingSources.toMarkdownFooter(emptyList()))
    }

    @Test
    fun footerEscapesBracketsAndEncodesUrlSpacesAndParens() {
        val sources = listOf(
            WebSource(
                title = "Topic [Draft] (v2)",
                url = "https://en.wikipedia.org/wiki/Kotlin (programming language)",
                domain = "en.wikipedia.org"
            ),
            WebSource(
                title = "Plain Title",
                url = "https://example.com/path",
                domain = "example.com"
            )
        )

        val expected = "\n\n**Sources**\n" +
            "1. [Topic \\[Draft\\] \\(v2\\)](https://en.wikipedia.org/wiki/Kotlin%20%28programming%20language%29)\n" +
            "2. [Plain Title](https://example.com/path)"
        assertEquals(expected, GroundingSources.toMarkdownFooter(sources))
    }

    @Test
    fun searchQueriesDropsBlanksAndCapsAtFive() {
        val candidate = obj(
            """
            {
              "groundingMetadata": {
                "webSearchQueries": ["", "   ", null, 123, "q1", " q2 ", "q3", "q4", "q5", "q6"]
              }
            }
            """.trimIndent()
        )

        assertEquals(
            listOf("q1", "q2", "q3", "q4", "q5"),
            GroundingSources.searchQueries(candidate)
        )
    }
}
