package com.uacastplayer.data.epg

import com.uacastplayer.epg.EpgData
import com.uacastplayer.epg.EpgIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The diagnosis that used to be thrown away. Every case of [EpgOutcome] that is not a success has
 * to produce something a reader can act on - "not loaded" alone is what sent a field report back
 * with its own headline unanswerable.
 */
class EpgFailureReasonTest {

    @Test
    fun aLoadedGuideHasNothingToExplain() {
        val loaded = EpgOutcome.Loaded(EpgData(EpgIndex(emptyList()), emptyMap()))
        assertNull(EpgFailureReason.of(loaded))
    }

    @Test
    fun anHttpErrorNamesItsCode() {
        assertEquals("the server answered HTTP 404", EpgFailureReason.of(EpgOutcome.HttpError(404)))
    }

    @Test
    fun aReadErrorNamesTheExceptionThatCausedIt() {
        val reason = EpgFailureReason.of(EpgOutcome.ReadError("UnknownHostException"))
        assertTrue(reason, reason!!.contains("UnknownHostException"))
    }

    /** A network failure with nothing attached still has to say something rather than "null". */
    @Test
    fun aReadErrorWithNoCauseStillReads() {
        val reason = EpgFailureReason.of(EpgOutcome.ReadError(null))
        assertFalse(reason, reason!!.contains("null"))
    }

    /** Told with the actual limit, so "too big" can be checked against the feed rather than trusted. */
    @Test
    fun aFeedOverTheLimitIsToldWhatTheLimitIs() {
        val reason = EpgFailureReason.of(EpgOutcome.SizeLimitExceeded)
        assertTrue(reason, reason!!.contains("96MB"))
    }

}
