package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackGenerationTest {
    @Test fun replacedAndStoppedQueuesCannotDeliverStaleEvents() {
        val generation = PlaybackGeneration()
        generation.advance()
        val first = generation.utteranceId(0)
        assertEquals(0, generation.indexOf(first))
        generation.advance()
        assertNull(generation.indexOf(first))
        val second = generation.utteranceId(2)
        assertEquals(2, generation.indexOf(second))
        generation.advance()
        assertNull(generation.indexOf(second))
        assertNull(generation.indexOf(null))
        assertNull(generation.indexOf(generation.utteranceId(-1)))
    }
}
