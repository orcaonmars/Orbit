package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastPolicyTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun relativeLuminanceMatchesBlackWhiteAndChannelWeights() {
        assertEquals(0.0, ContrastPolicy.relativeLuminance(black), 1e-9)
        assertEquals(1.0, ContrastPolicy.relativeLuminance(white), 1e-9)
        assertEquals(0.2126, ContrastPolicy.relativeLuminance(0xFFFF0000.toInt()), 1e-6)
        assertEquals(0.7152, ContrastPolicy.relativeLuminance(0xFF00FF00.toInt()), 1e-6)
        assertEquals(0.0722, ContrastPolicy.relativeLuminance(0xFF0000FF.toInt()), 1e-6)

        // Low-intensity linear branch (10 / 255 = 0.039215686 <= 0.04045)
        val expectedDarkGray = (10.0 / 255.0) / 12.92
        assertEquals(expectedDarkGray, ContrastPolicy.relativeLuminance(0xFF0A0A0A.toInt()), 1e-9)
    }

    @Test
    fun blackAndWhiteContrastIs21AndSameColorIs1() {
        assertEquals(21.0, ContrastPolicy.contrastRatio(black, white), 0.01)
        assertEquals(21.0, ContrastPolicy.contrastRatio(white, black), 0.01)

        assertEquals(1.0, ContrastPolicy.contrastRatio(black, black), 1e-9)
        assertEquals(1.0, ContrastPolicy.contrastRatio(white, white), 1e-9)
        val midTeal = 0xFF1F6F78.toInt()
        assertEquals(1.0, ContrastPolicy.contrastRatio(midTeal, midTeal), 1e-9)
    }

    @Test
    fun contrastRatioIsSymmetricForOpaqueColors() {
        val pairs = listOf(
            0xFF1A2B3C.toInt() to 0xFFF4F6F8.toInt(),
            0xFF767676.toInt() to white,
            0xFFB00020.toInt() to 0xFF121212.toInt(),
            0xFF0055FF.toInt() to 0xFFFFEE00.toInt()
        )
        for ((a, b) in pairs) {
            val ab = ContrastPolicy.contrastRatio(a, b)
            val ba = ContrastPolicy.contrastRatio(b, a)
            assertEquals(ab, ba, 1e-9)
            assertTrue(ab in 1.0..21.0)
        }
    }

    @Test
    fun wcagAaBoundary767676PassesAnd777777FailsOnWhite() {
        val passGray = ContrastPolicy.parseHex("#767676")!!
        val failGray = ContrastPolicy.parseHex("#777777")!!

        val passRatio = ContrastPolicy.contrastRatio(passGray, white)
        val failRatio = ContrastPolicy.contrastRatio(failGray, white)

        assertEquals(4.54, passRatio, 0.02)
        assertTrue(passRatio >= ContrastPolicy.AA_NORMAL)
        assertTrue(ContrastPolicy.meetsAa(passGray, white))

        assertTrue(failRatio < ContrastPolicy.AA_NORMAL)
        assertFalse(ContrastPolicy.meetsAa(failGray, white))
    }

    @Test
    fun aaLargeThresholdAllows3To4Point5AndRejectsBelow3() {
        assertEquals(4.5, ContrastPolicy.AA_NORMAL, 0.0)
        assertEquals(3.0, ContrastPolicy.AA_LARGE, 0.0)

        // #949494 on white is ~3.03:1 (passes AA_LARGE, fails AA_NORMAL)
        val largePass = ContrastPolicy.parseHex("#949494")!!
        val largePassRatio = ContrastPolicy.contrastRatio(largePass, white)
        assertTrue(largePassRatio in 3.0..4.5)
        assertFalse(ContrastPolicy.meetsAa(largePass, white, largeText = false))
        assertTrue(ContrastPolicy.meetsAa(largePass, white, largeText = true))

        // #969696 on white is ~2.95:1 (fails both)
        val largeFail = ContrastPolicy.parseHex("#969696")!!
        val largeFailRatio = ContrastPolicy.contrastRatio(largeFail, white)
        assertTrue(largeFailRatio < ContrastPolicy.AA_LARGE)
        assertFalse(ContrastPolicy.meetsAa(largeFail, white, largeText = false))
        assertFalse(ContrastPolicy.meetsAa(largeFail, white, largeText = true))
    }

    @Test
    fun semiTransparentForegroundBlendsOverOpaqueBackground() {
        // 50%-alpha black over white blends to mid-gray (~127/255), giving ~4.00:1
        val fiftyPercentBlack = ContrastPolicy.parseHex("#80000000")!!
        val ratio = ContrastPolicy.contrastRatio(fiftyPercentBlack, white)
        assertEquals(4.004, ratio, 0.02)
        assertFalse(ContrastPolicy.meetsAa(fiftyPercentBlack, white, largeText = false))
        assertTrue(ContrastPolicy.meetsAa(fiftyPercentBlack, white, largeText = true))

        // 0%-alpha foreground blends completely into background -> ratio 1.0
        val transparentBlack = 0x00000000
        assertEquals(1.0, ContrastPolicy.contrastRatio(transparentBlack, white), 1e-9)

        // Background alpha is ignored (background treated as opaque)
        val semiTransparentWhiteBg = 0x40FFFFFF
        assertEquals(21.0, ContrastPolicy.contrastRatio(black, semiTransparentWhiteBg), 0.01)
    }

    @Test
    fun parseHexHandlesValidAndInvalidFormatsWithoutThrowing() {
        assertEquals(0xFF12ABEF.toInt(), ContrastPolicy.parseHex("#12ABEF"))
        assertEquals(0xFF12ABEF.toInt(), ContrastPolicy.parseHex("#12abef"))
        assertEquals(0xFF12ABEF.toInt(), ContrastPolicy.parseHex("12aBeF"))
        assertEquals(0x8012ABEF.toInt(), ContrastPolicy.parseHex("#8012abEF"))
        assertEquals(0x00000000, ContrastPolicy.parseHex("#00000000"))

        val invalidInputs = listOf(
            "",
            "   ",
            "#",
            "#FFF",
            "FFF",
            "8012ABEF",
            "#12345",
            "#1234567",
            "#123456789",
            "#GGGGGG",
            "0xFFFFFF",
            "+123456",
            "-123456",
            " #FFFFFF",
            "#FFFFFF "
        )
        for (invalid in invalidInputs) {
            assertNull("Expected null for '$invalid'", ContrastPolicy.parseHex(invalid))
        }
    }
}
