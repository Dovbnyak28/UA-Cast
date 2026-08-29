package com.uacastplayer.data.cast

import com.uacastplayer.core.cast.TsProgramInfo
import com.uacastplayer.core.cast.TsProgramInfoParser
import com.uacastplayer.core.cast.TsSourceKind
import com.uacastplayer.core.io.BoundedByteReader
import com.uacastplayer.core.net.executeCancellable
import com.uacastplayer.log.AppLog
import com.uacastplayer.proxy.M3u8Rewriter
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "TsFirstSegmentDiagnostic"

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

    // This is the non-fatal boundary around three independent failure sources: URL validation,
    // network I/O and parsing arbitrary third-party transport-stream bytes. All must take the same
    // Unknown fallback, while CancellationException remains explicitly rethrown below.
    @Suppress("TooGenericExceptionCaught")
    suspend fun diagnose(streamUrl: String, httpClient: OkHttpClient): TsDiagnosticResult = try {
        val request = Request.Builder().url(streamUrl).header("Range", INITIAL_PROBE_RANGE_HEADER).build()
        val probe = httpClient.newCall(request).executeCancellable { response ->
            InitialProbe(
                contentType = response.header("Content-Type"),
                prefix = BoundedByteReader.readAtMostBytes(response.body.byteStream(), MAX_INITIAL_PROBE_BYTES),
                finalUrl = response.request.url.toString(),
            )
        }
        diagnoseResponse(probe, httpClient)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        // IPTV credentials commonly live in the URL path, so report only the failure type. Unknown
        // remains a normal routing fallback, but it is no longer an unexplained one in diagnostics.
        AppLog.d(TAG) { "Cast codec probe failed: ${error.javaClass.simpleName}" }
        TsDiagnosticResult(null, TsSourceKind.Unknown)
    }

    private suspend fun diagnoseResponse(probe: InitialProbe, httpClient: OkHttpClient): TsDiagnosticResult {
        return when (TsSourceClassifier.classify(probe.contentType, probe.prefix)) {
            TsSourceKind.RawTs -> TsDiagnosticResult(TsProgramInfoParser.parse(probe.prefix), TsSourceKind.RawTs)
            TsSourceKind.Hls -> {
                val info = diagnoseHlsSegment(probe.prefix, probe.finalUrl, httpClient)
                TsDiagnosticResult(info, TsSourceKind.Hls)
            }
            TsSourceKind.Unknown -> TsDiagnosticResult(null, TsSourceKind.Unknown)
        }
    }

    private suspend fun diagnoseHlsSegment(
        playlistPrefix: ByteArray,
        finalUrl: String,
        httpClient: OkHttpClient,
    ): TsProgramInfo? {
        val playlistText = String(playlistPrefix, Charsets.UTF_8)
        val segmentUrl = firstMediaSegmentLine(playlistText)?.let { M3u8Rewriter.resolveUrl(finalUrl, it) }
            ?: return null
        val segmentRequest = Request.Builder().url(segmentUrl).header("Range", SEGMENT_PROBE_RANGE_HEADER).build()
        return httpClient.newCall(segmentRequest).executeCancellable { segmentResponse ->
            TsProgramInfoParser.parse(
                BoundedByteReader.readAtMostBytes(segmentResponse.body.byteStream(), MAX_SEGMENT_PROBE_BYTES),
            )
        }
    }

    /** Capped independently of the Range request: a live origin can ignore Range and keep writing
     * forever, but codec sniffing only needs the leading playlist/PAT/PMT bytes. Keeping this value
     * response-free also guarantees the socket has been closed before a second HLS request starts. */
    private data class InitialProbe(
        val contentType: String?,
        val prefix: ByteArray,
        val finalUrl: String,
    )

    /** The first non-blank, non-tag line in an HLS playlist - its first actual media segment
     * reference, per the M3U8 spec (`#`-prefixed lines are tags/comments). Returns null for a
     * playlist with no segments (a master playlist, or one that's empty/malformed). */
    internal fun firstMediaSegmentLine(playlistText: String): String? =
        playlistText.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
}
