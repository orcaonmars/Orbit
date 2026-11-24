package com.hydrogen.ai.data

import com.hydrogen.ai.model.SavedConnection
import com.hydrogen.ai.model.SavedConnections
import java.io.File

/** Device-local JSON file of recently used servers and models (no keys). Call from a background dispatcher. */
class SavedConnectionStore(private val file: File) {

    @Synchronized
    fun load(): List<SavedConnection> {
        if (!file.exists()) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return SavedConnections.decode(text)
    }

    @Synchronized
    fun update(transform: (List<SavedConnection>) -> List<SavedConnection>): List<SavedConnection> {
        val next = transform(load())
        save(next)
        return next
    }

    @Synchronized
    fun save(connections: List<SavedConnection>) {
        SafeFileReplace.writeText(file, SavedConnections.encode(connections))
    }
}
