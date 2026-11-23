package com.hydrogen.ai.data

import com.hydrogen.ai.model.PersonalProfile
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilePersonalStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun anEmptyProfileRemovesTheFileAndAnyHalfWrittenCopy() = runTest {
        val dir = temporaryFolder.newFolder()
        val store = FilePersonalStore(dir)
        store.save(PersonalProfile(name = "  Sam ", replyStyle = "Keep it short."))
        assertEquals(PersonalProfile(name = "Sam", replyStyle = "Keep it short."), store.load())
        File(dir, "orbit_personal.json.tmp").writeText("{\"name\":\"Sa")

        store.save(PersonalProfile())

        assertTrue(store.load().isEmpty)
        assertFalse(File(dir, "orbit_personal.json").exists())
        assertFalse(File(dir, "orbit_personal.json.tmp").exists())
    }

    @Test fun aDamagedOrOversizedFileLoadsAsAnEmptyOrTrimmedProfile() = runTest {
        val dir = temporaryFolder.newFolder()
        File(dir, "orbit_personal.json").writeText("{not json")
        assertTrue(FilePersonalStore(dir).load().isEmpty)

        File(dir, "orbit_personal.json").writeText("{\"name\":\"${"n".repeat(500)}\",\"about\":\"${"a".repeat(9_000)}\"}")
        val loaded = FilePersonalStore(dir).load()
        assertEquals(40, loaded.name.length)
        assertEquals(1_500, loaded.about.length)
    }
}
