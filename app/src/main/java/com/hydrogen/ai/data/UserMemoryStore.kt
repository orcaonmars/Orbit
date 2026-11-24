package com.hydrogen.ai.data

import com.hydrogen.ai.model.MemoryItem
import com.hydrogen.ai.model.UserMemory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UserMemoryState(
    val items: List<MemoryItem> = emptyList(),
    val enabled: Boolean = true
)

/** Where "Memory you control" is kept. Device-local only. */
interface UserMemoryStore {
    suspend fun load(): UserMemoryState
    suspend fun save(state: UserMemoryState)
}

class InMemoryUserMemoryStore(private var state: UserMemoryState = UserMemoryState()) : UserMemoryStore {
    override suspend fun load(): UserMemoryState = state
    override suspend fun save(state: UserMemoryState) {
        this.state = state
    }
}

/** JSON list in [dir]/orbit_memory.json; an "orbit_memory_off" marker file means memory is switched off. */
class FileUserMemoryStore(private val dir: File) : UserMemoryStore {
    private val itemsFile get() = File(dir, "orbit_memory.json")
    private val offMarker get() = File(dir, "orbit_memory_off")

    override suspend fun load(): UserMemoryState = withContext(Dispatchers.IO) {
        val items = runCatching { if (itemsFile.exists()) UserMemory.decode(itemsFile.readText()) else emptyList() }
            .getOrDefault(emptyList())
        UserMemoryState(items = items, enabled = !offMarker.exists())
    }

    override suspend fun save(state: UserMemoryState) = withContext(Dispatchers.IO) {
        // Persist opt-out before touching the items. A failed write must not re-enable memory.
        if (!state.enabled) SafeFileReplace.writeText(offMarker, "off")
        if (state.items.isEmpty()) SafeFileReplace.deleteSnapshot(itemsFile)
        else SafeFileReplace.writeText(itemsFile, UserMemory.encode(state.items))
        // Only enable memory after the requested snapshot was saved successfully.
        if (state.enabled) SafeFileReplace.deleteSnapshot(offMarker)

        Unit
    }
}
