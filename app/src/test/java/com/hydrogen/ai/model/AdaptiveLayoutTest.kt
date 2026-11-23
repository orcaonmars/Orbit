package com.hydrogen.ai.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {

    @Test
    fun phonesKeepTheSlideInDrawerInBothOrientations() {
        assertFalse(AdaptiveLayout.isTwoPane(412, 915)) // portrait
        assertFalse(AdaptiveLayout.isTwoPane(915, 412)) // landscape: wide but short
    }

    @Test
    fun tabletsAndOpenFoldablesGetTwoPanes() {
        assertTrue(AdaptiveLayout.isTwoPane(1280, 800)) // tablet landscape
        assertTrue(AdaptiveLayout.isTwoPane(841, 701)) // large foldable, unfolded
    }

    @Test
    fun smallTabletPortraitStaysSinglePane() {
        assertFalse(AdaptiveLayout.isTwoPane(800, 1280))
        assertFalse(AdaptiveLayout.isTwoPane(673, 841))
    }

    @Test
    fun thresholdsAreInclusive() {
        assertTrue(AdaptiveLayout.isTwoPane(AdaptiveLayout.TWO_PANE_MIN_WIDTH_DP, AdaptiveLayout.TWO_PANE_MIN_HEIGHT_DP))
        assertFalse(AdaptiveLayout.isTwoPane(AdaptiveLayout.TWO_PANE_MIN_WIDTH_DP - 1, 900))
        assertFalse(AdaptiveLayout.isTwoPane(1200, AdaptiveLayout.TWO_PANE_MIN_HEIGHT_DP - 1))
    }
}
