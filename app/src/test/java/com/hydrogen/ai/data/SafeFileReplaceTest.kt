package com.hydrogen.ai.data

import com.hydrogen.ai.model.PersonalProfile
import com.hydrogen.ai.model.Project
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

class SafeFileReplaceTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun replacementOverwritesExistingContent() {
        val existing = folder.newFile("current.json")
        existing.writeText("previous")
        SafeFileReplace.writeText(existing, "updated")
        assertEquals("updated", existing.readText())
        assertFalse(File(existing.parentFile, existing.name + ".tmp").exists())
    }

    @Test fun failureDuringReplacementPreservesExistingContent() {
        val existing = folder.newFile("important.json")
        existing.writeText("valuable prior data")
        try {
            SafeFileReplace.writeText(existing, "uncommitted new data") { _, _ ->
                throw IOException("simulated disk failure")
            }
            fail("Expected a simulated failure")
        } catch (expected: IOException) {
            assertEquals("simulated disk failure", expected.message)
        }
        assertEquals("valuable prior data", existing.readText())
        assertFalse(File(existing.parentFile, existing.name + ".tmp").exists())
    }

    @Test fun missingParentDirectoryIsCreated() {
        val destination = File(folder.root, "nested/data/state.json")
        SafeFileReplace.writeText(destination, "ok")
        assertEquals("ok", destination.readText())
    }

    @Test fun invalidParentPathDoesNotDeleteAnyExistingFile() {
        val blocker = folder.newFile("not_a_directory")
        blocker.writeText("keep this")
        val impossible = File(blocker, "data.json")
        try {
            SafeFileReplace.writeText(impossible, "unwritten")
            fail("Expected a directory creation failure")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("Cannot create directory"))
        }
        assertEquals("keep this", blocker.readText())
    }

    @Test fun projectStoreCanReplaceAnExistingSnapshot() = runTest {
        val directory = folder.newFolder("project_data")
        val store = FileProjectStore(directory)
        val oldProjects = listOf(Project(id = "p1", name = "Old project"))
        val newProjects = listOf(Project(id = "p1", name = "New project"))
        store.save(oldProjects)
        store.save(newProjects)
        assertEquals(newProjects, store.load())
    }

    @Test fun personalStoreCanReplaceAnExistingProfile() = runTest {
        val directory = folder.newFolder("personal_data")
        val store = FilePersonalStore(directory)
        store.save(PersonalProfile(name = "Ada"))
        store.save(PersonalProfile(name = "Grace"))
        assertEquals("Grace", store.load().name)
    }
}
