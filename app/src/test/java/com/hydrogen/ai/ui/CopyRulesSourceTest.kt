package com.hydrogen.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Orbit's copy rules (docs/ORBIT_DESIGN.md): visible text joins details with plain commas, never with a middle dot,
 * and never ends a link or button with an arrow. Comments are skipped; only string literals are checked.
 */
class CopyRulesSourceTest {
    private val roots = listOf(
        "src/main/java/com/hydrogen/ai/ui", "src/main/java/com/hydrogen/ai/model", "src/main/java/com/hydrogen/ai/network"
    )
    private val literal = Regex("\"([^\"\\\\\\n]|\\\\.)*\"")

    private fun literals(): Sequence<Pair<String, String>> = roots.asSequence()
        .flatMap { File(it).walkTopDown().filter { f -> f.isFile && f.extension == "kt" } }
        .flatMap { file ->
            file.readLines(Charsets.UTF_8).asSequence()
                .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
                .flatMap { line -> literal.findAll(line).map { file.name to it.value } }
        }

    @Test
    fun visibleStringsUseNoMiddleDots() {
        val offenders = literals().filter { (_, s) -> s.contains('·') }.toList()
        assertTrue("Middle dots in visible strings: $offenders", offenders.isEmpty())
    }

    @Test
    fun visibleStringsUseNoArrowLinks() {
        val offenders = literals().filter { (_, s) -> s.contains('→') }.toList()
        assertTrue("Arrow links in visible strings: $offenders", offenders.isEmpty())
    }

    /** Error text must name screens and buttons as they are labeled now, or the user cannot follow it. */
    @Test
    fun visibleStringsUseCurrentScreenNames() {
        val stale = listOf("Models & API keys", "Load models", "Privacy & data")
        val offenders = literals().filter { (_, s) -> stale.any { s.contains(it) } }.toList()
        assertTrue("Old screen or button names in visible strings: $offenders", offenders.isEmpty())
    }
}
