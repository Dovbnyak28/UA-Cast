package com.uacastplayer.cast

/**
 * When the current uninterrupted stretch of playback began, or `null` if the receiver is not
 * playing. Feeds [CastRecoveryPolicy.shouldResetAttemptCounter], which forgives the accumulated
 * recovery attempts once a channel has held up long enough to count as working again.
 *
 * The rule that is easy to get wrong is what a *second* `PLAYING` update does: nothing. The Cast SDK
 * re-reports the current status on a variety of unrelated events - a volume change, a queue update,
 * a periodic refresh - so a channel sitting happily on screen produces a stream of identical PLAYING
 * updates. Restarting the clock on each of them (`= nowMillis` rather than `?: nowMillis`) would
 * pin the measured stretch near zero forever, the attempt counter would never reset, and a channel
 * that had been fine for an hour would still be carrying failures from before it recovered - so it
 * would give up early on its next genuine hiccup. The counter would look correct in every log line
 * along the way, which is why this is worth a policy of its own rather than an expression inlined
 * in a status handler.
 *
 * Anything that is not PLAYING clears the window outright: buffering, idle and paused all end the
 * stretch, and the next PLAYING starts a fresh one.
 */
object PlayingWindowPolicy {

    data class Transition(
        val nextStartMillis: Long?,
        /** Duration of the PLAYING window immediately before [status] is applied. Unlike asking
         * [stableMillis] after [next], this preserves the evidence when BUFFERING/IDLE closes it. */
        val stableBeforeTransitionMillis: Long,
    )

    /** The new window start given the previous one and the status that just arrived. */
    fun next(current: Long?, status: ReceiverStatus, nowMillis: Long): Long? =
        if (status == ReceiverStatus.PLAYING) current ?: nowMillis else null

    fun transition(current: Long?, status: ReceiverStatus, nowMillis: Long): Transition = Transition(
        nextStartMillis = next(current, status, nowMillis),
        stableBeforeTransitionMillis = stableMillis(current, nowMillis),
    )

    /** How long the receiver has been continuously playing, or 0 if it is not - the shape
     * [CastRecoveryPolicy.shouldResetAttemptCounter] expects. */
    fun stableMillis(current: Long?, nowMillis: Long): Long =
        current?.let { nowMillis - it } ?: 0L
}
