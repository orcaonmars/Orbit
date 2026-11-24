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
    fun searchMatchesTitleAndBodyAndPreservesOrder() {
        val a = SavedPrompt("a", "Code review", "Check security issues")
        val b = SavedPrompt("b", "Security review", "Check Kotlin code")
        val c = SavedPrompt("c", "Translate", "French")
        val list = listOf(a, b, c)
        assertSame(list, PromptLibrary.search(list, " "))
        assertEquals(listOf(a, b), PromptLibrary.search(list, " CODE   security "))
        assertEquals(listOf(c), PromptLibrary.search(list, "french"))
        assertEquals(emptyList<SavedPrompt>(), PromptLibrary.search(list, "missing"))
    }

    @Test
    fun previewCollapsesLinesAndIsBoundedWithoutEditingOriginal() {
        val original = "First\n second\t" + "x".repeat(300)
        val prompt = SavedPrompt("p", "Title", original)
        assertEquals("First second " + "x".repeat(167), PromptLibrary.preview(prompt))
        assertEquals(original, prompt.text)
    }

    @Test
    fun staleEditsCannotOverwriteNewerSavedPrompts() {
        val dir = Files.createTempDirectory("prompt-conflict").toFile()
        try {
            val file = File(dir, "prompts.json")
            val first = PromptLibraryStore(file)
            val second = PromptLibraryStore(file)
            val snapshot = first.loadForEditing()
            val updated = PromptLibrary.add(snapshot, "New prompt", "new")
            org.junit.Assert.assertTrue(first.saveIfUnchanged(snapshot, updated))
            org.junit.Assert.assertFalse(second.saveIfUnchanged(snapshot, emptyList()))
            assertEquals(updated, second.loadForEditing())
            file.writeText("{corrupt")
            org.junit.Assert.assertThrows(java.io.IOException::class.java) { first.saveIfUnchanged(updated, emptyList()) }
            assertEquals("{corrupt", file.readText())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun invalidLibraryCannotBeOpenedForEditing() {
        val dir = Files.createTempDirectory("prompt-strict").toFile()
        try {
            val file = File(dir, "prompts.json")
            val store = PromptLibraryStore(file)
            assertEquals(PromptLibrary.starterPrompts, store.loadForEditing())
            file.writeText("{broken")
            org.junit.Assert.assertThrows(java.io.IOException::class.java) { store.loadForEditing() }
            assertEquals("{broken", file.readText())
            store.save(emptyList())
            assertEquals(emptyList<SavedPrompt>(), store.loadForEditing())
        } finally { dir.deleteRecursively() }
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
