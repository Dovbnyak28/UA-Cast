package com.uacastplayer.proxy

private const val TS_PACKET_SIZE = 188
private const val TS_SYNC_BYTE = 0x47
private const val BYTE_MASK = 0xFF
private const val SNIFF_PACKET_COUNT = 2

/**
 * Sniffs whether a byte prefix looks like raw MPEG-TS: two sync bytes exactly one packet (188
 * bytes) apart is enough to rule out a coincidental single 0x47 byte in unrelated content, while
 * staying cheap enough to run on every probed response. Shared between [ProxyServer]'s own
 * remux-activation check and [com.uacastplayer.data.cast.TsFirstSegmentDiagnostic]'s pre-flight
 * source classification, so both agree on what "looks like TS" means.
 */
object MpegTsSniffer {

    fun looksLikeMpegTs(bytes: ByteArray): Boolean {
        val firstByteIsSync = bytes.isNotEmpty() && syncByteAt(bytes, 0)
        if (bytes.size < TS_PACKET_SIZE * SNIFF_PACKET_COUNT) return firstByteIsSync
        return firstByteIsSync && syncByteAt(bytes, TS_PACKET_SIZE)
    }

    private fun syncByteAt(bytes: ByteArray, offset: Int): Boolean =
        (bytes[offset].toInt() and BYTE_MASK) == TS_SYNC_BYTE
}
