package com.hydrogen.ai.ui

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Existing screen colour debt must not grow during Orbit Studio migration.
 * The theme package owns colour constants, so it is intentionally excluded.
 */
class ThemeTokenTest {
    private val inlineColour = Regex("""Color\s*\(\s*0x[0-9A-Fa-f]{8}\s*\)""")

    @Test
    fun noAdditionalHardcodedScreenColors() {
        val uiRoot = Paths.get("src/main/java/com/hydrogen/ai/ui")
        val counts = sortedMapOf<String, Int>()
        Files.walk(uiRoot).use { paths ->
            paths.filter { path: Path ->
                Files.isRegularFile(path) &&
                    path.toString().endsWith(".kt") &&
                    !uiRoot.relativize(path).startsWith("theme")
            }.forEach { path ->
                val matches = inlineColour.findAll(String(Files.readAllBytes(path), Charsets.UTF_8)).count()
                if (matches > 0) counts[uiRoot.relativize(path).toString().replace('\\', '/')] = matches
            }
        }
        // Existing debt recorded from an inspected source scan on Nov 22.
        // Reduce these numbers only when the corresponding screen is migrated.
        assertEquals(
            "No additional inline UI colors; use OrbitTheme colors instead",
            sortedMapOf(
                "ChatScreen.kt" to 1,
                "MarkdownMessage.kt" to 8,
                "RelaySettingsSheet.kt" to 1
            ),
            counts
        )
    }
}
