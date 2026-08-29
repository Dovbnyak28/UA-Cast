package com.uacastplayer.data.cast

import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.core.cast.CastRouteKind
import com.uacastplayer.log.AppLog
import com.uacastplayer.proxy.M3u8Rewriter
import com.uacastplayer.proxy.PlaylistUnwrapPolicy
import com.uacastplayer.proxy.RemuxHandoffPolicy
import java.io.IOException
import java.io.OutputStream
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val TAG = "ProxyServer"
private const val HTTP_BAD_GATEWAY = 502
private const val HTTP_SERVICE_UNAVAILABLE = 503
private const val HTTP_OK = 200
private const val HTTP_NOT_FOUND = 404

/**
 * Local HLS proxy: rewrites and re-serves an HLS stream so a Cast receiver that can't (or won't)
 * play the origin URL directly can play it through the phone instead. Every path is
 * `/hls/<sessionToken>/<resourceId>`, `resourceId = SHA-256("type:url")`. Only GET/HEAD are
 * served; headers are capped at 16KB; the resource map is LRU-bounded to 512 entries.
 *
 * A routing facade over four collaborators, each with its own file: [ProxyHttpServer] (sockets and
 * request parsing), [ProxyResponseServing] (headers, bodies, progress accounting and traffic
 * rollups), [ProxyResourceRegistry] (the resource map, tokens and idempotency), and
 * [RawTsRemuxSession] (one continuous raw-TS reader per active remux). This class owns session
 * identity and decides which upstream/remux route produces a response; it does not write response
 * bytes itself.
 */
class ProxyServer(
    private val httpClient: OkHttpClient,
    /** Injected so the serve rollup's windows can be driven by a test rather than by the clock -
     * the same seam [com.uacastplayer.app.UpdateController] uses. Declared before
     * [onRouteAttempted] so that stays the trailing parameter its call site passes as a lambda. */
    private val now: () -> Long = System::currentTimeMillis,
    /** Fired once per top-level (channel) resource, the first time this server decides whether it
     * takes the raw-TS remux path or an ordinary rewritten-HLS passthrough - see
     * [fetchAndServeUpstreamResource]. Callers own de-duplication (see
     * `RemuxEffectivenessStore.recordProxyRouteAttemptOnce`) since a non-remuxed resource has no
     * "already decided" shortcut and is reclassified on every manifest poll. */
    private val onRouteAttempted: (resourceId: String, route: CastRouteKind) -> Unit = { _, _ -> },
) {

    private val resourceRegistry = ProxyResourceRegistry(httpClient)
    private val httpServer = ProxyHttpServer(
        onRequest = ::serveRequest,
        isRequestAuthorized = ::isAuthorizedRequest,
    )
    private val responseServing = ProxyResponseServing(httpServer, now)
    private val flattenedStreamsLock = Any()
    private val flattenedStreams = mutableSetOf<HlsFlattenedStream>()
    private var acceptingFlattenedStreams = false
    private val upstreamCallsLock = Any()
    private val upstreamCalls = mutableSetOf<Call>()
    private var acceptingUpstreamCalls = false

    // Written from the main thread (start/ensureStarted, reached from CastSessionRepository on
    // Dispatchers.Main.immediate) and read from ProxyHttpServer's pool threads - isSessionToken,
    // shouldRemuxUpstream, buildLocalUrl. @Volatile for the same reason ProxyHttpServer marks its
    // own boundPort/running, which this class had simply been missing.
    //
    // Starting the server happens to publish the first two safely on its own, since httpServer
    // .start() creates the pool *after* they are assigned and thread creation carries a
    // happens-before edge. ensureStarted's already-running fast path has no such edge: it writes
    // remuxEnabled while the pool is live, so without this the setting could go unseen by the
    // threads serving the current session.
    @Volatile private var sessionToken: String = ""
    @Volatile private var host: String = "127.0.0.1"
    @Volatile private var remuxEnabled = true

    /**
     * Whether an m3u8 that is really a pointer at one endless stream should be followed to that
     * stream (see [PlaylistUnwrapPolicy]) rather than rewritten as a playlist.
     *
     * Separate from [remuxEnabled], which it used to be read off. They answer different questions:
     * unwrapping is about *what the origin actually is* - a playlist-shaped pointer is not a
     * playlist - while remuxing is about what one particular receiver can swallow. Tying them
     * together meant a receiver that wants no remux also got no unwrap, and so was handed a
     * manifest whose single "segment" is an endless raw TS.
     *
     * That combination is exactly what a DLNA renderer must never be given, and unwrapping is what
     * turns it into the one thing such a renderer plays best: a continuous MPEG-TS, passed
     * through untouched.
     */
    @Volatile private var unwrapWrapperPlaylists = true

    /**
     * Whether an HLS channel should be replayed to the client as one continuous MPEG-TS response
     * instead of being served as a manifest - see [HlsFlattenedStream].
     *
     * Off for Chromecast, which reads HLS natively and would only lose adaptive switching by it.
     * On for DLNA, where a manifest is the one thing most renderers cannot read at all, and where
     * the alternative is what a Hisense VIDAA answered with "Archivo no compatible".
     */
    @Volatile private var flattenHlsToStream = false

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
    @Synchronized
    fun ensureStarted(
        sessionToken: String,
        host: String,
        remuxEnabled: Boolean = true,
        unwrapWrapperPlaylists: Boolean = true,
        flattenHlsToStream: Boolean = false,
    ): Int {
        val currentPort = httpServer.port
        val sameSession = this.sessionToken == sessionToken && this.host == host
        if (httpServer.isRunning && currentPort != null && sameSession) {
            this.remuxEnabled = remuxEnabled
            this.unwrapWrapperPlaylists = unwrapWrapperPlaylists
            this.flattenHlsToStream = flattenHlsToStream
            return currentPort
        }
        return start(sessionToken, host, remuxEnabled, unwrapWrapperPlaylists, flattenHlsToStream)
    }

    /** Always tears down and rebinds a fresh socket/port, discarding every resource and remux
     * session - appropriate for an actual new cast session, not a mid-session channel switch (see
     * [ensureStarted], which every caller other than this class's own tests should prefer). */
    @Synchronized
    fun start(
        sessionToken: String,
        host: String,
        remuxEnabled: Boolean = true,
        unwrapWrapperPlaylists: Boolean = true,
        flattenHlsToStream: Boolean = false,
    ): Int {
        stop()
        this.sessionToken = sessionToken
        this.host = host
        this.remuxEnabled = remuxEnabled
        this.unwrapWrapperPlaylists = unwrapWrapperPlaylists
        this.flattenHlsToStream = flattenHlsToStream
        val port = httpServer.start()
        synchronized(flattenedStreamsLock) { acceptingFlattenedStreams = true }
        synchronized(upstreamCallsLock) { acceptingUpstreamCalls = true }
        return port
    }

    @Synchronized
    fun stop() {
        val callsToCancel = synchronized(upstreamCallsLock) {
            acceptingUpstreamCalls = false
            upstreamCalls.toList().also { upstreamCalls.clear() }
        }
        val streamsToStop = synchronized(flattenedStreamsLock) {
            acceptingFlattenedStreams = false
            flattenedStreams.toList().also { flattenedStreams.clear() }
        }
        // First, while their worker threads and client sockets still exist: a flattened stream can
        // be blocked on the upstream socket, which neither shutdownNow() nor closing only the
        // receiver side is guaranteed to wake.
        callsToCancel.forEach(Call::cancel)
        streamsToStop.forEach(HlsFlattenedStream::stop)
        httpServer.stop()
        resourceRegistry.clearAll()
        // Before the counters go quiet, not after: the window a session ends in is the one a reader
        // wants most, and without this it would be the one window that never got written down.
        responseServing.flushRollup()
        val httpMetrics = httpServer.metricsSnapshot()
        val rejected = httpMetrics.rejectedPerIp + httpMetrics.rejectedAdmissionQueue +
            httpMetrics.rejectedResponseQueue + httpMetrics.unauthorizedRequests
        if (rejected > 0 || httpMetrics.malformedRequests > 0) {
            AppLog.d(TAG) {
                "proxy admission: accepted=${httpMetrics.acceptedConnections}, rejected=$rejected, " +
                    "malformed=${httpMetrics.malformedRequests}"
            }
        }
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

    /**
     * Total response-body bytes handed to the receiver so far - playlists, remuxed segments and
     * passthrough media alike. Counted per chunk as it is written rather than per completed
     * response, so a receiver that is halfway through a 6MB segment still reads as progress; that
     * is the whole point, since the case worth telling apart from a stall is precisely a transfer
     * that has not finished yet. Read by [com.uacastplayer.cast.CastStallWatchdogPolicy] to tell a
     * load that is merely slow from one that is genuinely stuck.
     */
    fun bytesServedToReceiver(): Long = responseServing.bytesServedToReceiver()

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

    /** Exposed only so tests can verify header inheritance (see [servePlaylist]) without going
     * through a real socket. */
    internal fun resourcesForTesting(): Map<String, ResourceEntry> = resourceRegistry.snapshot()

    private fun serveRequest(request: ParsedRequest, output: OutputStream) {
        when (val target = ProxyRequestTarget.parse(request.path, sessionToken)) {
            null -> {
                responseServing.writeError(output, HTTP_NOT_FOUND, "Not Found")
                return
            }
            is ProxyRequestTarget.RemuxSegment -> {
                serveRemuxSegment(
                    resourceId = target.resourceId,
                    segmentPathPart = target.segmentPathPart,
                    method = request.method,
                    output = output,
                )
                return
            }
            is ProxyRequestTarget.Resource -> serveResource(target.resourceId, request, output)
        }
    }

    private fun serveResource(resourceId: String, request: ParsedRequest, output: OutputStream) {
        val resource = resourceRegistry.get(resourceId)
        if (resource == null) {
            AppLog.d(TAG) { "Unknown resource requested: $resourceId" }
            responseServing.writeError(output, HTTP_NOT_FOUND, "Not Found")
            return
        }
        servePlaylistOrMediaResource(resourceId, resource, request, output)
    }

    /** Checked in [ProxyHttpServer]'s admission pool before a request can consume a serving worker. */
    private fun isAuthorizedRequest(request: ParsedRequest): Boolean =
        ProxyRequestTarget.parse(request.path, sessionToken) != null

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
            responseServing.writePlaylistText(existingSession.currentPlaylist(), request.method, output)
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
        val upstreamRequest = buildUpstreamRequest(resourceId, resource, request, output) ?: return
        val upstream = fetchUpstreamOrRespondError(upstreamRequest, resourceId, output) ?: return
        try {
            routeUpstreamResponse(resourceId, resource, request, output, upstream.response)
        } finally {
            releaseUpstreamCall(upstream.call)
        }
    }

    /** Provider-controlled URL/header values can be malformed before an OkHttp Call exists. Keep
     * that failure inside the same 502 boundary as a socket-level upstream failure. */
    private fun buildUpstreamRequest(
        resourceId: String,
        resource: ResourceEntry,
        request: ParsedRequest,
        output: OutputStream,
    ): Request? = try {
        Request.Builder().url(resource.originalUrl).apply {
            header("User-Agent", resource.userAgent)
            resource.referrer?.let { header("Referer", it) }
            request.headers["range"]?.let { header("Range", it) }
        }.build()
    } catch (e: IllegalArgumentException) {
        AppLog.w(TAG) { "Upstream request for resource $resourceId is invalid: ${e.javaClass.simpleName}" }
        responseServing.writeError(output, HTTP_BAD_GATEWAY, "Bad Gateway")
        null
    }

    private fun routeUpstreamResponse(
        resourceId: String,
        resource: ResourceEntry,
        request: ParsedRequest,
        output: OutputStream,
        response: Response,
    ) {
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
        // `response` belongs to this function from here until one of the serve paths takes it on,
        // and every escape in between has to close it. The sniffs inside decideRoute were already
        // guarded individually, but the guard stopped there: onRouteAttempted writes to a
        // disk-backed store on this same connection-handling thread, and an IOException out of it
        // would have left the upstream connection open with nobody holding a reference - the
        // OkHttp "connection was leaked" warning against the origin host.
        var handedOff = false
        try {
            val decision = ProxyRouteSelector.select(resource, response, remuxEnabled)
            decision.attemptedRoute?.let { route -> onRouteAttempted(resourceId, route) }
            handedOff = true
            // Each branch owns `response` from here: the playlist and passthrough paths close it
            // through their own use {}, and the remux path passes it to the session's reader.
            when (decision.route) {
                UpstreamRoute.PLAYLIST ->
                    serveUpstreamPlaylist(resourceId, resource, response, request.method, output)
                UpstreamRoute.REMUX ->
                    serveRemuxedUpstream(resourceId, response, request.method, output)
                UpstreamRoute.PASSTHROUGH ->
                    response.use { responseServing.servePassthrough(resourceId, it, request.method, output) }
            }
        } finally {
            if (!handedOff) runCatchingNonFatal { response.close() }
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
    ): TrackedUpstreamResponse? {
        val call = httpClient.newCall(request)
        if (!trackUpstreamCall(call)) {
            call.cancel()
            responseServing.writeError(output, HTTP_SERVICE_UNAVAILABLE, "Service Unavailable")
            return null
        }
        return try {
            TrackedUpstreamResponse(call, call.execute())
        } catch (e: IOException) {
            releaseUpstreamCall(call)
            AppLog.w(TAG) { "Upstream fetch for resource $resourceId failed: ${e.javaClass.simpleName}" }
            responseServing.writeError(output, HTTP_BAD_GATEWAY, "Bad Gateway")
            null
        }
    }

    private fun trackUpstreamCall(call: Call): Boolean = synchronized(upstreamCallsLock) {
        if (acceptingUpstreamCalls && httpServer.isRunning) upstreamCalls.add(call) else false
    }

    private fun releaseUpstreamCall(call: Call) {
        synchronized(upstreamCallsLock) { upstreamCalls.remove(call) }
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
            responseServing.writeError(output, HTTP_SERVICE_UNAVAILABLE, "Service Unavailable")
        } else {
            responseServing.writePlaylistText(session.awaitInitialPlaylist(), method, output)
        }
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
            responseServing.writeError(output, HTTP_NOT_FOUND, "Not Found")
            return
        }
        AppLog.d(TAG) { "Remux segment served: seq=$sequence ${bytes.size}B of $resourceId" }
        responseServing.writeRemuxSegment(bytes, method, output)
    }

    private fun parseSegmentSequence(segmentPathPart: String): Int? {
        if (!segmentPathPart.startsWith("seg") || !segmentPathPart.endsWith(".ts")) return null
        return segmentPathPart.removePrefix("seg").removeSuffix(".ts").toIntOrNull()
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
        val text = response.use { responseServing.readPlaylistText(it, output) } ?: return
        val unwrapTarget =
            if (unwrapWrapperPlaylists) PlaylistUnwrapPolicy.unwrapTarget(text, finalUrl) else null
        when {
            unwrapTarget != null -> serveUnwrappedStream(resourceId, resource, unwrapTarget, method, output)
            // Tried before the rewrite, and falls through to it on any refusal - a receiver that
            // can read a manifest is no worse off than before, and one that cannot was getting
            // nothing playable at all. See HlsFlattenedStream.
            flattenHlsToStream && serveFlattenedHls(resourceId, resource, finalUrl, method, output) -> Unit
            // Tried before the rewrite, and falls through to it on any refusal - a receiver that
            // can read a manifest is no worse off than before, and one that cannot was getting
            // nothing playable at all. See HlsFlattenedStream.
            else -> serveRewrittenPlaylist(text, finalUrl, method, output, resource)
        }
    }

    /**
     * Replays an HLS channel as one continuous MPEG-TS response for a receiver that cannot read a
     * manifest.
     *
     * Returns false when nothing was written, which is the whole reason the headers are deferred
     * into a callback: until the first segment's bytes exist there is still a working fallback,
     * and a response that has already claimed `video/mp2t` cannot take it. Encrypted segments,
     * fragmented MP4 and an unreachable playlist all land here.
     *
     * A HEAD is answered with the headers alone. A DLNA renderer commonly HEADs a url before
     * committing to it, and replaying a live channel to answer that would fetch media nobody is
     * going to watch.
     */
    private fun serveFlattenedHls(
        resourceId: String,
        resource: ResourceEntry,
        playlistUrl: String,
        method: String,
        output: OutputStream,
    ): Boolean {
        val headers = mapOf("Content-Type" to "video/mp2t")
        // Captured, then compared: the server being up is not enough on its own, because start()
        // stops and rebinds for a genuinely new session and would read as running again. A loop
        // left over from the previous session has to stop, and its token is what says it is left
        // over.
        val servingSession = sessionToken
        val stream = HlsFlattenedStream(
            httpClient = httpClient,
            playlistUrl = playlistUrl,
            userAgent = resource.userAgent,
            referrer = resource.referrer,
            isRunning = { httpServer.isRunning && sessionToken == servingSession },
        )
        if (!trackFlattenedStream(stream, servingSession)) return false
        return try {
            val wrote = if (method != "GET") {
                announceFlattened(stream, output, headers)
            } else {
                stream.writeTo(responseServing.countedBody(output)) {
                    responseServing.writeHeaders(output, HTTP_OK, "OK", headers)
                }
            }
            if (method == "GET" && wrote) {
                AppLog.d(TAG) { "Flattened HLS stream ended for $resourceId after ${stream.bytesWritten}B" }
            }
            wrote
        } finally {
            stream.stop()
            synchronized(flattenedStreamsLock) {
                flattenedStreams.remove(stream)
                Unit // cleanup block deliberately has no Boolean result for the surrounding finally
            }
        }
    }

    /** Registers under the same lock stop() uses to close admission. A handler racing session
     * teardown therefore either joins the stop snapshot or is rejected before opening upstream. */
    private fun trackFlattenedStream(stream: HlsFlattenedStream, servingSession: String): Boolean =
        synchronized(flattenedStreamsLock) {
            if (acceptingFlattenedStreams && httpServer.isRunning && sessionToken == servingSession) {
                flattenedStreams.add(stream)
            } else {
                false
            }
        }

    /**
     * Answers a HEAD, which asks what a resource is rather than for it.
     *
     * This used to answer "video/mp2t" for every channel without checking. Falling back to the
     * manifest is routine on this path - encrypted segments, an fMP4 init, a variant that cannot be
     * read - so a renderer that HEADs first was told MPEG-TS and then handed an M3U8 on the GET,
     * which is the exact mismatch [serveUpstreamPlaylist]'s own KDoc records going into the field:
     * "the receiver going permanently silent after its very first playlist fetch".
     *
     * False here falls through to the manifest exactly as it does for a GET, so the two answers
     * agree. The cost is one upstream fetch on a request whose whole purpose is to ask this
     * question.
     */
    private fun announceFlattened(
        stream: HlsFlattenedStream,
        output: OutputStream,
        headers: Map<String, String>,
    ): Boolean {
        if (!stream.canFlatten()) return false
        responseServing.writeHeaders(output, HTTP_OK, "OK", headers)
        return true
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
        val upstream = fetchUpstreamOrRespondError(unwrapRequest, resourceId, output) ?: return
        try {
            val mediaResponse = upstream.response
            val shouldRemux = runCatchingNonFatal { ProxyRouteSelector.shouldRemuxRaw(mediaResponse, remuxEnabled) }
                .onFailure { runCatchingNonFatal { mediaResponse.close() } }
                .getOrThrow()
            if (shouldRemux) {
                serveRemuxedUpstream(resourceId, mediaResponse, method, output)
            } else {
                mediaResponse.use { responseServing.servePassthrough(resourceId, it, method, output) }
            }
        } finally {
            releaseUpstreamCall(upstream.call)
        }
    }

    internal fun servePlaylist(response: Response, method: String, output: OutputStream, parent: ResourceEntry) {
        val text = responseServing.readPlaylistText(response, output) ?: return
        serveRewrittenPlaylist(text, response.request.url.toString(), method, output, parent)
    }

    private fun serveRewrittenPlaylist(
        text: String,
        finalUrl: String,
        method: String,
        output: OutputStream,
        parent: ResourceEntry,
    ) {
        var rewrittenCount = 0
        val rewritten = M3u8Rewriter.rewrite(text, finalUrl) { absoluteUrl ->
            rewrittenCount++
            val type = if (looksLikePlaylist(absoluteUrl)) RESOURCE_TYPE_PLAYLIST else RESOURCE_TYPE_MEDIA
            buildLocalUrl(resourceRegistry.register(type, absoluteUrl, parent.userAgent, parent.referrer))
        }
        // The remux path logs every playlist poll and segment; this one logged nothing at all, so an
        // ordinary HLS cast - the common case - left no trace of whether the receiver ever fetched
        // anything. A field capture of a failing cast was unreadable for exactly that reason.
        //
        // It is now rolled up rather than written per poll - see
        // [com.uacastplayer.proxy.ProxyServeRollup] for the two reports where this one sentence,
        // every four seconds, had emptied the log of everything else. A playlist that rewrote
        // nothing keeps its own line: the receiver is being handed a manifest with no urls it can
        // fetch, which is a failure, not traffic.
        responseServing.writeRewrittenPlaylist(rewritten, rewrittenCount, method, output)
    }

    /** Only a hint for how to pre-register a URL discovered inside a playlist, before it's ever
     * been fetched - what actually gets served for it is decided from the real response by
     * [PlaylistDetector] once that request comes in (see [serveRequest]). */
    private fun looksLikePlaylist(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".m3u")
    }
}

private data class TrackedUpstreamResponse(val call: Call, val response: Response)
