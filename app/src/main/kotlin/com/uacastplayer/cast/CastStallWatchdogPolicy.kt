package com.uacastplayer.cast

sealed interface CastStallDecision {
    /** The receiver reached PLAYING - there is nothing left for the watchdog to catch. */
    data object Settled : CastStallDecision

    /** Not playing yet, but the load is demonstrably progressing - give it another tick. */
    data object KeepWaiting : CastStallDecision

    /** Nothing has moved for a whole tick (or the ceiling is up) - synthesize the IDLE/ERROR that
     * drives [CastRecoveryPolicy]'s reload cycle. */
    data object Fire : CastStallDecision
}

/**
 * Decides whether a load that hasn't reached PLAYING yet is actually stuck.
 *
 * "Has the receiver said PLAYING within N seconds" is the wrong question on the proxy path, and a
 * field capture proved it: casting an HD channel through the proxy, the receiver pulled a complete
 * 6.36MB segment 700ms before a flat 4s timeout fired and forced a reload. On the direct path every
 * byte travels origin -> receiver, so 4s of silence really does mean something is wrong; through the
 * proxy the same bytes travel origin -> phone -> receiver, and a single segment of that stream took
 * 2.85s to move. A receiver buffering two of them cannot report PLAYING inside 4s, so the timeout
 * fired on every attempt - and firing is not free. The reload cancels the in-flight fetches
 * (`Passthrough served: 200, 1260500B` of a 6.5MB segment, then `SocketException`), throwing away
 * partially-transferred megabytes and making the next attempt slower still. Three loads were spent
 * that way before playback happened to stick, turning a ~9s start into ~19s.
 *
 * So the signal is bytes, not time: the proxy knows exactly how much it has handed the receiver (see
 * [com.uacastplayer.data.cast.ProxyServer.bytesServedToReceiver]), and a receiver actively pulling
 * media is direct evidence that the load is fine, strictly better than the absence of a PLAYING
 * status. A tick with *zero* bytes delivered is a real stall and still fires immediately, so a
 * genuinely dead load is caught exactly as fast as before.
 *
 * Deliberately mode-agnostic: on the direct path the proxy serves nothing, so
 * [bytesDeliveredThisTick] is always 0 and this fires on the first tick - identical to the flat
 * timeout it replaces. No mode flag needed, and no way for the two paths to drift apart.
 */
object CastStallWatchdogPolicy {

    /** How long a tick is - also, therefore, how long a completely silent load waits before firing. */
    const val TICK_MILLIS = 4_000L

    /** Ceiling on the whole wait, however well it appears to be progressing. A receiver that keeps
     * fetching but never plays (an unsupported codec it only discovers after buffering, a decoder
     * that never starts) would otherwise be kept alive forever by its own fetching. */
    const val MAX_WAIT_MILLIS = 30_000L

    fun decide(elapsedMillis: Long, bytesDeliveredThisTick: Long, isPlaying: Boolean): CastStallDecision = when {
        isPlaying -> CastStallDecision.Settled
        elapsedMillis >= MAX_WAIT_MILLIS -> CastStallDecision.Fire
        bytesDeliveredThisTick > 0 -> CastStallDecision.KeepWaiting
        else -> CastStallDecision.Fire
    }
}
