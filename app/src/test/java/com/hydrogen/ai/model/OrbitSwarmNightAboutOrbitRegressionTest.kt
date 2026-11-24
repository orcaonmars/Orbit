package com.hydrogen.ai.model

import org.junit.Test
import org.junit.Assert.*

class OrbitSwarmNightAboutOrbitRegressionTest {

    @Test
    fun versionLine_nonBlankVersion_returnsFormattedString() {
        assertEquals("Version 1.2.3", AboutOrbit.versionLine("1.2.3"))
        assertEquals("Version 0.9.0-beta", AboutOrbit.versionLine("0.9.0-beta"))
    }

    @Test
    fun versionLine_blankVersion_returnsUnknown() {
        assertEquals("Version unknown", AboutOrbit.versionLine(""))
        assertEquals("Version unknown", AboutOrbit.versionLine("   "))
        assertEquals("Version unknown", AboutOrbit.versionLine("\t\n"))
    }
}