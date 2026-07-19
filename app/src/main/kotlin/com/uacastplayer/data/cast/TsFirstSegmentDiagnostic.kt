package com.uacastplayer.data.cast

import com.uacastplayer.cast.TsProgramInfo
import com.uacastplayer.cast.TsProgramInfoParser
import com.uacastplayer.cast.TsSourceKind
import com.uacastplayer.core.io.BoundedByteReader
import com.uacastplayer.proxy.M3u8Rewriter
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** [sourceKind] drives Cast delivery routing (see `cast.CastDeliveryStrategy.onDiagnosticResult`)
 * independently of whether [programInfo] came back null - a raw-TS origin whose PAT/PMT didn't
 * fit the probe window is still known to be raw TS, which matters even without codec info. */
data class TsDiagnosticResult(val programInfo: TsProgramInfo?, val sourceKind: TsSourceKind)

/**
 * Probes a channel's stream URL for its actual declared video/audio codecs (see
 * [TsProgramInfoParser]), so a receiver that will never be able to play it doesn't have to wait
 * out the full watchdog timeout to find out - see `cast.CastCompatibilityPolicy` for how the
 * result becomes a verdict.
 *
 * The URL can be either an HLS playlist or a raw MPEG-TS stream - IPTV origins routinely use
 * tokenized/extensionless URLs that give no hint which, and a raw-TS URL fed to a text playlist
 * parser just silently finds no segments (see [TsSourceClassifier]). The initial probe is
 * therefore always read as raw bytes first and classified before deciding how to proceed: an HLS
 * playlist needs its first segment fetched separately to reach actual TS packets, while raw TS
 * bytes already are the probe, sniffed directly with no second request.
 */
object TsFirstSegmentDiagnostic {

    private const val INITIAL_PROBE_RANGE_HEADER = "bytes=0-262143"
    private const val MAX_INITIAL_PROBE_BYTES = 256 * 1024
    private const val SEGMENT_PROBE_RANGE_HEADER = "bytes=0-262143"
    private const val MAX_SEGMENT_PROBE_BYTES = 256 * 1024

    fun diagnose(streamUrl: String, httpClient: OkHttpClient): TsDiagnosticResult = try {
        val request = Request.Builder().url(streamUrl).header("Range", INITIAL_PROBE_RANGE_HEADER).build()
        httpClient.newCall(request).execute().use { response -> diagnoseResponse(response, httpClient) }
    } catch (_: Exception) {
        TsDiagnosticResult(null, TsSourceKind.Unknown)
    }

    private fun diagnoseResponse(response: Response, httpClient: OkHttpClient): TsDiagnosticResult {
        val body = response.body ?: return TsDiagnosticResult(null, TsSourceKind.Unknown)
        // Capped independently of the Range header above: an origin that ignores Range and just
        // streams the whole live feed must not get to dictate how much we buffer - sniffing only
        // ever needs the leading PAT/PMT packets (raw TS) or the playlist header plus a segment
        // reference (HLS) anyway.
        val prefix = BoundedByteReader.readAtMostBytes(body.byteStream(), MAX_INITIAL_PROBE_BYTES)
        val finalUrl = response.request.url.toString()
        return when (TsSourceClassifier.classify(response.header("Content-Type"), prefix)) {
            TsSourceKind.RawTs -> TsDiagnosticResult(TsProgramInfoParser.parse(prefix), TsSourceKind.RawTs)
            TsSourceKind.Hls -> {
                val info = diagnoseHlsSegment(prefix, finalUrl, httpClient)
                TsDiagnosticResult(info, TsSourceKind.Hls)
            }
            TsSourceKind.Unknown -> TsDiagnosticResult(null, TsSourceKind.Unknown)
        }
    }

    private fun diagnoseHlsSegment(
        playlistPrefix: ByteArray,
        finalUrl: String,
        httpClient: OkHttpClient,
    ): TsProgramInfo? {
        val playlistText = String(playlistPrefix, Charsets.UTF_8)
        val segmentUrl = firstMediaSegmentLine(playlistText)?.let { M3u8Rewriter.resolveUrl(finalUrl, it) }
            ?: return null
        val segmentRequest = Request.Builder().url(segmentUrl).header("Range", SEGMENT_PROBE_RANGE_HEADER).build()
        return httpClient.newCall(segmentRequest).execute().use { segmentResponse ->
            segmentResponse.body?.let { body ->
                TsProgramInfoParser.parse(BoundedByteReader.readAtMostBytes(body.byteStream(), MAX_SEGMENT_PROBE_BYTES))
            }
        }
    }

    /** The first non-blank, non-tag line in an HLS playlist - its first actual media segment
     * reference, per the M3U8 spec (`#`-prefixed lines are tags/comments). Returns null for a
     * playlist with no segments (a master playlist, or one that's empty/malformed). */
    internal fun firstMediaSegmentLine(playlistText: String): String? =
        playlistText.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
}
