package com.uacastplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadStatusClassifierTest {

    @Test
    fun `2002 media queue noise is Superseded`() {
        val outcome = LoadStatusClassifier.classify(2002)
        assertTrue(outcome is LoadStatusOutcome.Superseded)
        assertEquals("MEDIA_QUEUE_NO_SESSION", outcome.statusName)
    }

    @Test
    fun `2103 REPLACED is Superseded`() {
        val outcome = LoadStatusClassifier.classify(2103)
        assertTrue(outcome is LoadStatusOutcome.Superseded)
        assertEquals("REPLACED", outcome.statusName)
    }

    @Test
    fun `2001 INVALID_REQUEST is Rejected - a genuine failure of this request`() {
        val outcome = LoadStatusClassifier.classify(2001)
        assertTrue(outcome is LoadStatusOutcome.Rejected)
        assertEquals("INVALID_REQUEST", outcome.statusName)
    }

    @Test
    fun `15 LOAD_FAILED is Failed`() {
        val outcome = LoadStatusClassifier.classify(15)
        assertTrue(outcome is LoadStatusOutcome.Failed)
        assertEquals("LOAD_FAILED", outcome.statusName)
    }

    @Test
    fun `2100 FAILED is Failed`() {
        val outcome = LoadStatusClassifier.classify(2100)
        assertTrue(outcome is LoadStatusOutcome.Failed)
        assertEquals("FAILED", outcome.statusName)
    }

    @Test
    fun `an unrecognized status code is Failed with an UNKNOWN name`() {
        val outcome = LoadStatusClassifier.classify(9999)
        assertTrue(outcome is LoadStatusOutcome.Failed)
        assertEquals("UNKNOWN", outcome.statusName)
        assertEquals(9999, outcome.statusCode)
    }
}
