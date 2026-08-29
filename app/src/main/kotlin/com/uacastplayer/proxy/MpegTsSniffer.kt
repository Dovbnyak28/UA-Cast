package com.uacastplayer.proxy

private const val TS_PACKET_SIZE = 188
private const val TS_SYNC_BYTE = 0x47
private const val BYTE_MASK = 0xFF

/**
 * Sniffs whether a byte prefix looks like raw MPEG-TS: two sync bytes exactly one packet (188
 * bytes) apart is enough to rule out a coincidental single 0x47 byte in unrelated content, while
 * staying cheap enough to run on every probed response. Both sync bytes are always required, so a
 * response too short to contain the start of a second packet cannot qualify. Diagnostic/remux
 * callers inspect a larger prefix for codec data; MIME correction needs only these two packets.
 * A single leading 0x47
 * alone must never qualify: 0x47 is ASCII 'G', so a short plain-text upstream error served with
 * a 200 ("Gateway Time-out...") would otherwise classify as TS and spin up a pointless remux
 * session on garbage. Shared between [ProxyServer]'s own remux-activation check and
 * [com.uacastplayer.data.cast.TsFirstSegmentDiagnostic]'s pre-flight source classification, so
 * both agree on what "looks like TS" means.
 */
object MpegTsSniffer {

    fun looksLikeMpegTs(bytes: ByteArray): Boolean = looksLikeMpegTs(bytes, bytes.size)

    /** Same probe for a populated prefix of a reusable buffer. Keeping [length] explicit avoids
     * copying a 64 KiB streaming buffer merely to inspect the first two packet boundaries. */
    fun looksLikeMpegTs(bytes: ByteArray, length: Int): Boolean {
        val available = length.coerceIn(0, bytes.size)
        return available > TS_PACKET_SIZE && syncByteAt(bytes, 0) && syncByteAt(bytes, TS_PACKET_SIZE)
    }

    private fun syncByteAt(bytes: ByteArray, offset: Int): Boolean =
        (bytes[offset].toInt() and BYTE_MASK) == TS_SYNC_BYTE
}
