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
    /**
     * Checked either side of every wait, and the loop stops the moment it says no.
     *
     * The hazard is the one `ProxyResourceRegistry.startRemuxSession` already names for the remux
     * path - "a background thread reading the upstream live stream indefinitely with no owner left
     * to ever stop it" - and this loop is a second place it could happen. Nothing else here would
     * notice a session ending: the client socket is only discovered to be gone by a *write*
     * failing, and this loop spends most of its time not writing.
     *
     * **Stated precisely, because it was measured rather than assumed**: `ProxyServer.stop()` tears
     * the pool down with `shutdownNow()`, whose interrupt makes the next `Thread.sleep` here throw
     * on its own - so the loop does already end shortly after a stop without this. What this adds
     * is not depending on that. An interrupt flag survives only as long as nothing between here and
     * the sleep swallows it, and everything between here and the sleep is OkHttp and a socket; a
     * library that catches `InterruptedException` without restoring the flag would turn a bounded
     * wait into an unbounded one. This check asks the owner directly, and it also ends the loop at
     * the first opportunity rather than after however long the segment fetch in progress takes.
     */
    private val isRunning: () -> Boolean,
) {

    /** Bytes written to the client, for the caller's logging. */
    var bytesWritten: Long = 0
        private set

    /**
     * Whether this channel can be replayed as a stream at all, without replaying any of it.
     *
     * For a HEAD, which asks what a resource is rather than for it. Answering "MPEG-TS" and then
     * serving a manifest on the GET is a mismatch a DLNA renderer does not recover from, so the
     * question has to be actually asked - one playlist fetch, two if it is a master.
     *
     * It is not a promise. A channel can pass this and still serve nothing, because whether the
     * segments themselves are reachable is only discovered by reaching for them (see [writeTo]).
     * What it rules out is the deterministic half: encrypted segments, an fMP4 init, an empty or
     * unreadable playlist, a master whose first variant is any of those.
     */
    fun canFlatten(): Boolean = resolveMediaPlaylist() != null

    /**
     * Runs until the client disconnects, the origin stops publishing, or the playlist turns out to
     * be one that cannot be replayed this way.
     *
     * Returns false when nothing could be written at all - the caller has not sent headers yet at
     * that point, so it can still fall back to serving the manifest.
     */
    fun writeTo(output: OutputStream, onHeadersNeeded: () -> Unit): Boolean {
        val opening = resolveMediaPlaylist() ?: return false
        // Two URLs, not one, and they are only the same when nothing redirected. [requestUrl] is what
        // gets re-fetched every refresh - the original, so a rotating token or a rebalanced edge is
        // re-followed each time rather than pinned to whichever CDN answered first. [base] is what
        // that fetch actually resolved to, and is the only correct thing to resolve a relative
        // segment URI against. Every other playlist path in this app already works this way; see
        // `finalUrl` in [ProxyServer.servePlaylist].
        val requestUrl = opening.requestUrl
        var base = opening.base
        var playlist: HlsMediaPlaylist? = opening.playlist
        var nextSequence = 0L
        var headersSent = false

        while (playlist != null) {
            val current = playlist
            val absolute = HlsFlattenPolicy.segmentsToServe(current, nextSequence)
                .mapNotNull { M3u8Rewriter.resolveUrl(base, it) }
            // all(), not a loop with a break: it stops on the first false, which is exactly the
            // "client hung up, stop fetching" behaviour wanted, and says so in one line.
            val clientGone = !absolute.all { segmentUrl ->
                streamSegment(segmentUrl, output) {
                    if (!headersSent) {
                        onHeadersNeeded()
                        headersSent = true
                    }
                }
            }
            nextSequence = HlsFlattenPolicy.sequenceAfterServing(current, nextSequence)
            // Nothing written on a whole pass over the playlist means this route cannot serve this
            // channel, and the caller is still free to fall back to the manifest - but only for as
            // long as no headers have gone out. Without this the loop would keep polling a playlist
            // whose every segment is refused (see [streamSegment]: a refusal is a glitch to skip,
            // not an end) and the renderer would hold an endless, empty, perfectly valid-looking
            // response. A stream that plays nothing is worse than a manifest that might.
            // `break` rather than a return, and they are the same thing here: `headersSent` is what
            // this function returns, and it is false.
            if (!headersSent) {
                AppLog.w(TAG) { "Flattened stream served no bytes on its first pass; leaving it to the manifest" }
                break
            }
            playlist = when {
                clientGone -> null
                // A finished playlist will never grow, so there is nothing left to wait for. Rare
                // for a live channel and ordinary for a catch-up one.
                current.hasEndList -> {
                    AppLog.d(TAG) { "Flattened stream reached the end of a finished playlist" }
                    null
                }
                // Checked *before* the sleep as well as after it: the cheap case is a session that
                // was already over when this iteration began, and waiting out a target duration to
                // discover that is exactly the delay this exists to avoid.
                !isRunning() -> null
                !sleepInterruptibly(HlsFlattenPolicy.refreshDelayMillis(current)) -> null
                !isRunning() -> null
                // The base moves with the refresh: a redirect can land on a different edge from one
                // refresh to the next, and the segment URIs in the body that came back are relative
                // to where that body came from.
                else -> fetchPlaylist(requestUrl)?.also { base = it.base }?.playlist
            }
        }
        return headersSent
    }

    /**
     * A fetched playlist and where it came from.
     *
     * [requestUrl] is what was asked for; [base] is where the response finally came from after any
     * redirects, and the two differ constantly on IPTV origins - an Xtream-style `.m3u8` typically
     * answers 302 to a tokenised CDN path, and a master playlist's variants almost always do. Every
     * segment URI in the body is relative to [base]; resolving them against [requestUrl] instead
     * builds URLs on the wrong host or the wrong directory, and the origin answers 404 to all of
     * them. [streamSegment] treats a refused segment as a glitch worth skipping, so the failure is
     * not an error at all - it is a stream that connects, stays connected, and plays nothing.
     */
    private data class Fetched(val requestUrl: String, val base: String, val playlist: HlsMediaPlaylist)

    /**
     * The media playlist to replay, following at most one level of master playlist.
     *
     * One level, not a loop: a master pointing at a master is not a thing real streams do, and an
     * unbounded follow is a redirect loop waiting to happen against a hostile origin. The first
     * variant is taken rather than the highest bitrate - a renderer being fed a fixed stream cannot
     * adapt, and the first entry is conventionally the most compatible one.
     */
    private fun resolveMediaPlaylist(): Fetched? {
        val first = fetchPlaylist(playlistUrl) ?: return null
        return when (val verdict = HlsFlattenPolicy.verdictFor(first.playlist)) {
            HlsFlattenPolicy.Verdict.Ok -> first
            is HlsFlattenPolicy.Verdict.Unsupported -> {
                AppLog.d(TAG) { "Not flattening this channel: ${verdict.reason}" }
                null
            }
            HlsFlattenPolicy.Verdict.NeedsVariant -> resolveVariant(first)
        }
    }

    private fun resolveVariant(master: Fetched): Fetched? {
        // Against the master's own base, for the same reason the segments are: a master that
        // redirected lists its variants relative to where it landed.
        val variantUrl = master.playlist.segmentUris.firstNotNullOfOrNull { M3u8Rewriter.resolveUrl(master.base, it) }
        val variant = variantUrl?.let(::fetchPlaylist)
        val verdict = variant?.let { HlsFlattenPolicy.verdictFor(it.playlist) }
        if (verdict != null && verdict != HlsFlattenPolicy.Verdict.Ok) {
            AppLog.d(TAG) { "Not flattening this channel's first variant: $verdict" }
        }
        return if (verdict == HlsFlattenPolicy.Verdict.Ok) variant else null
    }

    private fun fetchPlaylist(url: String): Fetched? = try {
        newCall(url).execute().use { response ->
            if (!response.isSuccessful) {
                AppLog.w(TAG) { "Flattened stream playlist refresh returned HTTP ${response.code}" }
                null
            } else {
                // response.request, not the url asked for: OkHttp follows redirects itself and this
                // is the request that finally answered.
                Fetched(url, response.request.url.toString(), HlsMediaPlaylistParser.parse(response.body.string()))
            }
        }
    } catch (e: IOException) {
        AppLog.w(TAG) { "Flattened stream playlist refresh failed: ${e.javaClass.simpleName}" }
        null
    }

    /**
     * False once the client has gone or the origin has - either way there is nothing left to do,
     * and the loop above stops rather than fetching a stream nobody is reading.
     *
     * [onBytesComing] fires once the origin has answered and the copy is about to start, never
     * before. That ordering is the whole point of it: it is what commits the response to being a
     * flattened stream, and committing on a segment that then turns out to 404 leaves the renderer
     * holding an endless empty body with no way back to the manifest.
     */
    private fun streamSegment(url: String, output: OutputStream, onBytesComing: () -> Unit): Boolean = try {
        newCall(url).execute().use { response ->
            if (!response.isSuccessful) {
                // One refused segment is not the end of a channel: an origin that limits concurrent
                // connections rejects the occasional fetch, and the next one usually succeeds. The
                // gap is a glitch, which is better than ending the stream over it.
                AppLog.w(TAG) { "Flattened stream segment returned HTTP ${response.code}; skipping it" }
                true
            } else {
                onBytesComing()
                copyToClient(response.body.byteStream(), output)
                true
            }
        }
    } catch (e: IOException) {
        AppLog.d(TAG) { "Flattened stream ended: ${e.javaClass.simpleName}" }
        false
    }

    /**
     * False when the wait was interrupted, which is `shutdownNow()` tearing the pool down - the one
     * signal that reaches a sleeping thread at all.
     *
     * The flag is restored rather than swallowed: this runs on a pooled thread that goes back to
     * serving other connections, and a cleared interrupt would leave the next thing it does unaware
     * the pool is shutting down.
     */
    private fun sleepInterruptibly(millis: Long): Boolean = try {
        Thread.sleep(millis)
        true
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        AppLog.d(TAG) { "Flattened stream interrupted while waiting for the next playlist" }
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
