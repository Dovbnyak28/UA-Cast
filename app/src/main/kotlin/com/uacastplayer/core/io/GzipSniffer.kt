package com.uacastplayer.core.io

/** Detects the gzip magic number so callers can decide whether to inflate, without trusting a URL's file extension. */
object GzipSniffer {
    private const val MAGIC_BYTE_0 = 0x1f
    private const val MAGIC_BYTE_1 = 0x8b.toByte()

    fun isGzip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == MAGIC_BYTE_0.toByte() && bytes[1] == MAGIC_BYTE_1
}
