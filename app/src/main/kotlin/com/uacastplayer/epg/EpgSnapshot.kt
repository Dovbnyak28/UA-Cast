package com.uacastplayer.epg

/**
 * The last downloaded EPG document, kept exactly as received (still gzipped) - it's only
 * inflated and parsed on demand, never eagerly, to avoid holding a decompressed multi-megabyte
 * document in memory or on disk longer than needed.
 */
data class EpgSnapshot(
    val sourceFingerprint: String,
    val savedAtEpochMillis: Long,
    val gzipDocument: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is EpgSnapshot &&
            sourceFingerprint == other.sourceFingerprint &&
            savedAtEpochMillis == other.savedAtEpochMillis &&
            gzipDocument.contentEquals(other.gzipDocument)

    override fun hashCode(): Int {
        var result = sourceFingerprint.hashCode()
        result = 31 * result + savedAtEpochMillis.hashCode()
        result = 31 * result + gzipDocument.contentHashCode()
        return result
    }
}
