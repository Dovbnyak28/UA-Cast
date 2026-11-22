package app.zorik.player.ui.components

import androidx.compose.runtime.Composable
import app.zorik.player.ui.theme.motionAllowed
import app.zorik.player.ui.theme.systemMotionEnabled

internal fun animationsAllowedFor(animatorScale: Float): Boolean = systemMotionEnabled(animatorScale)

/** Reacts to Android's "Remove animations" setting for decorative infinite/stagger effects. */
@Composable
internal fun animationsAllowed(): Boolean = motionAllowed()
