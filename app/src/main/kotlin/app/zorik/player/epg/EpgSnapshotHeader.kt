package app.zorik.player.epg

/**
 * Metadata for the last downloaded EPG document. The document body itself is deliberately not a
 * field here - it's streamed directly to/from disk by [EpgSnapshotCodec] and
 * [app.zorik.player.data.epg.EpgSnapshotStore] instead, since feeds can run tens of megabytes and
 * holding one as an in-memory ByteArray alongside everything else is wasteful.
 */
data class EpgSnapshotHeader(
    val sourceFingerprint: String,
    val savedAtEpochMillis: Long,
)
