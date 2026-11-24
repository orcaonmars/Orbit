package com.hydrogen.ai.model

/** Search words may match across a destination label, description and section. */
object SettingsSearch {
    fun matches(query: String, vararg labels: String): Boolean {
        val text = labels.joinToString(" ")
        return query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            .all { text.contains(it, ignoreCase = true) }
    }
}
