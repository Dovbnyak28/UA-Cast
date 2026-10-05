package com.uacastplayer.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val EXTRA_HEIGHT_PER_FONT_SCALE_DP = 40f

/** Shared with the mini player: accessibility-sized navigation must never overlap playback controls. */
fun navigationBarHeight(fontScale: Float): Dp =
    GlassTabBarHeight + (EXTRA_HEIGHT_PER_FONT_SCALE_DP * (fontScale.coerceAtLeast(1f) - 1f)).dp

/** Reserve the overlay's actual height outside scroll viewports, so bringIntoView cannot hide a target under it. */
fun miniPlayerContentPadding(visible: Boolean): Dp = if (visible) MiniPlayerBarHeight + GapM else 0.dp
