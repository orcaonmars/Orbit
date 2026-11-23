package com.hydrogen.ai.model

import com.hydrogen.ai.data.PromptLibraryStore
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PromptLibraryTest {

    @Test
    fun addUsesFirstLineTitleAndIgnoresBlankOrDuplicateText() {
        val start = PromptLibrary.starterPrompts
        val added = PromptLibrary.add(start, "  \n  Rewrite   as a formal email\nbody", id = "p1")

        assertEquals("p1", added.first().id)
        assertEquals("Rewrite as a formal email", added.first().title)
        assertEquals(start.size + 1, added.size)
        assertSame(added, PromptLibrary.add(added, "   \n ", id = "p2"))
        assertSame(added, PromptLibrary.add(added, "  \n  Rewrite   as a formal email\nbody  ", id = "p3"))
        assertEquals(start, PromptLibrary.remove(added, "p1"))
        assertEquals(PromptLibrary.MAX_TITLE_LENGTH, PromptLibrary.titleFor("x".repeat(200))!!.length)
    }

    @Test
    fun insertKeepsExistingDraft() {
        assertEquals("Summarize:", PromptLibrary.insertIntoDraft("  ", "Summarize:"))
        assertEquals("my notes\n\nSummarize:", PromptLibrary.insertIntoDraft("my notes  \n", "Summarize:"))
    }

    @Test
    fun storeRoundTripsAndFallsBackToStartersOnMissingOrCorruptFile() {
        val dir = Files.createTempDirectory("prompts").toFile()
        try {
            val file = File(dir, "hydrogen_prompts.json")
            val store = PromptLibraryStore(file)
            assertEquals(PromptLibrary.starterPrompts, store.load())

            val saved = PromptLibrary.add(emptyList(), "Plan my week", id = "w")
            store.save(saved)
            assertEquals(saved, store.load())

            file.writeText("{not json")
            assertNull(PromptLibrary.decode("{not json"))
            assertEquals(PromptLibrary.starterPrompts, store.load())
        } finally {
            dir.deleteRecursively()
        }
    }
}
