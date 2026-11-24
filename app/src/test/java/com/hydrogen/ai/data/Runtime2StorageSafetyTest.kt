package com.hydrogen.ai.data

import com.hydrogen.ai.model.MemoryItem
import com.hydrogen.ai.model.PersonaShareCodec
import com.hydrogen.ai.model.SharedPersona
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Runtime2StorageSafetyTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun switchingMemoryOffAndOnPreservesSavedItems() = runTest {
        val dir = temporaryFolder.newFolder()
        val store = FileUserMemoryStore(dir)
        val item = MemoryItem("test-id", "A saved preference", 42L, pinned = true)
        store.save(UserMemoryState(listOf(item), enabled = true))
        store.save(store.load().copy(enabled = false))
        val disabled = FileUserMemoryStore(dir).load()
        assertFalse(disabled.enabled)
        assertEquals(listOf(item), disabled.items)
        store.save(disabled.copy(enabled = true))
        assertEquals(UserMemoryState(listOf(item), enabled = true), FileUserMemoryStore(dir).load())
    }

    @Test fun unreadablePersonaFileIsNotDeletedDuringLoad() {
        val file = File(temporaryFolder.newFolder(), "personas.json")
        val original = "{incomplete"
        file.writeText(original)
        assertTrue(CustomPersonaStore(file).load().isEmpty())
        assertEquals(original, file.readText())
    }

    @Test fun personaSaveReplacesExistingFileWithoutLeavingTemporaryCopies() {
        val dir = temporaryFolder.newFolder()
        val file = File(dir, "personas.json")
        val first = PersonaShareCodec.toPersona(SharedPersona("First", instructions = "First instruction"), "custom-first")
        val second = PersonaShareCodec.toPersona(SharedPersona("Second", instructions = "Second instruction"), "custom-second")
        val store = CustomPersonaStore(file)
        store.save(listOf(first))
        assertEquals(listOf(first), store.load())
        store.save(listOf(second))
        assertEquals(listOf(second), CustomPersonaStore(file).load())
        assertEquals(listOf("personas.json"), dir.list()!!.toList())
    }

    @Test fun failedPersonaReplacementPreservesDestinationAndCleansStagingFile() {
        val dir = temporaryFolder.newFolder()
        val destination = File(dir, "personas.json").apply { mkdir() }
        val sentinel = File(destination, "preserved").apply { writeText("unchanged") }
        try {
            CustomPersonaStore(destination).save(emptyList())
            fail("Replacing a nonempty directory must fail")
        } catch (_: IOException) {
            assertEquals("unchanged", sentinel.readText())
            assertEquals(listOf("personas.json"), dir.list()!!.toList())
        }
    }
}