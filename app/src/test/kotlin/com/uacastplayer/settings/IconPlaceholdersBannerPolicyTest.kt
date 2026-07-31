package com.uacastplayer.settings

import com.uacastplayer.data.prefs.IconDisplayMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconPlaceholdersBannerPolicyTest {

    @Test
    fun `shows when placeholders is the tier default and the hint has not been seen`() {
        assertTrue(
            IconPlaceholdersBannerPolicy.shouldShow(
                iconDisplayMode = IconDisplayMode.PLACEHOLDERS,
                isTierDefault = true,
                hasSeenHint = false,
            ),
        )
    }

    @Test
    fun `does not show once the hint has been seen`() {
        assertFalse(
            IconPlaceholdersBannerPolicy.shouldShow(
                iconDisplayMode = IconDisplayMode.PLACEHOLDERS,
                isTierDefault = true,
                hasSeenHint = true,
            ),
        )
    }

    @Test
    fun `does not show when the user explicitly chose placeholders`() {
        assertFalse(
            IconPlaceholdersBannerPolicy.shouldShow(
                iconDisplayMode = IconDisplayMode.PLACEHOLDERS,
                isTierDefault = false,
                hasSeenHint = false,
            ),
        )
    }

    @Test
    fun `does not show for a non-placeholders mode even if tier-default`() {
        assertFalse(
            IconPlaceholdersBannerPolicy.shouldShow(
                iconDisplayMode = IconDisplayMode.CACHE_LIMITED,
                isTierDefault = true,
                hasSeenHint = false,
            ),
        )
    }
}
