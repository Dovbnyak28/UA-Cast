package com.uacastplayer.data.cast

import com.uacastplayer.proxy.M3u8Rewriter
import com.uacastplayer.proxy.MpegTsSniffer
import com.uacastplayer.proxy.TsCompatibilityPolicy
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches a channel's first HLS segment and sniffs it for known-unsupported codecs (MPEG-2
 * video, MP2 audio), so a receiver that will never be able to play it doesn't have to wait out
 * the full watchdog timeout to find out.
 */
object TsFirstSegmentDiagnostic {

    private const val SEGMENT_PROBE_BYTES = "bytes=0-65535"

    fun isKnownUnsupported(streamUrl: String, httpClient: OkHttpClient): Boolean {
        return try {
            httpClient.newCall(Request.Builder().url(streamUrl).build()).execute().use { response ->
                val text = response.body?.string() ?: return false
                val finalUrl = response.request.url.toString()
                val firstSegmentRef = text.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                    ?: return false
                val segmentUrl = M3u8Rewriter.resolveUrl(finalUrl, firstSegmentRef) ?: return false

                val segmentRequest = Request.Builder().url(segmentUrl).header("Range", SEGMENT_PROBE_BYTES).build()
                httpClient.newCall(segmentRequest).execute().use { segmentResponse ->
                    val bytes = segmentResponse.body?.bytes() ?: return false
                    val info = MpegTsSniffer.sniff(bytes) ?: return false
                    TsCompatibilityPolicy.isKnownUnsupported(info)
                }
            }
        } catch (_: Exception) {
            false
        }
    }
}
