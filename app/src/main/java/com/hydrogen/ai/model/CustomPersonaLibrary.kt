package com.hydrogen.ai.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One saved custom persona on disk; the id is always chosen locally ("custom-…"). */
@Serializable
data class StoredPersona(val id: String, val persona: SharedPersona)

/** Personas the user imported from a share code, kept on this device next to the built-in ones. */
object CustomPersonaLibrary {
    const val MAX_PERSONAS = 20
    const val ID_PREFIX = "custom-"

    private val json = Json { ignoreUnknownKeys = true }

    fun isCustom(id: String): Boolean = id.startsWith(ID_PREFIX)

    fun encode(list: List<Persona>): String =
        json.encodeToString(list.take(MAX_PERSONAS).map { StoredPersona(it.id, PersonaShareCodec.fromPersona(it)) })

    /** Saved personas, skipping any entry that is not valid; null when the file is not readable JSON. */
    fun decode(text: String): List<Persona>? {
        val stored = runCatching { json.decodeFromString<List<StoredPersona>>(text) }.getOrNull() ?: return null
        val seen = HashSet<String>()
        return stored.mapNotNull { entry ->
            if (!isCustom(entry.id) || !seen.add(entry.id)) return@mapNotNull null
            PersonaShareCodec.validate(entry.persona).getOrNull()?.let { PersonaShareCodec.toPersona(it, entry.id) }
        }.take(MAX_PERSONAS)
    }

    /**
     * Adds an imported persona first. Re-importing the same name and instructions changes nothing; a name
     * already used (also by a built-in persona) gets " (2)", " (3)"…; the oldest drop off past [MAX_PERSONAS].
     */
    fun add(list: List<Persona>, shared: SharedPersona, id: String): List<Persona> {
        require(isCustom(id)) { "Custom persona ids start with $ID_PREFIX" }
        val clean = PersonaShareCodec.validate(shared).getOrElse { throw IllegalArgumentException(it.message, it) }
        if (list.any { it.name.equals(clean.name, ignoreCase = true) && it.instructions == clean.instructions }) {
            return list
        }
        val taken = (Personas.all + list).map { it.name.lowercase() }.toSet()
        var name = clean.name
        var n = 2
        while (name.lowercase() in taken) {
            val suffix = " ($n)"
            name = clean.name.take(PersonaShareCodec.MAX_NAME - suffix.length) + suffix
            n++
        }
        return (listOf(PersonaShareCodec.toPersona(clean.copy(name = name), id)) + list).take(MAX_PERSONAS)
    }

    /**
     * Replaces the custom persona [id] in place (same id and position) with an edited version. Fails with a
     * short message when [id] is not in [list] or another persona, built-in or custom, already has the name.
     */
    fun update(list: List<Persona>, id: String, shared: SharedPersona): Result<List<Persona>> {
        if (list.none { it.id == id }) return Result.failure(IllegalArgumentException("This assistant no longer exists."))
        val clean = PersonaShareCodec.validate(shared).getOrElse { return Result.failure(it) }
        val clash = (Personas.all + list).any { it.id != id && it.name.equals(clean.name, ignoreCase = true) }
        if (clash) return Result.failure(IllegalArgumentException("You already have an assistant called ${clean.name}."))
        return Result.success(list.map { if (it.id == id) PersonaShareCodec.toPersona(clean, id) else it })
    }

    /** Adds a persona made in the editor first; unlike [add], a name already in use is refused, not renamed. */
    fun create(list: List<Persona>, shared: SharedPersona, id: String): Result<List<Persona>> {
        require(isCustom(id)) { "Custom persona ids start with $ID_PREFIX" }
        val clean = PersonaShareCodec.validate(shared).getOrElse { return Result.failure(it) }
        if ((Personas.all + list).any { it.name.equals(clean.name, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("You already have an assistant called ${clean.name}."))
        }
        if (list.size >= MAX_PERSONAS) {
            return Result.failure(IllegalArgumentException("You can keep up to $MAX_PERSONAS assistants. Remove one first."))
        }
        return Result.success(listOf(PersonaShareCodec.toPersona(clean, id)) + list)
    }

    fun remove(list: List<Persona>, id: String): List<Persona> = list.filterNot { it.id == id }
}
