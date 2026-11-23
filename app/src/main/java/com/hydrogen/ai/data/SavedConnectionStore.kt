package com.hydrogen.ai.data

import com.hydrogen.ai.model.SavedConnection
import com.hydrogen.ai.model.SavedConnections
import java.io.File

/** Device-local JSON file of recently used servers and models (no keys). Call from a background dispatcher. */
class SavedConnectionStore(private val file: File) {

    fun load(): List<SavedConnection> {
        if (!file.exists()) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return SavedConnections.decode(text)
    }

    fun save(connections: List<SavedConnection>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(SavedConnections.encode(connections))
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }
}
