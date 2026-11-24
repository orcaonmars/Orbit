package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyPlaybackPolicyTest {
    @Test fun resumesAtCurrentChunkWithoutSkippingSpeech() {
        assertEquals(listOf("two", "three"), ReplyPlaybackPolicy.remaining(listOf("one", "two", "three"), 1))
    }
    @Test fun clampsBadChunkIndicesAndHandlesEmptyQueue() {
        assertEquals(listOf("one"), ReplyPlaybackPolicy.remaining(listOf("one"), 99))
        assertEquals(listOf("one"), ReplyPlaybackPolicy.remaining(listOf("one"), -1))
        assertEquals(emptyList<String>(), ReplyPlaybackPolicy.remaining(emptyList(), 0))
    }
    @Test fun acceptsSupportedRatesAndRejectsInvalidRates() {
        for (rate in ReplyPlaybackPolicy.speeds) assertEquals(rate, ReplyPlaybackPolicy.normalizeSpeed(rate), 0f)
        assertEquals(1f, ReplyPlaybackPolicy.normalizeSpeed(Float.NaN), 0f)
        assertEquals(1f, ReplyPlaybackPolicy.normalizeSpeed(99f), 0f)
    }
}