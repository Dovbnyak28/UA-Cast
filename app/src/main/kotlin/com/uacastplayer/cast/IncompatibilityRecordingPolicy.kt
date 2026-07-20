package com.uacastplayer.cast

/**
 * Whether a (stream, receiver) pair is worth persisting to `data/cast/IncompatibilityMemoryStore` -
 * previously every mid-playback failure that [CastRecoveryPolicy] ultimately gave up on was
 * recorded, even one that followed hours of perfectly fine playback (a single unlucky network blip
 * outliving the retry budget). That meant a channel that's normally fine could get skipped straight
 * to the proxy for the next 30 days over one bad moment. Recording is reserved for cases where
 * retrying can't reasonably be expected to help: a confirmed hard-incompatible codec
 * ([CastCompatibilityVerdict.IncompatibleVideo]), or a channel that never reached `PLAYING` at all
 * this casting episode (including every recovery reload) - if it played even briefly, the failure
 * was transient, not a genuine incompatibility.
 */
object IncompatibilityRecordingPolicy {
    fun shouldRecord(isConfirmedIncompatible: Boolean, everReachedPlaying: Boolean): Boolean =
        isConfirmedIncompatible || !everReachedPlaying
}
