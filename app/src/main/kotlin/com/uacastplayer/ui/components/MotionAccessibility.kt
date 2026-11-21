package com.uacastplayer.ui.components

import androidx.compose.runtime.Composable
import com.uacastplayer.ui.theme.motionAllowed
import com.uacastplayer.ui.theme.systemMotionEnabled

internal fun animationsAllowedFor(animatorScale: Float): Boolean = systemMotionEnabled(animatorScale)

/** Reacts to Android's "Remove animations" setting for decorative infinite/stagger effects. */
@Composable
internal fun animationsAllowed(): Boolean = motionAllowed()
