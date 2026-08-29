package com.uacastplayer.cast

import com.uacastplayer.core.cast.TsSourceKind
import com.uacastplayer.core.media.StreamMimeClassifier
import com.uacastplayer.core.media.StreamType

/**
 * Content-type for a Cast load request. Our own proxy/remux URL (`/hls/...`, see
 * `data/cast/ProxyServer.kt`) is always HLS regardless of what it fetched from - it repackages the
 * origin into an HLS playlist either way (rewrite for an already-HLS origin, remux for raw TS, see
 * docs/PROXY_RULES.md) - so that check wins first. Otherwise [TsSourceKind] from
 * `data/cast/TsFirstSegmentDiagnostic` is authoritative when known: raw TS needs `video/mp2t`
 * (an HLS mime type on a bare TS URL is exactly the kind of receiver-side "Invalid Request" this
 * exists to avoid), HLS needs `application/x-mpegurl`. A null/[TsSourceKind.Unknown] sourceKind
 * (no diagnostic result yet, or PAT/PMT not found in the probe window) falls back to the existing
 * URL-token guess shared with local playback ([StreamMimeClassifier]).
 */
object CastContentType {

    private const val PROXY_PATH_MARKER = "/hls/"

    fun of(url: String, sourceKind: TsSourceKind?): String {
        if (PROXY_PATH_MARKER in url) return "application/x-mpegurl"
        return when (sourceKind) {
            TsSourceKind.Hls -> "application/x-mpegurl"
            TsSourceKind.RawTs -> "video/mp2t"
            TsSourceKind.Unknown, null -> urlMimeType(url)
        }
    }

    private fun urlMimeType(url: String): String = when (StreamMimeClassifier.classify(url)) {
        StreamType.HLS -> "application/x-mpegurl"
        StreamType.DASH -> "application/dash+xml"
    }
}
