package com.uacastplayer.data.cast

import java.io.InputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/** A total deadline, starting at accept (including queue time), not a resettable idle timeout. */
internal class AdmissionDeadlineInput(private val socket: Socket, private val deadlineNanos: Long) : InputStream() {
    private val input = socket.getInputStream()

    override fun read(): Int {
        applyRemainingTimeout()
        return input.read()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        applyRemainingTimeout()
        return input.read(buffer, offset, length)
    }

    private fun applyRemainingTimeout() {
        val remaining = deadlineNanos - System.nanoTime()
        if (remaining <= 0) throw SocketTimeoutException("HTTP admission deadline exceeded")
        socket.soTimeout = TimeUnit.NANOSECONDS.toMillis(remaining).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
    }
}
