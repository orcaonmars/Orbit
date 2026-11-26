package com.hydrogen.ai.model

/**
 * Words for the About Orbit sheet and its Open source licenses page. Pure Kotlin so AboutOrbitTest can hold the copy
 * to the house rules (plain words, no exclamation marks, no em dashes) and check that every library the app ships is
 * credited here.
 */
object AboutOrbit {
    const val NAME = "Orbit"
    const val TAGLINE = "Open-source AI chat for your phone and your PC."

    fun versionLine(versionName: String): String =
        if (versionName.isBlank()) "Version unknown" else "Version $versionName"

    /** What Orbit promises. Each line must stay true: PrivacySummary has the detail behind it. */
    val PROMISES: List<String> = listOf(
        "Your chats stay on this phone.",
        "No account, no ads and no tracking.",
        "Messages go only to the model you pick, and to your PC once you pair it."
    )

    const val EARLY_NOTE = "This is an early version. Back up your chats now and then."

    const val WHATS_NEW = "What's new"
    const val PRIVACY = "Privacy and data"
    const val LICENSES = "Open source licenses"

    const val APACHE_2 = "Apache License 2.0"
    const val ORBIT_LICENSE_LINE = "Orbit itself is free software under the $APACHE_2."

    const val LICENSES_INTRO =
        "Orbit is built on these open source libraries. Thank you to the people who make them."
    const val READ_LICENSE = "Read the $APACHE_2"
    const val BACK = "Back"

    /**
     * One credited library. [modules] are the version-catalog aliases from app/build.gradle.kts that it covers, so a
     * new dependency without a credit fails AboutOrbitTest.
     */
    data class Library(val name: String, val by: String, val modules: List<String>, val license: String = APACHE_2)

    val LIBRARIES: List<Library> = listOf(
        Library(
            name = "AndroidX Core, Activity and Lifecycle",
            by = "The Android Open Source Project",
            modules = listOf(
                "androidx.core.ktx",
                "androidx.activity.compose",
                "androidx.lifecycle.runtime.ktx",
                "androidx.lifecycle.runtime.compose",
                "androidx.lifecycle.viewmodel.compose"
            )
        ),
        Library(
            name = "Jetpack Compose and Material 3",
            by = "The Android Open Source Project",
            modules = listOf(
                "androidx.compose.bom",
                "androidx.compose.ui",
                "androidx.compose.ui.tooling.preview",
                "androidx.compose.material3"
            )
        ),
        Library(
            name = "Material icons for Compose",
            by = "The Android Open Source Project and Google",
            modules = listOf("androidx.compose.material.icons.extended")
        ),
        Library(name = "Kotlin standard library", by = "JetBrains and Kotlin contributors", modules = emptyList()),
        Library(name = "kotlinx.coroutines", by = "JetBrains and Kotlin contributors", modules = emptyList()),
        Library(
            name = "kotlinx.serialization",
            by = "JetBrains and Kotlin contributors",
            modules = listOf("kotlinx.serialization.json")
        )
    )

    /**
     * A plain-text license file as paragraphs for a phone screen: blank lines split paragraphs, the hard line breaks
     * and column indents inside one are joined with single spaces. No words are changed.
     */
    fun reflow(text: String): List<String> =
        text.replace("\r\n", "\n")
            .split(Regex("\n[ \t]*\n"))
            .map { paragraph -> paragraph.lines().joinToString(" ") { it.trim() }.replace(Regex(" {2,}"), " ").trim() }
            .filter { it.isNotEmpty() }
}
