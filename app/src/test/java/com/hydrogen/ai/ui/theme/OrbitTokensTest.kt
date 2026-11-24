package com.hydrogen.ai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitTokensTest {
    @Test fun spacingFollowsStudioScale() {
        val tokens = OrbitSpacing()
        assertEquals(listOf(2, 4, 8, 12, 16, 20, 24, 32, 48).map { it.dp },
            listOf(tokens.xxs, tokens.xs, tokens.s, tokens.m, tokens.l,
                tokens.xl, tokens.xxl, tokens.xxxl, tokens.huge))
    }

    @Test fun shapesUseConsistentCorners() {
        val tokens = OrbitShapeTokens()
        assertEquals(RoundedCornerShape(6.dp), tokens.xs)
        assertEquals(RoundedCornerShape(12.dp), tokens.m)
        assertEquals(RoundedCornerShape(16.dp), tokens.l)
        assertEquals(RoundedCornerShape(28.dp), tokens.xxl)
        assertEquals(RoundedCornerShape(percent = 50), tokens.pill)
    }

    @Test fun motionIsMonotonicAndMatchesDirectorBrief() {
        val tokens = OrbitMotionTokens()
        assertEquals(listOf(120, 220, 350, 500),
            listOf(tokens.fastMs, tokens.standardMs, tokens.emphasizedMs, tokens.longMs))
        assertTrue(tokens.fastMs < tokens.standardMs)
        assertTrue(tokens.dragSpringDampingRatio in 0.85f..0.9f)
        assertTrue(tokens.dragSpringStiffness in 380f..500f)
    }

    @Test fun typographyReusesExistingAccessibleScale() {
        assertEquals(16.sp, OrbitTypography.bodyLarge.fontSize)
        assertEquals(26.sp, OrbitTypography.bodyLarge.lineHeight)
        assertEquals(28.sp, OrbitTypography.headlineMedium.fontSize)
    }
}
