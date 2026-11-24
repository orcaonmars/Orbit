package com.hydrogen.ai.data

import com.hydrogen.ai.model.PromptLibrary
import com.hydrogen.ai.model.SavedPrompt
import java.io.File
import java.io.IOException

/** Device-local JSON file for saved prompts. Call from a background dispatcher. */
class PromptLibraryStore(private val file: File) {
    private companion object { val writeLock = Any() }

    fun load(): List<SavedPrompt> = runCatching { loadForEditing() }.getOrDefault(PromptLibrary.starterPrompts)

    /** An unreadable existing library must not silently become editable starter data. */
    fun loadForEditing(): List<SavedPrompt> {
        if (!file.exists()) return PromptLibrary.starterPrompts
        return PromptLibrary.decode(file.readText()) ?: throw IOException("Invalid saved prompt library")
    }

    fun save(prompts: List<SavedPrompt>) = synchronized(writeLock) {
        SafeFileReplace.writeText(file, PromptLibrary.encode(prompts))
    }

    /** Reject a stale snapshot rather than overwriting a newer library from another screen instance. */
    fun saveIfUnchanged(expected: List<SavedPrompt>, updated: List<SavedPrompt>): Boolean = synchronized(writeLock) {
        if (loadForEditing() != expected) return@synchronized false
        SafeFileReplace.writeText(file, PromptLibrary.encode(updated))
        true
    }
}
