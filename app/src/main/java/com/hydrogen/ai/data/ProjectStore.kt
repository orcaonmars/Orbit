package com.hydrogen.ai.data

import com.hydrogen.ai.model.Project
import com.hydrogen.ai.model.Projects
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where projects (their instructions and file text) are kept. Device-local only. */
interface ProjectStore {
    suspend fun load(): List<Project>
    suspend fun save(projects: List<Project>)
}

class InMemoryProjectStore(private var projects: List<Project> = emptyList()) : ProjectStore {
    override suspend fun load(): List<Project> = projects
    override suspend fun save(projects: List<Project>) {
        this.projects = projects
    }
}

/** JSON in [dir]/orbit_projects.json, written through a temp file; no projects removes the file and any half-written copy. */
class FileProjectStore(private val dir: File) : ProjectStore {
    private val file get() = File(dir, "orbit_projects.json")
    private val tmp get() = File(dir, "orbit_projects.json.tmp")

    override suspend fun load(): List<Project> = withContext(Dispatchers.IO) {
        runCatching { if (file.exists()) Projects.decode(file.readText()) else emptyList() }
            .getOrDefault(emptyList())
    }

    override suspend fun save(projects: List<Project>) = withContext(Dispatchers.IO) {
        if (projects.isEmpty()) {
            file.delete()
            tmp.delete()
            return@withContext
        }
        dir.mkdirs()
        tmp.writeText(Projects.encode(projects))
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
        Unit
    }
}
