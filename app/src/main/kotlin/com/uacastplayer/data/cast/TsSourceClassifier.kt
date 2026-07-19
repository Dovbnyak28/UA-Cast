package com.uacastplayer.data.cast

import com.uacastplayer.cast.TsSourceKind
import com.uacastplayer.proxy.MpegTsSniffer
import com.uacastplayer.proxy.PlaylistDetector

/**
 * Classifies a probed byte prefix as HLS playlist text, raw MPEG-TS, or neither - the first
 * question [TsFirstSegmentDiagnostic] has to answer before it knows how to find codec info at
 * all: an HLS playlist needs its first segment fetched separately to actually reach TS packets,
 * while raw TS bytes already are the probe. Reuses the same magic-byte detectors [ProxyServer]
 * itself uses to route real requests, so the diagnostic and the proxy always agree on what a
 * given origin actually is.
 */
object TsSourceClassifier {

    fun classify(contentType: String?, prefixBytes: ByteArray): TsSourceKind = when {
        PlaylistDetector.isPlaylist(contentType, prefixBytes) -> TsSourceKind.Hls
        MpegTsSniffer.looksLikeMpegTs(prefixBytes) -> TsSourceKind.RawTs
        else -> TsSourceKind.Unknown
    }
}
