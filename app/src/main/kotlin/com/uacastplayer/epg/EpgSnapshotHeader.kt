package com.uacastplayer.epg

/**
 * The last downloaded EPG document, kept exactly as received - some sources serve gzip, others
 * plain XML (see [EpgSource]) - it's only inflated (when needed) and parsed on demand, never
 * eagerly, to avoid holding a decompressed multi-megabyte document in memory or on disk longer
 * than needed.
 */
data class EpgSnapshot(
    val sourceFingerprint: String,
    val savedAtEpochMillis: Long,
    val documentBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is EpgSnapshot &&
            sourceFingerprint == other.sourceFingerprint &&
            savedAtEpochMillis == other.savedAtEpochMillis &&
            documentBytes.contentEquals(other.documentBytes)

    override fun hashCode(): Int {
        var result = sourceFingerprint.hashCode()
        result = 31 * result + savedAtEpochMillis.hashCode()
        result = 31 * result + documentBytes.contentHashCode()
        return result
    }
}
