package com.hydrogen.ai.data

import com.hydrogen.ai.model.CustomPersonaLibrary
import com.hydrogen.ai.model.Persona
import java.io.File

/** Device-local JSON file for imported personas. Call from a background dispatcher. */
class CustomPersonaStore(private val file: File) {

    fun load(): List<Persona> {
        if (!file.exists()) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return CustomPersonaLibrary.decode(text) ?: emptyList()
    }

    fun save(personas: List<Persona>) {
        SafeFileReplace.writeText(file, CustomPersonaLibrary.encode(personas))
    }
}
