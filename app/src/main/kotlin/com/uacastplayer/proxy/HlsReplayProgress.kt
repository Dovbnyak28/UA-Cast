package com.uacastplayer.proxy

/** Bounds a committed response whose origin still serves manifests but no usable media. */
internal class HlsReplayProgress(
    private val maxIdleMillis: Long = 60_000L,
    private val nowMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) {
    private var lastBytes = 0L
    private var lastProgressAt = nowMillis()

    fun mayContinue(bytesWritten: Long): Boolean {
        val now = nowMillis()
        if (bytesWritten > lastBytes) {
            lastBytes = bytesWritten
            lastProgressAt = now
        }
        return now - lastProgressAt < maxIdleMillis
    }
}

private const val NANOS_PER_MILLI = 1_000_000L
