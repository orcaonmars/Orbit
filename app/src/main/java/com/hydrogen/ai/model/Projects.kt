package com.hydrogen.ai.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A file kept in a project. Only its words are stored (text, code, a PDF's text or a Word file's text). */
@Serializable
data class ProjectFile(
    val id: String,
    val name: String,
    val text: String,
    val addedAtEpochMs: Long = 0L,
    /** The file was longer than Orbit keeps, so only its beginning is here. */
    val truncated: Boolean = false
)

/**
 * A project groups chats that share instructions and reference files, like a folder with a brief. Everything stays
 * on this device; the instructions and files go to the model only inside the system message of the project's chats.
 */
@Serializable
data class Project(
    val id: String,
    val name: String,
    val instructions: String = "",
    val files: List<ProjectFile> = emptyList(),
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
)

@Serializable
data class ProjectsFile(
    val version: Int = Projects.VERSION,
    val projects: List<Project> = emptyList()
)

/** The saved text selected for a project's next system message. */
data class ProjectFileContext(val file: ProjectFile, val includedText: String) {
    val storedChars: Int get() = file.text.trimEnd().length
    val sentChars: Int get() = includedText.length
    val omitted: Boolean get() = sentChars == 0
    val partial: Boolean get() = sentChars in 1 until storedChars
}

object Projects {
    const val VERSION = 1
    const val MAX_NAME = 60
    const val MAX_INSTRUCTIONS = 4_000
    const val MAX_PROJECTS = 100
    const val MAX_FILES = 20
    /** One file keeps at most this much text, the same cap as a file attached to a message. */
    const val MAX_FILE_CHARS = FileAttachPolicy.MAX_TEXT_CHARS
    /**
     * All of a project's files together, as sent with each message. About 10,000 tokens: enough for real notes and
     * small enough for a model running on the PC. The rest of a longer file is left out, with a note saying so.
     */
    const val FILES_BUDGET_CHARS = 40_000

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** One line, trimmed and capped; null when blank. */
    fun normalizeName(raw: String): String? =
        raw.replace(Regex("\\s+"), " ").trim().take(MAX_NAME).trim().ifBlank { null }

    fun cleanInstructions(raw: String): String = raw.replace("\r\n", "\n").trim().take(MAX_INSTRUCTIONS).trim()

    private fun cleanFileName(raw: String): String =
        raw.replace(Regex("[\\r\\n\\t\"<>]+"), " ").replace(Regex(" +"), " ").trim().take(120).ifBlank { "file" }

    fun clean(project: Project): Project = project.copy(
        name = normalizeName(project.name) ?: "Untitled project",
        instructions = cleanInstructions(project.instructions),
        files = project.files
            .filter { it.id.isNotBlank() && it.text.isNotBlank() }
            .distinctBy { it.id }
            .take(MAX_FILES)
            .map { f ->
                val text = f.text.replace("\r\n", "\n")
                f.copy(
                    name = cleanFileName(f.name),
                    text = text.take(MAX_FILE_CHARS),
                    truncated = f.truncated || text.length > MAX_FILE_CHARS
                )
            }
    )

    /** Characters of file text a project holds, for the "how full" meter. */
    fun filesChars(project: Project): Int = project.files.sumOf { it.text.length }

    /** Shared by the local preview and actual request construction. Does not mutate saved text. */
    fun fileContext(project: Project): List<ProjectFileContext> {
        var left = FILES_BUDGET_CHARS
        return clean(project).files.map { file ->
            val shown = file.text.trimEnd().take(left).trimEnd()
            left -= shown.length
            ProjectFileContext(file, shown)
        }
    }

    /** A new file for [project], or null when it already has [MAX_FILES] files or the text is blank. */
    fun withFile(project: Project, file: ProjectFile, nowEpochMs: Long): Project? {
        if (project.files.size >= MAX_FILES || file.text.isBlank()) return null
        val cleaned = clean(project.copy(files = listOf(file))).files.firstOrNull() ?: return null
        return project.copy(files = project.files + cleaned, updatedAtEpochMs = nowEpochMs)
    }

    /**
     * The project's part of the system message: its name, instructions and files, in the order the files were
     * added. Files share [FILES_BUDGET_CHARS]; the one that runs over is cut with a note, and any after it are
     * named but left out, so the model never thinks it has seen a file it hasn't.
     */
    fun systemBlock(project: Project?): String? {
        if (project == null) return null
        val p = clean(project)
        val parts = mutableListOf("This chat is part of the user's project \"${p.name}\".")
        if (p.instructions.isNotEmpty()) {
            parts += "Project instructions:\n${p.instructions}"
        }
        if (p.files.isNotEmpty()) {
            val included = mutableListOf<String>()
            val skipped = mutableListOf<String>()
            for (entry in fileContext(p)) {
                val file = entry.file
                if (entry.omitted) {
                    skipped += file.name
                    continue
                }
                val shown = entry.includedText
                val note = when {
                    entry.partial -> "\n(The rest of this file did not fit and was left out.)"
                    file.truncated -> "\n(Only the beginning of this file was saved.)"
                    else -> ""
                }
                included += "<file name=\"${file.name}\">\n$shown$note\n</file>"
            }
            val header = "Project files the user added for reference. Use them when they help; say which file you used."
            parts += (listOf(header) + included).joinToString("\n")
            if (skipped.isNotEmpty()) {
                parts += "These project files were too long to include this time: ${skipped.joinToString(", ")}."
            }
        }
        return parts.joinToString("\n\n")
    }

    /** The project [conversation] belongs to, or null when it has none or the project was deleted. */
    fun projectOf(conversation: Conversation?, projects: List<Project>): Project? {
        val id = conversation?.projectId ?: return null
        return projects.firstOrNull { it.id == id }
    }

    fun encode(projects: List<Project>): String =
        json.encodeToString(ProjectsFile.serializer(), ProjectsFile(projects = projects.map(::clean)))

    /** A damaged or unreadable file gives no projects rather than an error. */
    fun decode(text: String): List<Project> = runCatching {
        json.decodeFromString(ProjectsFile.serializer(), text).projects
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .take(MAX_PROJECTS)
            .map(::clean)
    }.getOrDefault(emptyList())
}
