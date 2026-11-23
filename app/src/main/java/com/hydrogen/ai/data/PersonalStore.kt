package com.hydrogen.ai.data

import com.hydrogen.ai.model.Personal
import com.hydrogen.ai.model.PersonalProfile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where Settings → Personal is kept. Device-local only. */
interface PersonalStore {
    suspend fun load(): PersonalProfile
    suspend fun save(profile: PersonalProfile)
}

class InMemoryPersonalStore(private var profile: PersonalProfile = PersonalProfile()) : PersonalStore {
    override suspend fun load(): PersonalProfile = profile
    override suspend fun save(profile: PersonalProfile) {
        this.profile = profile
    }
}

/** JSON in [dir]/orbit_personal.json; an empty profile removes the file, and any half-written copy. */
class FilePersonalStore(private val dir: File) : PersonalStore {
    private val file get() = File(dir, "orbit_personal.json")
    private val tmp get() = File(dir, "orbit_personal.json.tmp")

    override suspend fun load(): PersonalProfile = withContext(Dispatchers.IO) {
        runCatching { if (file.exists()) Personal.decode(file.readText()) else PersonalProfile() }
            .getOrDefault(PersonalProfile())
    }

    override suspend fun save(profile: PersonalProfile) = withContext(Dispatchers.IO) {
        val cleaned = Personal.clean(profile)
        if (cleaned.isEmpty) {
            // Also the temp copy a save killed mid-way could leave, so "delete all" leaves nothing of you behind.
            file.delete()
            tmp.delete()
            return@withContext
        }
        dir.mkdirs()
        tmp.writeText(Personal.encode(cleaned))
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
