package com.uacastplayer.cast

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CastProxyOperationTest {

    @Test
    fun `a synchronous proxy startup failure stays inside the result boundary`() {
        val result = CastProxyOperation.run<Unit> { throw IllegalStateException("socket bind failed") }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is never converted to a proxy failure`() {
        CastProxyOperation.run<Unit> { throw CancellationException("cancelled") }
    }

    @Test
    fun `a prepared proxy value passes through unchanged`() {
        val prepared = PreparedCastProxy("resource", "http://192.168.1.2:1234/resource")

        assertEquals(prepared, CastProxyOperation.run { prepared }.getOrThrow())
    }
}
