package com.uacastplayer.player

/**
 * Whether a channel that just exhausted its retries deserves to be blamed for it.
 *
 * [PlaybackRetryPolicy] gives up after four attempts, and giving up used to mean one thing: mark
 * this channel dead and, if auto-skip is on, move to the next one. That is right when one stream is
 * broken and wrong in the case that produces it most often - the device having no network at all.
 *
 * With no network every channel fails, so the app walked the playlist: four failed attempts, a
 * decoder, an audio-focus request and a skip per channel, roughly one every three seconds, for as
 * long as the outage lasted. Captured on a Mi A2 during a 70-second Wi-Fi outage: about twenty
 * error/focus cycles and a march from the channel the user opened to an unrelated one several
 * positions away.
 *
 * The lasting damage is not the churn. Every channel walked past is added to the dead set, and that
 * set survives the outage - so once the network returns, auto-skip goes on skipping perfectly good
 * channels for the rest of the session, on the strength of a failure that was never theirs.
 *
 * A device with no network is not evidence about any channel.
 */
object DeadChannelPolicy {

    /**
     * True when the failure can fairly be attributed to this channel, i.e. the device had a network
     * to reach it through.
     *
     * @param hasNetwork whether the system reports any validated network. False during flight mode,
     *   a dropped Wi-Fi connection, or a captive portal that has not been signed into.
     */
    fun shouldBlameChannel(hasNetwork: Boolean): Boolean = hasNetwork

    /**
     * How long to wait before trying the same channel again when the network, not the channel, is
     * what failed.
     *
     * Twice [PlaybackRetryPolicy]'s whole ladder, which sums to exactly five seconds. Nothing this
     * app does brings a network back, so asking more often only costs battery - and ten seconds is
     * imperceptible against a live stream that has to rebuffer on return anyway.
     */
    const val NO_NETWORK_RETRY_MILLIS = 10_000L
}
