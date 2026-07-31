package com.uacastplayer.player

/**
 * Decides what fullscreen player touch gestures mean, independent of how Compose reports them.
 * Kept separate from the pointer-input plumbing in `PlayerScreen` so the thresholds and zone math
 * are testable without a Compose UI test.
 */
object PlayerGesturePolicy {

    /** A horizontal swipe shorter than this fraction of the screen width is treated as
     * incidental finger drift, not an intentional channel switch. */
    const val MIN_SWIPE_WIDTH_FRACTION = 0.15f

    /** Which third of the screen (by x-position, as a 0f..1f fraction of width) a gesture
     * started in - the left/right thirds drive brightness/volume, the center third is reserved
     * for the horizontal channel-switch swipe so the two never fight over the same touch area. */
    enum class GestureZone { LEFT, CENTER, RIGHT }

    enum class SwipeChannelAction { NEXT, PREVIOUS }

    fun zoneFor(xFraction: Float): GestureZone = when {
        xFraction < 1f / 3f -> GestureZone.LEFT
        xFraction > 2f / 3f -> GestureZone.RIGHT
        else -> GestureZone.CENTER
    }

    /**
     * [widthFraction] is the swipe's total horizontal travel as a fraction of the screen width -
     * negative for a leftward swipe. Swiping left (content moving toward you) advances to the
     * next channel, mirroring a "next page" gesture; swiping right goes back.
     */
    fun channelSwipeAction(widthFraction: Float): SwipeChannelAction? = when {
        widthFraction <= -MIN_SWIPE_WIDTH_FRACTION -> SwipeChannelAction.NEXT
        widthFraction >= MIN_SWIPE_WIDTH_FRACTION -> SwipeChannelAction.PREVIOUS
        else -> null
    }

    /** Converts a raw drag delta (positive = finger moved down) into a level change (positive =
     * increase) - dragging up is the universal "turn it up" gesture for both brightness and volume. */
    fun levelDelta(dragDeltaFraction: Float): Float = -dragDeltaFraction

    /** Clamps a brightness/volume level to its valid 0f..1f range after applying a delta. */
    fun applyLevelDelta(currentLevel: Float, delta: Float): Float = (currentLevel + delta).coerceIn(0f, 1f)
}
