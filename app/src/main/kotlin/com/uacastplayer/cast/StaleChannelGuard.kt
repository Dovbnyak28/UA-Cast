package com.uacastplayer.cast

/**
 * Whether a deferred continuation captured for [streamUrl] - a watchdog timeout, a diagnostic
 * result arriving off the IO dispatcher, a future recovery reload - still applies to whatever
 * channel is actually active right now. Rapid channel zapping means several of these can be
 * in flight for channels the user has already moved past; the direct-mode watchdog job is
 * cancelled on every new channel switch (see `CastSessionRepository.startPlayback`), but that
 * cancellation is cooperative and races a diagnostic already past its last suspension point - this
 * check doesn't depend on that timing at all. Without it, a stale continuation firing late would
 * load the WRONG (already abandoned) channel onto the receiver, or attribute a codec incompatibility
 * banner to whatever channel happens to be on screen instead of the one it was actually diagnosed
 * for.
 */
object StaleChannelGuard {
    fun isCurrent(streamUrl: String, activeStreamUrl: String?): Boolean = streamUrl == activeStreamUrl
}
