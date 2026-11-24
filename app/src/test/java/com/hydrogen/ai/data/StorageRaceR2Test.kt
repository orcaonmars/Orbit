package com.hydrogen.ai.data

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageRaceR2Test {
    @get:Rule val folder = TemporaryFolder()

    @Test fun overlappingWritesNeverShareTheStagingFile() {
        val target = folder.newFile("state.json")
        val firstReady = CountDownLatch(1)
        val secondDone = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        try {
            val first = pool.submit {
                SafeFileReplace.writeText(target, "first") { temp, dest ->
                    firstReady.countDown()
                    check(secondDone.await(5, TimeUnit.SECONDS))
                    assertEquals("first", temp.toFile().readText())
                    Files.move(temp, dest, REPLACE_EXISTING)
                }
            }
            assertTrue(firstReady.await(5, TimeUnit.SECONDS))
            SafeFileReplace.writeText(target, "second")
            assertEquals("second", target.readText())
            secondDone.countDown()
            first.get(5, TimeUnit.SECONDS)
            assertEquals("first", target.readText())
            assertEquals(listOf("state.json"), folder.root.list()!!.sorted())
        } finally {
            secondDone.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun explicitDeleteClearsLegacyAndUniqueTempCopiesWithoutTouchingOtherFiles() {
        val target = folder.newFile("private.json")
        folder.newFile("private.json.tmp")
        folder.newFile("private.json.123456.tmp")
        val unrelated = folder.newFile("other.json.tmp")
        SafeFileReplace.deleteSnapshot(target)
        assertFalse(target.exists())
        assertEquals(listOf(unrelated.name), folder.root.list()!!.sorted())
    }

    @Test fun optingOutPersistsEvenWhenTheMemorySnapshotCannotBeWritten() = runTest {
        val dir = folder.newFolder()
        val blocked = File(dir, "orbit_memory.json")
        assertTrue(blocked.mkdir())
        File(blocked, "existing").writeText("block deletion")
        try {
            FileUserMemoryStore(dir).save(UserMemoryState(enabled = false))
            fail("Expected a failed snapshot delete")
        } catch (_: IOException) {
            assertTrue(File(dir, "orbit_memory_off").isFile)
            assertFalse(FileUserMemoryStore(dir).load().enabled)
        }
    }

    @Test fun failedEnableDoesNotRemoveTheOptOutMarker() = runTest {
        val dir = folder.newFolder()
        File(dir, "orbit_memory_off").writeText("off")
        val blocked = File(dir, "orbit_memory.json")
        assertTrue(blocked.mkdir())
        File(blocked, "existing").writeText("block deletion")
        try {
            FileUserMemoryStore(dir).save(UserMemoryState(enabled = true))
            fail("Expected a failed snapshot delete")
        } catch (_: IOException) {
            assertFalse(FileUserMemoryStore(dir).load().enabled)
        }
    }

    @Test fun clearingMemoryRemovesSavedAndAbandonedCopies() = runTest {
        val dir = folder.newFolder()
        val store = FileUserMemoryStore(dir)
        File(dir, "orbit_memory.json").writeText("[]")
        File(dir, "orbit_memory.json.42.tmp").writeText("old content")
        store.save(UserMemoryState(enabled = false))
        assertEquals(listOf("orbit_memory_off"), dir.list()!!.sorted())
        assertFalse(store.load().enabled)
        store.save(UserMemoryState(enabled = true))
        assertTrue(store.load().enabled)
        assertTrue(dir.list()!!.isEmpty())
    }
}