package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectsTest {

    private fun file(id: String, text: String, name: String = "$id.txt", truncated: Boolean = false) =
        ProjectFile(id = id, name = name, text = text, truncated = truncated)

    @Test
    fun namesAreOneTrimmedLineAndBlankIsNoName() {
        assertEquals("Trip to Japan", Projects.normalizeName("  Trip \n to\tJapan  "))
        assertEquals(Projects.MAX_NAME, Projects.normalizeName("x".repeat(200))!!.length)
        assertNull(Projects.normalizeName("   \n "))
    }

    @Test
    fun cleanDropsEmptyAndDuplicateFilesAndCapsLongOnes() {
        val long = "a".repeat(Projects.MAX_FILE_CHARS + 10)
        val cleaned = Projects.clean(
            Project(
                id = "p",
                name = "  ",
                instructions = "  Keep it short.\r\n  ",
                files = listOf(
                    file("1", "hello", name = "we\"ird<name>.txt"),
                    file("1", "duplicate"),
                    file("2", "   "),
                    file("", "no id"),
                    file("3", long)
                )
            )
        )
        assertEquals("Untitled project", cleaned.name)
        assertEquals("Keep it short.", cleaned.instructions)
        assertEquals(listOf("1", "3"), cleaned.files.map { it.id })
        assertFalse(cleaned.files[0].name.contains("\"") || cleaned.files[0].name.contains("<") || cleaned.files[0].name.contains(">"))
        assertEquals(Projects.MAX_FILE_CHARS, cleaned.files[1].text.length)
        assertTrue(cleaned.files[1].truncated)
        assertFalse(cleaned.files[0].truncated)
    }

    @Test
    fun aProjectHoldsAtMostTwentyFilesAndNoBlankOnes() {
        var project = Project(id = "p", name = "Notes")
        repeat(Projects.MAX_FILES) { i -> project = Projects.withFile(project, file("f$i", "text $i"), 5L)!! }
        assertEquals(Projects.MAX_FILES, project.files.size)
        assertEquals(5L, project.updatedAtEpochMs)
        assertNull(Projects.withFile(project, file("extra", "more"), 6L))
        assertNull(Projects.withFile(Project(id = "q", name = "Empty"), file("blank", "  \n "), 6L))
    }

    @Test
    fun systemBlockNamesTheProjectThenInstructionsThenFiles() {
        val block = Projects.systemBlock(
            Project(
                id = "p",
                name = "Trip",
                instructions = "Keep costs in euros.",
                files = listOf(file("1", "Day 1: Kyoto", name = "plan.txt"), file("2", "Hotel list", name = "hotels.md"))
            )
        )!!
        val name = block.indexOf("This chat is part of the user's project \"Trip\".")
        val instructions = block.indexOf("Keep costs in euros.")
        val plan = block.indexOf("<file name=\"plan.txt\">\nDay 1: Kyoto\n</file>")
        val hotels = block.indexOf("<file name=\"hotels.md\">\nHotel list\n</file>")
        assertEquals(0, name)
        assertTrue(name < instructions && instructions < plan && plan < hotels)
        assertFalse(block.contains("left out"))
    }

    @Test
    fun noProjectMeansNoBlockAndAnEmptyProjectIsJustItsName() {
        assertNull(Projects.systemBlock(null))
        assertEquals(
            "This chat is part of the user's project \"Ideas\".",
            Projects.systemBlock(Project(id = "p", name = "Ideas"))
        )
    }

    @Test
    fun filesShareOneBudgetAndWhatDoesNotFitIsSaid() {
        val big = "b".repeat(Projects.FILES_BUDGET_CHARS - 100)
        val block = Projects.systemBlock(
            Project(
                id = "p",
                name = "Big",
                files = listOf(
                    file("1", big, name = "first.txt"),
                    file("2", "c".repeat(500), name = "second.txt"),
                    file("3", "never shown", name = "third.txt")
                )
            )
        )!!
        assertTrue(block.contains(big))
        assertTrue(block.contains("<file name=\"second.txt\">\n" + "c".repeat(100) + "\n(The rest of this file did not fit and was left out.)"))
        assertFalse(block.contains("never shown"))
        assertTrue(block.contains("These project files were too long to include this time: third.txt."))
    }

    @Test
    fun aFileSavedOnlyInPartSaysSo() {
        val block = Projects.systemBlock(
            Project(id = "p", name = "Docs", files = listOf(file("1", "Start of a long manual", truncated = true)))
        )!!
        assertTrue(block.contains("Start of a long manual\n(Only the beginning of this file was saved.)"))
    }

    @Test
    fun projectOfIgnoresUnknownProjects() {
        val projects = listOf(Project(id = "p1", name = "One"))
        val chat = Conversation(id = "c", title = "t", createdAtEpochMs = 1L, updatedAtEpochMs = 1L, projectId = "p1")
        assertEquals("One", Projects.projectOf(chat, projects)?.name)
        assertNull(Projects.projectOf(chat.copy(projectId = "gone"), projects))
        assertNull(Projects.projectOf(chat.copy(projectId = null), projects))
        assertNull(Projects.projectOf(null, projects))
    }

    @Test
    fun encodeAndDecodeRoundTrip() {
        val projects = listOf(
            Project(id = "p1", name = "Trip", instructions = "Euros.", files = listOf(file("f", "Day 1")), createdAtEpochMs = 3L),
            Project(id = "p2", name = "Work")
        )
        val decoded = Projects.decode(Projects.encode(projects))
        assertEquals(projects.map(Projects::clean), decoded)
    }

    @Test
    fun aDamagedFileGivesNoProjectsAndBadEntriesAreDropped() {
        assertTrue(Projects.decode("not json").isEmpty())
        assertTrue(Projects.decode("").isEmpty())
        val text = """{"version":1,"projects":[{"id":"","name":"No id"},{"id":"a","name":"A"},{"id":"a","name":"Again"},{"id":"b","name":"  "}],"future":true}"""
        val decoded = Projects.decode(text)
        assertEquals(listOf("a", "b"), decoded.map { it.id })
        assertEquals("A", decoded[0].name)
        assertEquals("Untitled project", decoded[1].name)
    }

    @Test
    fun copyAvoidsDashesAndExclamationMarks() {
        val block = Projects.systemBlock(
            Project(
                id = "p",
                name = "Trip",
                files = listOf(file("1", "x".repeat(Projects.FILES_BUDGET_CHARS + 5), truncated = true), file("2", "y"))
            )
        )
        assertNotNull(block)
        val withoutFileText = block!!.replace("x", "").replace("y", "")
        assertFalse(withoutFileText.contains("—"))
        assertFalse(withoutFileText.contains("!"))
    }
}
