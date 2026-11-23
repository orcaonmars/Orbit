package com.hydrogen.ai.model

import com.hydrogen.ai.BuildConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutOrbitTest {

    private val licenseFile = File("src/main/res/raw/apache_license_2_0.txt")

    @Test fun copyFollowsTheHouseRules() {
        val all = listOf(
            AboutOrbit.NAME, AboutOrbit.TAGLINE, AboutOrbit.EARLY_NOTE, AboutOrbit.WHATS_NEW, AboutOrbit.PRIVACY,
            AboutOrbit.LICENSES, AboutOrbit.ORBIT_LICENSE_LINE, AboutOrbit.LICENSES_INTRO, AboutOrbit.READ_LICENSE,
            AboutOrbit.BACK, AboutOrbit.versionLine(BuildConfig.VERSION_NAME)
        ) + AboutOrbit.PROMISES + AboutOrbit.LIBRARIES.flatMap { listOf(it.name, it.by, it.license) }
        all.forEach { line ->
            assertFalse(line, line.contains('!'))
            assertFalse(line, line.contains('—'))
            assertFalse(line, line.contains('–'))
            assertFalse(line, line.contains("ChatGPT", ignoreCase = true))
            assertFalse(line, line.contains("Claude", ignoreCase = true))
            assertFalse(line, line.contains("telemetry", ignoreCase = true))
            assertEquals(line, line.trim(), line)
            assertTrue(line, line.isNotEmpty())
        }
        (AboutOrbit.PROMISES + AboutOrbit.TAGLINE + AboutOrbit.EARLY_NOTE).forEach { assertTrue(it, it.endsWith(".")) }
    }

    @Test fun theVersionLineShowsTheBuiltVersion() {
        assertEquals("Version ${BuildConfig.VERSION_NAME}", AboutOrbit.versionLine(BuildConfig.VERSION_NAME))
    }

    @Test fun everyLibraryShippedInTheAppIsCredited() {
        // Release dependencies only: debugImplementation (layout preview tools) and test libraries never ship.
        val gradle = File("build.gradle.kts").readText()
        val dependencies = gradle.substringAfter("\ndependencies {")
        val shipped = dependencies.lines()
            .map { it.trim() }
            .filterNot { it.startsWith("debugImplementation") || it.startsWith("test") || it.startsWith("androidTest") }
            .flatMap { line -> Regex("""libs\.([A-Za-z0-9.]+)""").findAll(line).map { it.groupValues[1] }.toList() }
            .toSet()
        assertTrue("found no dependencies in build.gradle.kts", shipped.size >= 5)
        val credited = AboutOrbit.LIBRARIES.flatMap { it.modules }.toSet()
        assertEquals("Add a credit in AboutOrbit.LIBRARIES", emptySet<String>(), shipped - credited)
        assertEquals("Remove credits for libraries Orbit no longer ships", emptySet<String>(), credited - shipped)
    }

    @Test fun theBundledLicenseIsTheFullApacheText() {
        val text = licenseFile.readText()
        assertTrue(text.contains("Apache License"))
        assertTrue(text.contains("Version 2.0, January 2004"))
        assertTrue(text.contains("END OF TERMS AND CONDITIONS"))
        assertTrue(AboutOrbit.LIBRARIES.all { it.license == AboutOrbit.APACHE_2 })
    }

    @Test fun reflowJoinsHardLineBreaksButKeepsEveryWord() {
        assertEquals(
            listOf("1. Definitions.", "\"License\" shall mean the terms and conditions."),
            AboutOrbit.reflow("   1. Definitions.\r\n\r\n      \"License\" shall mean\n      the terms and conditions.\n\n\n")
        )
        val original = licenseFile.readText()
        val paragraphs = AboutOrbit.reflow(original)
        assertTrue(paragraphs.size > 20)
        assertEquals(original.split(Regex("\\s+")).filter { it.isNotEmpty() }, paragraphs.flatMap { it.split(' ') })
        paragraphs.forEach { assertFalse(it, it.contains('\n')) }
    }
}
