package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectFileContextTest {
    private fun file(id: String, text: String, truncated: Boolean = false) =
        ProjectFile(id, "$id.txt", text, truncated = truncated)

    @Test fun fullPartialAndOmittedFilesPreserveOriginalOrder() {
        val p = Project("p", "Project", files = listOf(
            file("a", "a".repeat(Projects.FILES_BUDGET_CHARS - 10)),
            file("b", "b".repeat(50)),
            file("c", "not sent")
        ))
        val entries = Projects.fileContext(p)
        assertEquals(listOf("a", "b", "c"), entries.map { it.file.id })
        assertFalse(entries[0].partial)
        assertTrue(entries[1].partial)
        assertEquals(10, entries[1].sentChars)
        assertTrue(entries[2].omitted)
        assertEquals(Projects.FILES_BUDGET_CHARS, entries.sumOf { it.sentChars })
        assertEquals(50, p.files[1].text.length)
        val block = Projects.systemBlock(p)!!
        assertTrue(block.contains("b".repeat(10) + "\n(The rest of this file did not fit"))
        assertFalse(block.contains("not sent"))
    }

    @Test fun trailingWhitespaceDoesNotConsumeContextBudget() {
        val p = Project("p", "Project", files = listOf(
            file("a", "a".repeat(Projects.FILES_BUDGET_CHARS - 1) + " \n "),
            file("b", "b")
        ))
        val entries = Projects.fileContext(p)
        assertEquals(1, entries[1].sentChars)
        assertFalse(entries[0].partial)
        assertFalse(entries[1].omitted)
    }

    @Test fun whitespaceAtBoundaryDoesNotMakeEmptyFileLookIncluded() {
        val p = Project("p", "Project", files = listOf(
            file("a", "a".repeat(Projects.FILES_BUDGET_CHARS - 1)),
            file("b", "  content"),
            file("c", "c")
        ))
        val entries = Projects.fileContext(p)
        assertTrue(entries[1].omitted)
        assertEquals("c", entries[2].includedText)
        val block = Projects.systemBlock(p)!!
        assertFalse(block.contains("<file name=\"b.txt\">"))
        assertTrue(block.contains("b.txt."))
    }

    @Test fun importedTruncationIsDistinctFromContextTruncation() {
        val entry = Projects.fileContext(Project("p", "Project", files = listOf(file("a", "saved beginning", true)))).single()
        assertTrue(entry.file.truncated)
        assertFalse(entry.partial)
        assertEquals(entry.storedChars, entry.sentChars)
    }

    @Test fun previewUsesSameCleanedFilesAsRequest() {
        val p = Project("p", "Project", files = listOf(file("a", "hello"), file("a", "duplicate"), file("b", " ")))
        assertEquals(listOf("a"), Projects.fileContext(p).map { it.file.id })
        assertFalse(Projects.systemBlock(p)!!.contains("duplicate"))
    }
}
