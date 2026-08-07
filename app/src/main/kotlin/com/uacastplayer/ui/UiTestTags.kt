package com.uacastplayer.ui

/**
 * Compose semantics test tags for UI elements that tests need to target reliably (no stable content
 * description of their own, or one that varies by locale). Kept to the bare minimum actually needed
 * by a test - see app/src/androidTest and app/src/test.
 */
object UiTestTags {
    const val MINI_PLAYER_BAR = "mini_player_bar"

    /** The root top bar's title row, matched by tag because the title itself is a localised,
     * per-destination string. See `RootTopBarLayoutTest`. */
    const val ROOT_TOP_BAR_TITLE = "root_top_bar_title"

    /** The download banner's outer bounds, so a test can assert it does not overlap what follows
     * it - the banner's own texts are only part of its height. See `RootTopBarLayoutTest`. */
    const val DOWNLOAD_STATUS_BANNER = "download_status_banner"

    /** The update banner's outer bounds. Matched by tag rather than by its text, which carries a
     * localised string and a version number that changes with every release. */
    const val UPDATE_BANNER = "update_banner"
}
