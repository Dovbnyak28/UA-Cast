package com.uacastplayer.core.concurrent

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class NonFatalTest {

    @Test
    fun `ordinary exception is captured`() {
        val expected = IllegalStateException("provider failed")

        val result = runCatchingNonFatal<Unit> { throw expected }

        assertSame(expected, result.exceptionOrNull())
    }

    @Test
    fun `coroutine cancellation is propagated`() {
        val expected = CancellationException("screen left")

        val actual = assertThrows(CancellationException::class.java) {
            runCatchingNonFatal<Unit> { throw expected }
        }

        assertSame(expected, actual)
    }

    @Test
    fun `fatal VM error is propagated`() {
        val expected = OutOfMemoryError("heap exhausted")

        val actual = assertThrows(OutOfMemoryError::class.java) {
            runCatchingNonFatal<Unit> { throw expected }
        }

        assertSame(expected, actual)
    }
}
