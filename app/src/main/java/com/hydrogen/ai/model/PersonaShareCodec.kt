package com.hydrogen.ai.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64

/** A persona as it travels in a share code: no id, so an imported copy can never replace a built-in one. */
@Serializable
data class SharedPersona(
    val name: String,
    val tagline: String = "",
    val greeting: String = "",
    val instructions: String,
    val starters: List<SharedStarter> = emptyList()
)

@Serializable
data class SharedStarter(val title: String, val hint: String = "", val prompt: String, val icon: String = "idea")

/**
 * Turns a persona into one line of plain text ("hydrogen-persona:v1:" + URL-safe Base64 JSON) that can be
 * pasted into any chat app, and back. Decoding never throws: anything odd yields a short message, and
 * every field is cleaned and length-checked, so a pasted code is only ever data (never code or a URL).
 */
object PersonaShareCodec {
    const val PREFIX = "orbit-persona:v1:"

    /** Codes shared before the app was renamed to Orbit; still accepted. */
    const val LEGACY_PREFIX = "hydrogen-persona:v1:"
    const val MAX_NAME = 40
    const val MAX_TAGLINE = 60
    const val MAX_INSTRUCTIONS = 4000
    const val MAX_GREETING = 200
    const val MAX_STARTERS = 4
    const val MAX_STARTER_TITLE = 16
    const val MAX_STARTER_HINT = 40
    const val MAX_STARTER_PROMPT = 300
    const val MAX_TOKEN = 16_000
    const val DEFAULT_ICON = "idea"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun fromPersona(persona: Persona): SharedPersona = SharedPersona(
        name = persona.name,
        tagline = persona.tagline,
        greeting = persona.greeting,
        instructions = persona.instructions,
        starters = persona.starters.map { SharedStarter(it.title, it.hint, it.prompt, it.icon) }
    )

    /** A local persona for an imported code; [id] must be chosen by the caller (never taken from the code). */
    fun toPersona(shared: SharedPersona, id: String): Persona = Persona(
        id = id,
        name = shared.name,
        tagline = shared.tagline,
        greeting = shared.greeting,
        instructions = shared.instructions,
        starters = shared.starters.map { PersonaStarter(it.title, it.hint, it.prompt, it.icon) }
    )

    /** Share code for [persona]; throws [IllegalArgumentException] when it breaks a limit. */
    fun encode(persona: SharedPersona): String {
        val clean = validate(persona).getOrElse { throw IllegalArgumentException(it.message, it) }
        val bytes = json.encodeToString(clean).toByteArray(Charsets.UTF_8)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** Finds a share code anywhere in [text] (for example a whole pasted message) and reads it. */
    fun decode(text: String): Result<SharedPersona> {
        val prefix = if (text.contains(PREFIX)) PREFIX else LEGACY_PREFIX
        val start = text.indexOf(prefix)
        if (start < 0) {
            return failure(
                if (text.contains("orbit-persona:") || text.contains("hydrogen-persona:")) {
                    "This persona code is from a newer Orbit version."
                } else {
                    "No Orbit persona code found."
                }
            )
        }
        val token = text.substring(start + prefix.length).takeWhile { !it.isWhitespace() }
        if (token.isEmpty()) return failure("The persona code is empty.")
        if (token.length > MAX_TOKEN) return failure("The persona code is too long.")
        val decoded = try {
            String(Base64.getUrlDecoder().decode(token.trimEnd('=')), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            return failure("The persona code is damaged.")
        }
        val shared = try {
            json.decodeFromString<SharedPersona>(decoded)
        } catch (e: Exception) {
            return failure("The persona code is damaged.")
        }
        return validate(shared)
    }

    /** Cleans every field (control characters, icon keys) and checks the limits. */
    fun validate(persona: SharedPersona): Result<SharedPersona> {
        val name = clean(persona.name).trim()
        val tagline = clean(persona.tagline).trim()
        val greeting = clean(persona.greeting).trim()
        val instructions = clean(persona.instructions).trim()
        if (name.isEmpty()) return failure("The persona needs a name.")
        if (name.length > MAX_NAME) return failure("The name is longer than $MAX_NAME characters.")
        if (tagline.length > MAX_TAGLINE) return failure("The tagline is longer than $MAX_TAGLINE characters.")
        if (instructions.isEmpty()) return failure("The persona needs instructions.")
        if (instructions.length > MAX_INSTRUCTIONS) {
            return failure("The instructions are longer than $MAX_INSTRUCTIONS characters.")
        }
        if (greeting.length > MAX_GREETING) return failure("The greeting is longer than $MAX_GREETING characters.")
        if (persona.starters.size > MAX_STARTERS) return failure("A persona can have at most $MAX_STARTERS starters.")
        val starters = persona.starters.map { starter ->
            val title = clean(starter.title).trim()
            val hint = clean(starter.hint).trim()
            val prompt = clean(starter.prompt)
            if (title.isEmpty()) return failure("Every starter needs a title.")
            if (title.length > MAX_STARTER_TITLE) {
                return failure("Starter titles must be $MAX_STARTER_TITLE characters or fewer.")
            }
            if (hint.length > MAX_STARTER_HINT) return failure("Starter hints must be $MAX_STARTER_HINT characters or fewer.")
            if (prompt.isBlank()) return failure("Every starter needs a prompt.")
            if (prompt.length > MAX_STARTER_PROMPT) {
                return failure("Starter prompts must be $MAX_STARTER_PROMPT characters or fewer.")
            }
            val icon = starter.icon.trim().takeIf { it in Personas.STARTER_ICONS } ?: DEFAULT_ICON
            SharedStarter(title, hint, prompt, icon)
        }
        if (starters.map { it.title.lowercase() }.toSet().size != starters.size) {
            return failure("Two starters have the same title.")
        }
        return Result.success(SharedPersona(name, tagline, greeting, instructions, starters))
    }

    /** Drops ASCII control characters except newline and tab (also removes stray carriage returns). */
    private fun clean(text: String): String =
        text.filter { it == '\n' || it == '\t' || !(it < ' ' || it == '\u007F') }

    private fun failure(message: String): Result<SharedPersona> = Result.failure(IllegalArgumentException(message))
}
