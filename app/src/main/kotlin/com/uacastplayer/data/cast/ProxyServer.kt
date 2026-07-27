package com.uacastplayer.data.cast

import com.uacastplayer.cast.CastCompatibilityPolicy
import com.uacastplayer.cast.CastCompatibilityVerdict
import com.uacastplayer.cast.TsProgramInfoParser
import com.uacastplayer.diagnostics.CastRouteKind
import com.uacastplayer.log.AppLog
import com.uacastplayer.playlist.BoundedReadResult
import com.uacastplayer.playlist.BoundedTextReader
import com.uacastplayer.proxy.M3u8Rewriter
import com.uacastplayer.proxy.MpegTsSniffer
import com.uacastplayer.proxy.PlaylistDetector
import com.uacastplayer.proxy.PlaylistUnwrapPolicy
import com.uacastplayer.proxy.RawTsRemuxActivation
import com.uacastplayer.proxy.RemuxHandoffPolicy
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val TAG = "ProxyServer"
private const val MAX_PLAYLIST_BYTES = 4 * 1024 * 1024
private const val PLAYLIST_SNIFF_BYTES = 16L
private const val TS_PROBE_BYTES = 128L * 1024
private const val HTTP_BAD_GATEWAY = 502
private const val HTTP_SERVICE_UNAVAILABLE = 503

/**
 * Local HLS proxy: rewrites and re-serves an HLS stream so a Cast receiver that can't (or won't)
 * play the origin URL directly can play it through the phone instead. Every path is
 * `/hls/<sessionToken>/<resourceId>`, `resourceId = SHA-256("type:url")`. Only GET/HEAD are
 * served; headers are capped at 16KB; the resource map is LRU-bounded to 512 entries.
 *
 * A facade over three collaborators, each with its own file: [ProxyHttpServer] (sockets, request
 * parsing, response writing), [ProxyResourceRegistry] (the resource map, tokens, idempotency) and
 * [RawTsRemuxSession] (one continuous raw-TS reader per active remux). This class owns what's left
 * once those are factored out: session identity (host/token), routing a request to either an
 * existing remux session or a fresh upstream fetch, and the active/draining remux handoff.
 */
class ProxyServer(
    private val httpClient: OkHttpClient,
    /** Fired once per top-level (channel) resource, the first time this server decides whether it
     * takes the raw-TS remux path or an ordinary rewritten-HLS passthrough - see
     * [fetchAndServeUpstreamResource]. Callers own de-duplication (see
     * `RemuxEffectivenessStore.recordProxyRouteAttemptOnce`) since a non-remuxed resource has no
     * "already decided" shortcut and is reclassified on every manifest poll. */
    private val onRouteAttempted: (resourceId: String, route: CastRouteKind) -> Unit = { _, _ -> },
) {

    private val resourceRegistry = ProxyResourceRegistry(httpClient)
    private val httpServer = ProxyHttpServer(onRequest = ::serveRequest)

    private var sessionToken: String = ""
    private var host: String = "127.0.0.1"
    private var remuxEnabled = true

    /**
     * Starts the server if it isn't already running for this exact `(sessionToken, host)` pair -
     * otherwise a no-op that reuses the running socket, port, and every resource/remux session
     * already registered on it. A cast session's channel switches all share one `sessionToken` (see
     * `cast/CastSessionRepository`), so a mid-session switch that calls this instead of [start]
     * doesn't tear down and rebind a fresh port out from under a receiver that's still fetching the
     * previous channel's URL - which is exactly what a raw [start] call does unconditionally (see
     * its own doc). Only an actual new cast session (a new token) or a host change forces a real
     * [start].
     */
    fun ensureStarted(sessionToken: String, host: String, remuxEnabled: Boolean = true): Int {
        val currentPort = httpServer.port
        val sameSession = this.sessionToken == sessionToken && this.host == host
        if (httpServer.isRunning && currentPort != null && sameSession) {
            this.remuxEnabled = remuxEnabled
            return currentPort
        }
        return start(sessionToken, host, remuxEnabled)
    }

    /** Always tears down and rebinds a fresh socket/port, discarding every resource and remux
     * session - appropriate for an actual new cast session, not a mid-session channel switch (see
     * [ensureStarted], which every caller other than this class's own tests should prefer). */
    fun start(sessionToken: String, host: String, remuxEnabled: Boolean = true): Int {
        stop()
        this.sessionToken = sessionToken
        this.host = host
        this.remuxEnabled = remuxEnabled
        return httpServer.start()
    }

    fun stop() {
        httpServer.stop()
        resourceRegistry.clearAll()
    }

    /** Called once the new channel's load is confirmed to have succeeded on the receiver (see
     * `cast/CastSessionRepository`) - lets a still-draining previous remux session be torn down
     * right away instead of waiting out the rest of [RemuxHandoffPolicy.DRAIN_TIMEOUT_MILLIS]. A
     * harmless no-op when nothing is draining. */
    fun confirmActiveSession() = resourceRegistry.confirmActiveSession()

    fun registerPlaylist(url: String, userAgent: String? = null, referrer: String? = null): String =
        resourceRegistry.registerPlaylist(url, userAgent, referrer)

    /** True once [resourceId]'s first fetch decided to engage the raw-TS remux path rather than an
     * ordinary rewritten-HLS passthrough - see [fetchAndServeUpstreamResource]/[onRouteAttempted]. */
    fun wasRemuxed(resourceId: String): Boolean = resourceRegistry.remuxSessionFor(resourceId) != null

    /** @throws IllegalStateException if called while the server isn't running - a caller asking
     * for a URL into a stopped proxy is a bug upstream, not something to paper over with a
     * malformed "http://host:null/..." URL. */
    fun buildLocalUrl(resourceId: String): String {
        val port = checkNotNull(httpServer.port) { "buildLocalUrl() called while the proxy server is not running" }
        return "http://$host:$port/hls/$sessionToken/$resourceId"
    }

    private fun buildRemuxSegmentUrl(resourceId: String, sequence: Int): String {
        val port = checkNotNull(httpServer.port) {
            "buildRemuxSegmentUrl() called while the proxy server is not running"
        }
        return "http://$host:$port/hls/$sessionToken/$resourceId/seg$sequence.ts"
    }

    /** Exposed only so tests can verify header inheritance (see [servePlaylist]) without going through a real socket. */
    internal fun resourcesForTesting(): Map<String, ResourceEntry> = resourceRegistry.snapshot()

    private fun serveRequest(request: ParsedRequest, output: OutputStream) {
        val segments = request.path.substringBefore('?').split('/').filter { it.isNotEmpty() }

        if (segments.size == 4 && segments[0] == "hls" && isSessionToken(segments[1])) {
            serveRemuxSegment(
                resourceId = segments[2],
                segmentPathPart = segments[3],
                method = request.method,
                output = output,
            )
            return
        }
        if (segments.size != 3 || segments[0] != "hls" || !isSessionToken(segments[1])) {
            httpServer.writeError(output, 404, "Not Found")
            return
        }
        val resourceId = segments[2]
        val resource = resourceRegistry.get(resourceId)
        if (resource == null) {
            httpServer.writeError(output, 404, "Not Found")
            return
        }
        servePlaylistOrMediaResource(resourceId, resource, request, output)
    }

    private fun servePlaylistOrMediaResource(
        resourceId: String,
        resource: ResourceEntry,
        request: ParsedRequest,
        output: OutputStream,
    ) {
        // A second (or third, ...) poll of a channel already being remuxed - serve the live
        // playlist as it stands right now, no upstream fetch involved. Falls back to a still-
        // draining previous session (see RemuxHandoffPolicy) so an in-flight poll for the channel
        // just switched away from doesn't 404 mid-handoff. Falling through (null) starts a fresh
        // remux session, which also stops and replaces the dead one - see remuxSessionForPlaylist
        // for exactly which sessions are served vs. restarted.
        val existingSession = resourceRegistry.remuxSessionForPlaylist(resourceId)
        if (existingSession != null) {
            writePlaylistText(existingSession.currentPlaylist(), request.method, output)
        } else {
            fetchAndServeUpstreamResource(resourceId, resource, request, output)
        }
    }

    private fun fetchAndServeUpstreamResource(
        resourceId: String,
        resource: ResourceEntry,
        request: ParsedRequest,
        output: OutputStream,
    ) {
        val upstreamRequest = Request.Builder().url(resource.originalUrl).apply {
            header("User-Agent", resource.userAgent)
            resource.referrer?.let { header("Referer", it) }
            request.headers["range"]?.let { header("Range", it) }
        }.build()
        val response = fetchUpstreamOrRespondError(upstreamRequest, resourceId, output) ?: return
        if (!response.isSuccessful) {
            AppLog.w(TAG) { "Upstream fetch for resource $resourceId returned ${response.code}" }
        }

        // The resource's registered type is only a hint used when it was discovered inside a
        // parent playlist (see servePlaylist below) - it can be wrong for extensionless/tokenized
        // URLs, so what actually gets served is decided from the real response via peekBody(),
        // which does not consume the body available to servePlaylist/servePassthrough/the remux
        // reader below. The probes themselves read from the connection and can throw on a flaky
        // upstream - the response must be closed before that exception continues on to
        // handleConnection's catch, or the half-read connection leaks.
        // Remux is only ever eligible for a TOP-LEVEL resource (registered by the sender, always
        // RESOURCE_TYPE_PLAYLIST - see registerPlaylist): it exists for endless raw-TS streams. A
        // RESOURCE_TYPE_MEDIA entry was discovered INSIDE a parent playlist, i.e. it is one of
        // that playlist's finite SEGMENTS - and a segment's bytes are, of course, MPEG-TS, so the
        // content sniff alone can't tell it from a live stream. Field-confirmed failure without
        // this: every segment fetch of an ordinary HLS channel spun up its own remux session that
        // re-downloaded the same 10s segment in a loop and answered the receiver with M3U8 text
        // where it expected segment bytes.
        val remuxEligible = resource.type == RESOURCE_TYPE_PLAYLIST
        val (isPlaylist, shouldRemux) = runCatching {
            val isPlaylist = isUpstreamPlaylist(response)
            isPlaylist to (!isPlaylist && remuxEligible && shouldRemuxUpstream(response))
        }.onFailure { runCatching { response.close() } }.getOrThrow()

        // Only the top-level (channel) resource represents a routing decision worth counting -
        // a RESOURCE_TYPE_MEDIA fetch is just serving one piece of a playlist already routed.
        if (remuxEligible) {
            onRouteAttempted(resourceId, if (shouldRemux) CastRouteKind.PROXY_REMUX else CastRouteKind.PROXY_REWRITE)
        }

        if (isPlaylist) {
            serveUpstreamPlaylist(resourceId, resource, response, request.method, output)
            return
        }

        if (shouldRemux) {
            serveRemuxedUpstream(resourceId, response, request.method, output)
        } else {
            response.use { servePassthrough(resourceId, it, request.method, output) }
        }
    }

    /** Runs an upstream fetch, turning a network-level failure (refused/reset connection, timeout
     * - never a clean HTTP response) into a logged 502 instead of letting the exception escape
     * uncaught to [ProxyHttpServer]'s generic catch, which silently drops the receiver's
     * connection with no response at all - indistinguishable, from the receiver's side, from the
     * proxy simply vanishing. Logged distinctly from a completed-but-unsuccessful response (see
     * the `!response.isSuccessful` check after every call site) since only one of the two means
     * the origin was ever actually reached - relevant for origins that reject a second connection
     * from the same account outright rather than answering it with an HTTP error. */
    private fun fetchUpstreamOrRespondError(
        request: Request,
        resourceId: String,
        output: OutputStream,
    ): Response? = try {
        httpClient.newCall(request).execute()
    } catch (e: IOException) {
        AppLog.w(TAG) { "Upstream fetch for resource $resourceId failed: ${e.javaClass.simpleName}" }
        httpServer.writeError(output, HTTP_BAD_GATEWAY, "Bad Gateway")
        null
    }

    /** Ownership of [response] passes to the remux session's background reader thread - it is
     * deliberately NOT wrapped in .use{} here, and is closed by the session itself once the reader
     * loop ends (stream EOF, stop(), or an error). A null session means the server was stopped
     * while this request's upstream fetch was in flight (see [startRemuxSession]) - the session was
     * never started, [response] is already closed, and the client gets a clean 503 rather than a
     * playlist from a server that no longer exists. */
    private fun serveRemuxedUpstream(resourceId: String, response: Response, method: String, output: OutputStream) {
        val session = resourceRegistry.startRemuxSession(resourceId, response, ::buildRemuxSegmentUrl) {
            httpServer.isRunning
        }
        if (session == null) {
            httpServer.writeError(output, HTTP_SERVICE_UNAVAILABLE, "Service Unavailable")
        } else {
            writePlaylistText(session.awaitInitialPlaylist(), method, output)
        }
    }

    private fun isUpstreamPlaylist(response: Response): Boolean =
        response.body != null &&
            PlaylistDetector.isPlaylist(response.header("Content-Type"), response.peekBody(PLAYLIST_SNIFF_BYTES).bytes())

    private fun shouldRemuxUpstream(response: Response): Boolean {
        val tsProbe = if (response.body != null) response.peekBody(TS_PROBE_BYTES).bytes() else ByteArray(0)
        val looksLikeTs = MpegTsSniffer.looksLikeMpegTs(tsProbe)
        val verdict = if (looksLikeTs) classifyTsProbe(tsProbe) else CastCompatibilityVerdict.Unknown
        return RawTsRemuxActivation.shouldActivate(
            isHlsPlaylist = false,
            looksLikeMpegTs = looksLikeTs,
            verdict = verdict,
            featureEnabled = remuxEnabled,
        )
    }

    /** [CastCompatibilityPolicy.classify]/[TsProgramInfoParser.parse] are expected to already
     * degrade to null/[CastCompatibilityVerdict.Unknown] on malformed input (see both), but this is
     * still an arbitrary third-party byte stream feeding a hand-written binary parser - any future
     * defect in that parsing must fail into "don't know, act conservatively" here, not propagate up
     * through [ProxyHttpServer]'s catch and silently drop the connection with no HTTP response at
     * all (which looks like a timeout to the receiver, not a clean error). */
    @Suppress("TooGenericExceptionCaught")
    private fun classifyTsProbe(tsProbe: ByteArray): CastCompatibilityVerdict = try {
        CastCompatibilityPolicy.classify(TsProgramInfoParser.parse(tsProbe))
    } catch (e: Exception) {
        AppLog.e(TAG, e) { "Failed to classify a probed TS stream's codecs; treating as Unknown" }
        CastCompatibilityVerdict.Unknown
    }

    private fun serveRemuxSegment(resourceId: String, segmentPathPart: String, method: String, output: OutputStream) {
        val sequence = parseSegmentSequence(segmentPathPart)
        val session = resourceRegistry.remuxSessionFor(resourceId)
        val bytes = sequence?.let { session?.segmentBytes(it) }
        if (bytes == null) {
            // Field-debugging aid (resourceId is a SHA fingerprint, never a raw URL): tells apart
            // "receiver asked for a segment that already rolled off / never existed" from
            // "receiver never asked for segments at all", which a sender-side logcat otherwise
            // can't distinguish - both just look like the receiver idling until IDLE/ERROR.
            AppLog.d(TAG) { "Remux segment miss: $segmentPathPart of $resourceId (session=${session != null})" }
            httpServer.writeError(output, 404, "Not Found")
            return
        }
        AppLog.d(TAG) { "Remux segment served: seq=$sequence ${bytes.size}B of $resourceId" }
        val headers = mapOf("Content-Type" to "video/MP2T", "Content-Length" to bytes.size.toString())
        httpServer.writeHeaders(output, 200, "OK", headers)
        // HEAD gets the same headers (Content-Length included) with no body, same as
        // writePlaylistText/servePassthrough already do for their resources.
        if (method == "GET") output.write(bytes)
        output.flush()
    }

    private fun parseSegmentSequence(segmentPathPart: String): Int? {
        if (!segmentPathPart.startsWith("seg") || !segmentPathPart.endsWith(".ts")) return null
        return segmentPathPart.removePrefix("seg").removeSuffix(".ts").toIntOrNull()
    }

    private fun writePlaylistText(text: String, method: String, output: OutputStream) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        httpServer.writeHeaders(
            output, 200, "OK",
            mapOf("Content-Type" to "application/vnd.apple.mpegurl", "Content-Length" to bytes.size.toString()),
        )
        if (method == "GET") output.write(bytes)
        output.flush()
    }

    /**
     * A playlist response from the origin is either a real HLS playlist (rewritten and served) or
     * an IPTV "wrapper" - a playlist-shaped pointer at a single endless raw-TS URL (see
     * [PlaylistUnwrapPolicy]). The wrapper case must be unwrapped HERE, at the top-level playlist
     * resource: letting the receiver discover the TS URL as a "segment" of the rewritten playlist
     * makes the proxy remux it one level down and answer that segment fetch with M3U8 *text* where
     * the receiver expects MPEG-TS *bytes* - confirmed in the field as the receiver going
     * permanently silent after its very first playlist fetch. The remux session is registered
     * under THIS resource's id, so the receiver's playlist polls and segment fetches route
     * straight back to it.
     */
    private fun serveUpstreamPlaylist(
        resourceId: String,
        resource: ResourceEntry,
        response: Response,
        method: String,
        output: OutputStream,
    ) {
        val finalUrl = response.request.url.toString()
        val text = response.use { readPlaylistText(it, output) } ?: return
        val unwrapTarget = if (remuxEnabled) PlaylistUnwrapPolicy.unwrapTarget(text, finalUrl) else null
        if (unwrapTarget == null) {
            serveRewrittenPlaylist(text, finalUrl, method, output, resource)
        } else {
            serveUnwrappedStream(resourceId, resource, unwrapTarget, method, output)
        }
    }

    private fun serveUnwrappedStream(
        resourceId: String,
        resource: ResourceEntry,
        unwrapTarget: String,
        method: String,
        output: OutputStream,
    ) {
        AppLog.d(TAG) { "Unwrapping single-stream wrapper playlist for resource $resourceId" }
        val unwrapRequest = Request.Builder().url(unwrapTarget).apply {
            header("User-Agent", resource.userAgent)
            resource.referrer?.let { header("Referer", it) }
        }.build()
        val mediaResponse = fetchUpstreamOrRespondError(unwrapRequest, resourceId, output) ?: return
        val shouldRemux = runCatching { shouldRemuxUpstream(mediaResponse) }
            .onFailure { runCatching { mediaResponse.close() } }
            .getOrThrow()
        if (shouldRemux) {
            serveRemuxedUpstream(resourceId, mediaResponse, method, output)
        } else {
            mediaResponse.use { servePassthrough(resourceId, it, method, output) }
        }
    }

    internal fun servePlaylist(response: Response, method: String, output: OutputStream, parent: ResourceEntry) {
        val text = readPlaylistText(response, output) ?: return
        serveRewrittenPlaylist(text, response.request.url.toString(), method, output, parent)
    }

    /** Null means an error response has already been written to [output]. */
    private fun readPlaylistText(response: Response, output: OutputStream): String? {
        val body = response.body
        if (body == null) {
            httpServer.writeError(output, HTTP_BAD_GATEWAY, "Bad Gateway")
            return null
        }
        return when (val bounded = BoundedTextReader.readText(body.byteStream(), MAX_PLAYLIST_BYTES)) {
            is BoundedReadResult.Success -> bounded.text
            BoundedReadResult.SizeLimitExceeded -> {
                AppLog.w(TAG) { "Upstream playlist exceeded $MAX_PLAYLIST_BYTES bytes; rejecting" }
                httpServer.writeError(output, HTTP_BAD_GATEWAY, "Bad Gateway")
                null
            }
        }
    }

    private fun serveRewrittenPlaylist(
        text: String,
        finalUrl: String,
        method: String,
        output: OutputStream,
        parent: ResourceEntry,
    ) {
        val rewritten = M3u8Rewriter.rewrite(text, finalUrl) { absoluteUrl ->
            val type = if (looksLikePlaylist(absoluteUrl)) RESOURCE_TYPE_PLAYLIST else RESOURCE_TYPE_MEDIA
            buildLocalUrl(resourceRegistry.register(type, absoluteUrl, parent.userAgent, parent.referrer))
        }
        writePlaylistText(rewritten, method, output)
    }

    private fun servePassthrough(resourceId: String, response: Response, method: String, output: OutputStream) {
        // A non-2xx here is forwarded to the receiver as-is (below) - which looks identical to a
        // genuine codec/network failure on the sender's own logs, since the receiver just goes
        // IDLE/ERROR a moment later either way. This confirmed-single-connection-per-account
        // origin is expected to reject an occasional segment fetch under rapid channel switching
        // (multiple proxy fetches racing for the one slot) - this line is what tells that apart
        // from a genuine proxy defect on the next field capture.
        // Logs resourceId (an opaque SHA fingerprint), never the upstream request URL/path - an
        // Xtream-style origin commonly carries the account username/password AS the URL path
        // segments, not just as a query param, so even a bare encodedPath here would have leaked
        // credentials into the diagnostics report.
        if (!response.isSuccessful) {
            AppLog.w(TAG) { "Passthrough upstream returned ${response.code} for resource $resourceId" }
        }
        val headers = linkedMapOf("Content-Type" to (response.header("Content-Type") ?: "application/octet-stream"))
        response.header("Content-Range")?.let { headers["Content-Range"] = it }
        response.header("Accept-Ranges")?.let { headers["Accept-Ranges"] = it }
        response.header("Content-Length")?.let { headers["Content-Length"] = it }
        httpServer.writeHeaders(output, response.code, response.message.ifEmpty { "OK" }, headers)
        if (method == "GET") {
            response.body?.byteStream()?.use { input -> input.copyTo(output) }
        }
        output.flush()
    }

    /** Constant-time so a client fishing for the session token can't learn anything from how
     * quickly a guess gets rejected. */
    private fun isSessionToken(candidate: String): Boolean =
        MessageDigest.isEqual(candidate.toByteArray(Charsets.UTF_8), sessionToken.toByteArray(Charsets.UTF_8))

    /** Only a hint for how to pre-register a URL discovered inside a playlist, before it's ever
     * been fetched - what actually gets served for it is decided from the real response by
     * [PlaylistDetector] once that request comes in (see [serveRequest]). */
    private fun looksLikePlaylist(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".m3u")
    }
}
