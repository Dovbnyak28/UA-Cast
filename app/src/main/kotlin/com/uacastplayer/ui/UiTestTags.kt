package com.uacastplayer.ui

/**
 * Compose semantics test tags for UI elements that instrumentation tests need to target reliably
 * (no stable content description of their own, or one that varies by locale). Kept to the bare
 * minimum actually needed by a test - see app/src/androidTest.
 */
object UiTestTags {
    const val MINI_PLAYER_BAR = "mini_player_bar"
}
