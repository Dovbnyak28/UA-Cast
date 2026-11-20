package com.uacastplayer.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalVideoRangeTest {
    @Test fun completeReadUsesActualLength() {
        assertEquals(LocalVideoRange(0, 999), LocalVideoRange.parse(null, 1000))
        assertNull(LocalVideoRange.parse(null, 0))
        assertNull(LocalVideoRange.parse(null, -1))
    }
    @Test fun exactAndOpenEndedRangesRemainBounded() {
        assertEquals(LocalVideoRange(200, 299), LocalVideoRange.parse("bytes=200-299", 1000))
        assertEquals(LocalVideoRange(900, 999), LocalVideoRange.parse("bytes=900-", 1000))
        assertEquals(LocalVideoRange(900, 999), LocalVideoRange.parse("bytes=900-99999", 1000))
    }
    @Test fun suffixRangesCannotSeekBeforeTheFile() {
        assertEquals(LocalVideoRange(900, 999), LocalVideoRange.parse("bytes=-100", 1000))
        assertEquals(LocalVideoRange(0, 999), LocalVideoRange.parse("bytes=-10000", 1000))
        assertNull(LocalVideoRange.parse("bytes=-0", 1000))
    }
    @Test fun malformedReversedMultipleAndOutOfBoundsRequestsFailClosed() {
        listOf("bytes=-", "bytes=1-0", "bytes=1000-", "bytes=a-5", "bytes=0-2,4-8", "items=0-5")
            .forEach { assertNull(it, LocalVideoRange.parse(it, 1000)) }
    }
    @Test fun numericOverflowDoesNotWrapIntoAPrivatePrefix() {
        assertNull(LocalVideoRange.parse("bytes=9223372036854775808-", Long.MAX_VALUE))
        assertNull(LocalVideoRange.parse("bytes=0-9223372036854775808", Long.MAX_VALUE))
        assertNull(LocalVideoRange.parse("bytes=-9223372036854775808", Long.MAX_VALUE))
    }
    @Test fun videosBeyondFourGiBAreNotTruncatedToInt() {
        val range = LocalVideoRange.parse("bytes=4294967296-4294967551", 5L * 1024 * 1024 * 1024)!!
        assertEquals(4294967296, range.start)
        assertEquals(256, range.length)
    }
    @Test fun maximalFiniteLengthStillHasCorrectArithmetic() {
        assertEquals(Long.MAX_VALUE, LocalVideoRange.parse(null, Long.MAX_VALUE)?.length)
        assertEquals(LocalVideoRange(0, 99), LocalVideoRange.parse("bytes=-9223372036854775807", 100))
    }
}
