package com.hydrogen.ai.data

import com.hydrogen.ai.model.ErrorLog
import com.hydrogen.ai.model.ErrorLogEntry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Settings → Error log. Device-local only; never sent anywhere by Orbit. */
interface ErrorLogStore {
    suspend fun load(): List<ErrorLogEntry>
    suspend fun save(entries: List<ErrorLogEntry>)
}

class InMemoryErrorLogStore(private var entries: List<ErrorLogEntry> = emptyList()) : ErrorLogStore {
    override suspend fun load(): List<ErrorLogEntry> = entries
    override suspend fun save(entries: List<ErrorLogEntry>) {
        this.entries = entries.take(ErrorLog.MAX_ENTRIES)
    }
}

/** JSON in [dir]/orbit_error_log.json; an empty log removes the file and any half-written copy. */
class FileErrorLogStore(private val dir: File) : ErrorLogStore {
    private val file get() = File(dir, "orbit_error_log.json")
    private val tmp get() = File(dir, "orbit_error_log.json.tmp")

    override suspend fun load(): List<ErrorLogEntry> = withContext(Dispatchers.IO) {
        runCatching { if (file.exists()) ErrorLog.decode(file.readText()) else emptyList() }.getOrDefault(emptyList())
    }

    override suspend fun save(entries: List<ErrorLogEntry>) = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) {
            file.delete()
            tmp.delete()
            return@withContext
        }
        dir.mkdirs()
        tmp.writeText(ErrorLog.encode(entries))
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
