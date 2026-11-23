package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTitlerTest {

    @Test
    fun plainSentenceIsCapitalizedAndPreservedWithoutEllipsis() {
        val title = ConversationTitler.titleFor("how do I center a Box in Compose?")
        assertEquals("How do I center a Box in Compose?", title)
        assertTrue(title.length <= ConversationTitler.MAX_CHARS)
        assertFalse(title.endsWith("…"))

        val multiSentence = ConversationTitler.titleFor("Explain Kotlin Flow. Also compare it to LiveData.")
        assertEquals("Explain Kotlin Flow.", multiSentence)

        val multiLine = ConversationTitler.titleFor("Debug my Gradle build\nIt keeps failing offline")
        assertEquals("Debug my Gradle build", multiLine)
    }

    @Test
    fun longTextIsCutAtWordBoundaryWithinMaxCharsWithEllipsis() {
        val exact40 = "This title is exactly forty chars long!!"
        assertEquals(40, exact40.length)
        assertEquals(exact40, ConversationTitler.titleFor(exact40))

        val longText = "explain how Kotlin structured concurrency works across viewModelScope and coroutines"
        val title = ConversationTitler.titleFor(longText)
        assertEquals("Explain how Kotlin structured…", title)
        assertTrue(title.length <= ConversationTitler.MAX_CHARS)
        assertTrue(title.endsWith("…"))

        val singleHugeWord = "supercalifragilisticexpialidociousandmorecharacters"
        val hardCut = ConversationTitler.titleFor(singleHugeWord)
        assertEquals(ConversationTitler.MAX_CHARS, hardCut.length)
        assertTrue(hardCut.endsWith("…"))
    }

    @Test
    fun markdownMarkersAndListBulletsAreStrippedCleanly() {
        val headingAndInline = ConversationTitler.titleFor("  ## **How** do _I_ use `Flow` in Kotlin? ## ")
        assertEquals("How do I use Flow in Kotlin?", headingAndInline)

        val quoteAndBullet = ConversationTitler.titleFor("> - [x] Fix snake_case_var in C# parser")
        assertEquals("Fix snake_case_var in C# parser", quoteAndBullet)

        val orderedList = ConversationTitler.titleFor("1. summarize the release notes")
        assertEquals("Summarize the release notes", orderedList)

        val markdownLink = ConversationTitler.titleFor("Check [Kotlin docs](https://kotlinlang.org) please")
        assertEquals("Check Kotlin docs please", markdownLink)
    }

    @Test
    fun codeOnlyAndLabelOnlyMessagesFallBackToDefaultWhileProseAfterCodeIsUsed() {
        val codeOnly = """
            ```kotlin
            fun main() {
                println("hello")
            }
            ```
        """.trimIndent()
        assertEquals("New chat", ConversationTitler.titleFor(codeOnly))

        val attachedFileOnly = """
            File: MainActivity.kt
            ```kotlin
            class MainActivity
            ```
            (File truncated: only the beginning is included.)
        """.trimIndent()
        assertEquals("New chat", ConversationTitler.titleFor(attachedFileOnly))

        val attachedFileWithQuestion = """
            File: MainActivity.kt
            ```kotlin
            class MainActivity
            ```
            Why does this crash on rotation?
        """.trimIndent()
        assertEquals("Why does this crash on rotation?", ConversationTitler.titleFor(attachedFileWithQuestion))

        val companionPreviewWithQuestion = """
            [PC File: notes.txt · bytes 0..42 of 42 B]
            ```
            some notes
            ```
            summarize these notes
        """.trimIndent()
        assertEquals("Summarize these notes", ConversationTitler.titleFor(companionPreviewWithQuestion))
    }

    @Test
    fun urlOnlyFallsBackAndUrlsInsideProseAreDropped() {
        assertEquals("New chat", ConversationTitler.titleFor("https://developer.android.com/jetpack/compose"))
        assertEquals("New chat", ConversationTitler.titleFor("www.example.com/docs?id=42"))

        val withUrl = ConversationTitler.titleFor("Summarize https://kotlinlang.org/docs/home.html for me")
        assertEquals("Summarize for me", withUrl)

        val urlFirstLineThenProse = ConversationTitler.titleFor("https://example.com\nExplain this page")
        assertEquals("Explain this page", urlFirstLineThenProse)
    }

    @Test
    fun emojiAndUnicodeAreSafeAndLinearTimeWith10kCap() {
        val unicode = ConversationTitler.titleFor("café résumé naïve — explain accents")
        assertEquals("Café résumé naïve — explain accents", unicode)

        val emojiStream = "🚀".repeat(30)
        val cutEmoji = ConversationTitler.titleFor(emojiStream)
        assertTrue(cutEmoji.length <= ConversationTitler.MAX_CHARS)
        assertTrue(cutEmoji.endsWith("…"))
        val beforeEllipsis = cutEmoji.removeSuffix("…")
        assertFalse(beforeEllipsis.last().isHighSurrogate())

        val unpairedSurrogate = "\uD83D"
        assertEquals("New chat", ConversationTitler.titleFor(unpairedSurrogate))

        val huge = "word ".repeat(30_000)
        val hugeTitle = ConversationTitler.titleFor(huge)
        assertTrue(hugeTitle.length <= ConversationTitler.MAX_CHARS)
        assertTrue(hugeTitle.startsWith("Word "))
        assertTrue(hugeTitle.endsWith("…"))
    }

    @Test
    fun blankAndSymbolOnlyInputsReturnNewChat() {
        assertEquals("New chat", ConversationTitler.titleFor(""))
        assertEquals("New chat", ConversationTitler.titleFor("   \n\t  "))
        assertEquals("New chat", ConversationTitler.titleFor("---\n***\n###"))
        assertEquals("New chat", ConversationTitler.titleFor("??? !!! ... !@#$%^&*()"))
        assertEquals("New chat", ConversationTitler.titleFor("> > >"))
    }
}
