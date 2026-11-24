package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioMemorySearchTest {
    @Test fun pinningRoundTripsWithoutChangingOtherNotes() {
        val notes = listOf(MemoryItem("a", "First note", 1), MemoryItem("b", "Second note", 2))
        val updated = UserMemory.setPinned(notes, "a", true)
        assertTrue(updated.first().pinned)
        assertEquals(notes[1], updated[1])
        assertEquals(updated, UserMemory.decode(UserMemory.encode(updated)))
        assertEquals(notes, UserMemory.setPinned(updated, "a", false))
        assertTrue(UserMemory.systemBlock(updated)!!.indexOf("First note") < UserMemory.systemBlock(updated)!!.indexOf("Second note"))
    }

    @Test fun searchIsLocalAccentInsensitiveAndStable() {
        val items = listOf(MemoryItem("a", "Caf\u00e9 in Paris", 1), MemoryItem("b", "Paris metro", 2), MemoryItem("c", "Tea", 3))
        assertEquals(items.take(2), UserMemory.search(items, " PARIS "))
        assertEquals(items.take(1), UserMemory.search(items, "cafe paris"))
        assertEquals(items, UserMemory.search(items, " "))
        assertTrue(UserMemory.search(items, "missing").isEmpty())
        assertEquals(3, items.size)
    }
}
