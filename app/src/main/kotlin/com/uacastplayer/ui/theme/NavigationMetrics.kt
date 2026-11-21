package com.uacastplayer.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val EXTRA_HEIGHT_PER_FONT_SCALE_DP = 40f
internal const val LARGE_NAVIGATION_FONT_SCALE = 1.5f
private val LargeNavigationMinHeight = 104.dp

/** Shared with the mini player: accessibility-sized navigation must never overlap playback controls. */
fun navigationBarHeight(fontScale: Float): Dp {
    val height = GlassTabBarHeight + (EXTRA_HEIGHT_PER_FONT_SCALE_DP * (fontScale.coerceAtLeast(1f) - 1f)).dp
    return if (fontScale >= LARGE_NAVIGATION_FONT_SCALE) maxOf(LargeNavigationMinHeight, height) else height
}

private const val MINI_EXTRA_HEIGHT_PER_FONT_SCALE_DP = 40f

/** One title and one programme/receiver line fit without wasting the small-screen viewport. */
fun miniPlayerHeight(fontScale: Float): Dp =
    MiniPlayerBarHeight + (MINI_EXTRA_HEIGHT_PER_FONT_SCALE_DP * (fontScale.coerceAtLeast(1f) - 1f)).dp

/** Reserve the overlay's actual height outside scroll viewports, so bringIntoView cannot hide a target under it. */
fun miniPlayerContentPadding(visible: Boolean, fontScale: Float = 1f): Dp =
    if (visible) miniPlayerHeight(fontScale) + GapM else 0.dp
