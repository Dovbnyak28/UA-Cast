package com.uacastplayer.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationMetricsTest {
    @Test fun noPlayerDoesNotWasteContentSpace() {
        assertEquals(0.dp, miniPlayerContentPadding(visible = false))
    }

    @Test fun visiblePlayerReservesItsHeightAndTheGapAboveIt() {
        assertEquals(MiniPlayerBarHeight + GapM, miniPlayerContentPadding(visible = true))
    }

    @Test fun accessibleNavigationKeepsItsLabelsAndDoesNotShrinkAtSmallFontScales() {
        assertEquals(GlassTabBarHeight, navigationBarHeight(0.85f))
        assertEquals(GlassTabBarHeight + 40.dp, navigationBarHeight(2f))
    }
}
