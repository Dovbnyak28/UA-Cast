package com.uacastplayer.data.cast

import com.uacastplayer.log.AppLog
import com.uacastplayer.proxy.HlsFlattenPolicy
import com.uacastplayer.proxy.HlsMediaPlaylist
import com.uacastplayer.proxy.HlsMediaPlaylistParser
import com.uacastplayer.proxy.M3u8Rewriter
import java.io.IOException
import java.io.OutputStream
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "HlsFlattenedStream"

/**
 * Replays an HLS channel to one client as a single, endless MPEG-TS response.
 *
 * A DLNA renderer plays a stream, not a manifest - see [HlsFlattenPolicy] for the field report this
 * exists because of. So for a receiver that cannot read HLS, this app does the HLS client's own job
 * on the phone: fetch the media playlist, fetch its segments in order, write their bytes into the
 * one response, refresh the playlist, repeat until the client hangs up.
 *
 * **Nothing is buffered.** Each segment is streamed straight from the origin socket to the client
 * socket in 64KB chunks, which is what keeps this affordable on the phones that need it most - see
 * `HeapBudget`, written from a device whose entire heap is 128MB. The cost is one segment's worth
 * of latency, which for live television nobody is seeking through is not a cost at all.
 *
 * The write is the pacing. A TS stream carries no rate signal of its own, so a renderer consumes it
 * as fast as the socket allows; writing only what the playlist has published means the origin's own
 * publishing rate throttles this, and the renderer's buffer does the rest. If a client stops
 * reading, the socket write blocks and this loop blocks with it, which is the desired behaviour -
 * it stops fetching upstream too.
 */
internal class HlsFlattenedStream(
    private val httpClient: OkHttpClient,
    private val playlistUrl: String,
    private val userAgent: String,
    private val referrer: String?,
) {

    /** Bytes written to the client, for the caller's logging. */
    var bytesWritten: Long = 0
        private set

    /**
     * Runs until the client disconnects, the origin stops publishing, or the playlist turns out to
     * be one that cannot be replayed this way.
     *
     * Returns false when nothing could be written at all - the caller has not sent headers yet at
     * that point, so it can still fall back to serving the manifest.
     */
    fun writeTo(output: OutputStream, onHeadersNeeded: () -> Unit): Boolean {
        val opening = resolveMediaPlaylist() ?: return false
        val url = opening.url
        var playlist: HlsMediaPlaylist? = opening.playlist
        var nextSequence = 0L
        var headersSent = false

        while (playlist != null) {
            val current = playlist
            val absolute = HlsFlattenPolicy.segmentsToServe(current, nextSequence)
                .mapNotNull { M3u8Rewriter.resolveUrl(url, it) }
            // all(), not a loop with a break: it stops on the first false, which is exactly the
            // "client hung up, stop fetching" behaviour wanted, and says so in one line.
            val clientGone = !absolute.all { segmentUrl ->
                if (!headersSent) {
                    onHeadersNeeded()
                    headersSent = true
                }
                streamSegment(segmentUrl, output)
            }
            nextSequence = HlsFlattenPolicy.sequenceAfterServing(current, nextSequence)
            playlist = when {
                clientGone -> null
                // A finished playlist will never grow, so there is nothing left to wait for. Rare
                // for a live channel and ordinary for a catch-up one.
                current.hasEndList -> {
                    AppLog.d(TAG) { "Flattened stream reached the end of a finished playlist" }
                    null
                }
                else -> {
                    Thread.sleep(HlsFlattenPolicy.refreshDelayMillis(current))
                    fetchPlaylist(url)
                }
            }
        }
        return headersSent
    }

    private data class Resolved(val url: String, val playlist: HlsMediaPlaylist)

    /**
     * The media playlist to replay, following at most one level of master playlist.
     *
     * One level, not a loop: a master pointing at a master is not a thing real streams do, and an
     * unbounded follow is a redirect loop waiting to happen against a hostile origin. The first
     * variant is taken rather than the highest bitrate - a renderer being fed a fixed stream cannot
     * adapt, and the first entry is conventionally the most compatible one.
     */
    private fun resolveMediaPlaylist(): Resolved? {
        val first = fetchPlaylist(playlistUrl) ?: return null
        return when (val verdict = HlsFlattenPolicy.verdictFor(first)) {
            HlsFlattenPolicy.Verdict.Ok -> Resolved(playlistUrl, first)
            is HlsFlattenPolicy.Verdict.Unsupported -> {
                AppLog.d(TAG) { "Not flattening this channel: ${verdict.reason}" }
                null
            }
            HlsFlattenPolicy.Verdict.NeedsVariant -> resolveVariant(first)
        }
    }

    private fun resolveVariant(master: HlsMediaPlaylist): Resolved? {
        val variantUrl = master.segmentUris.firstNotNullOfOrNull { M3u8Rewriter.resolveUrl(playlistUrl, it) }
        val variant = variantUrl?.let(::fetchPlaylist)
        val verdict = variant?.let(HlsFlattenPolicy::verdictFor)
        if (verdict != null && verdict != HlsFlattenPolicy.Verdict.Ok) {
            AppLog.d(TAG) { "Not flattening this channel's first variant: $verdict" }
        }
        return if (verdict == HlsFlattenPolicy.Verdict.Ok) Resolved(variantUrl, variant) else null
    }

    private fun fetchPlaylist(url: String): HlsMediaPlaylist? = try {
        newCall(url).execute().use { response ->
            if (!response.isSuccessful) {
                AppLog.w(TAG) { "Flattened stream playlist refresh returned HTTP ${response.code}" }
                null
            } else {
                HlsMediaPlaylistParser.parse(response.body.string())
            }
        }
    } catch (e: IOException) {
        AppLog.w(TAG) { "Flattened stream playlist refresh failed: ${e.javaClass.simpleName}" }
        null
    }

    /** False once the client has gone or the origin has - either way there is nothing left to do,
     * and the loop above stops rather than fetching a stream nobody is reading. */
    private fun streamSegment(url: String, output: OutputStream): Boolean = try {
        newCall(url).execute().use { response ->
            if (!response.isSuccessful) {
                // One refused segment is not the end of a channel: an origin that limits concurrent
                // connections rejects the occasional fetch, and the next one usually succeeds. The
                // gap is a glitch, which is better than ending the stream over it.
                AppLog.w(TAG) { "Flattened stream segment returned HTTP ${response.code}; skipping it" }
                true
            } else {
                copyToClient(response.body.byteStream(), output)
                true
            }
        }
    } catch (e: IOException) {
        AppLog.d(TAG) { "Flattened stream ended: ${e.javaClass.simpleName}" }
        false
    }

    private fun copyToClient(input: java.io.InputStream, output: OutputStream) {
        val chunk = ByteArray(CHUNK_BYTES)
        while (true) {
            val read = input.read(chunk)
            if (read == -1) break
            output.write(chunk, 0, read)
            bytesWritten += read
        }
        output.flush()
    }

    private fun newCall(url: String) = httpClient.newCall(
        Request.Builder().url(url).apply {
            header("User-Agent", userAgent)
            referrer?.let { header("Referer", it) }
        }.build(),
    )

    private companion object {
        const val CHUNK_BYTES = 64 * 1024
    }
}
