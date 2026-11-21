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

    @Test fun accessibleMiniPlayerAndReservedViewportAlwaysUseTheSameHeight() {
        for (scale in listOf(0.85f, 1f, 1.3f, 1.5f, 2f)) {
            assertEquals(miniPlayerHeight(scale) + GapM, miniPlayerContentPadding(true, scale))
            assertEquals(0.dp, miniPlayerContentPadding(false, scale))
        }
        assertEquals(104.dp, miniPlayerHeight(2f))
    }

    @Test fun accessibleNavigationKeepsItsLabelsAndDoesNotShrinkAtSmallFontScales() {
        assertEquals(GlassTabBarHeight, navigationBarHeight(0.85f))
        assertEquals(GlassTabBarHeight + 40.dp, navigationBarHeight(2f))
        assertEquals(104.dp, navigationBarHeight(1.5f))
    }
}
