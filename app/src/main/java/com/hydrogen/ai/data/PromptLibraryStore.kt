package com.hydrogen.ai.data

import com.hydrogen.ai.model.PromptLibrary
import com.hydrogen.ai.model.SavedPrompt
import java.io.File

/** Device-local JSON file for saved prompts. Call from a background dispatcher. */
class PromptLibraryStore(private val file: File) {

    fun load(): List<SavedPrompt> {
        if (!file.exists()) return PromptLibrary.starterPrompts
        val text = runCatching { file.readText() }.getOrNull() ?: return PromptLibrary.starterPrompts
        return PromptLibrary.decode(text) ?: PromptLibrary.starterPrompts
    }

    fun save(prompts: List<SavedPrompt>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(PromptLibrary.encode(prompts))
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }
}
