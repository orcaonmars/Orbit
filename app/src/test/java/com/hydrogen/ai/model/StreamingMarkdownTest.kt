package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingMarkdownTest {
    @Test fun hidesBareHeadingMarks() = assertEquals("Day 1\n- walk", StreamingMarkdown.settled("Day 1\n- walk\n\n##"))
    @Test fun hidesBareListMarker() = assertEquals("Intro", StreamingMarkdown.settled("Intro\n- "))
    @Test fun hidesNumberedMarkerAndFence() {
        assertEquals("A", StreamingMarkdown.settled("A\n2."))
        assertEquals("A", StreamingMarkdown.settled("A\n```kotlin"))
    }
    @Test fun keepsLinesWithWords() = assertEquals("A\n## Day 2", StreamingMarkdown.settled("A\n## Day 2"))
    @Test fun keepsPlainText() = assertEquals("Hello", StreamingMarkdown.settled("Hello"))
    @Test fun singleLineMarkerBecomesEmpty() = assertEquals("", StreamingMarkdown.settled("#"))
}
