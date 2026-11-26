package app.zorik.player.guidedtour

/**
 * The stable names UI elements register themselves under, so a tip can say *what* it points at
 * without knowing where on the screen that is.
 *
 * Semantic, never coordinates: a layout change moves the pixels and leaves the name alone, which is
 * the whole reason these exist. The values are also deliberately not the same constants as
 * [app.zorik.player.ui.UiTestTags] - a test tag is something a test may change freely, and a tip
 * that silently stopped appearing because a test was tidied would be a bad trade.
 */
object GuidedTourKeys {

    /** Registered on *both* search fields - the one over all channels on the groups screen, and the
     * one inside an opened group. They are never composed at the same time, and a tip that only knew
     * about the first would never appear for someone who happened to have a group open. */
    const val CHANNEL_SEARCH = "channel_search"

    /** The video frame on the player page - what the player tip is about. */
    const val PLAYER_VIDEO = "player_video"

    /** The Chromecast and "other TVs" buttons together, on the player page. One spotlight over both,
     * because the tip explains both and they sit side by side. */
    const val CAST_BUTTON = "cast_button"

    /** Every key the app is expected to be able to register. Used by `GuidedTipsTest` to catch a tip
     * pointing at a name nothing will ever report. */
    val ALL: Set<String> = setOf(CHANNEL_SEARCH, PLAYER_VIDEO, CAST_BUTTON)
}
