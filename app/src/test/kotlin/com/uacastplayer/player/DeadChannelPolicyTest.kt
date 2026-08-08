package com.uacastplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression protection for a defect captured on hardware: with Wi-Fi off, the player marched
 * through the playlist marking channels dead.
 *
 * From a 70-second outage on a Mi A2 - four failed attempts per channel, then a skip, roughly one
 * cycle every three seconds, each with its own decoder and audio-focus request. The user opened one
 * channel and came back to a different one.
 *
 * The churn ends with the outage. The dead set does not: every channel walked past stays marked,
 * so auto-skip keeps skipping working channels afterwards, for a failure that belonged to the
 * network.
 */
class DeadChannelPolicyTest {

    /** One stream failing while the device is online is what the dead set is for. */
    @Test
    fun aChannelThatFailsWithAWorkingNetworkIsBlamed() {
        assertTrue(DeadChannelPolicy.shouldBlameChannel(hasNetwork = true))
    }

    /** A device with no network is not evidence about any channel. */
    @Test
    fun aChannelIsNotBlamedWhenTheDeviceHasNoNetwork() {
        assertFalse(DeadChannelPolicy.shouldBlameChannel(hasNetwork = false))
    }

    /**
     * The retry ladder is for a stream that might recover in a second. An outage will not, and no
     * amount of asking shortens it - so the no-network wait is deliberately longer than the whole
     * ladder put together.
     */
    @Test
    fun theNoNetworkWaitOutlastsTheEntireRetryLadder() {
        // 500 + 1000 + 1500 + 2000, the four attempts PlaybackRetryPolicy allows.
        val fullLadderMillis = (1..PlaybackRetryPolicy.MAX_ATTEMPTS).sumOf { it * 500L }
        assertTrue(
            "no-network wait (${DeadChannelPolicy.NO_NETWORK_RETRY_MILLIS}ms) must outlast the " +
                "retry ladder (${fullLadderMillis}ms)",
            DeadChannelPolicy.NO_NETWORK_RETRY_MILLIS > fullLadderMillis,
        )
        assertEquals(10_000L, DeadChannelPolicy.NO_NETWORK_RETRY_MILLIS)
    }
}
