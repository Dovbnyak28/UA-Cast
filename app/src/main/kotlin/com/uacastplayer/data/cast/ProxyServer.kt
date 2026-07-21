package com.uacastplayer.data.cast

import com.uacastplayer.cast.CastCompatibilityPolicy
import com.uacastplayer.cast.CastCompatibilityVerdict
import com.uacastplayer.cast.TsProgramInfoParser
import com.uacastplayer.core.net.HttpDefaults
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.log.AppLog
import com.uacastplayer.playlist.BoundedReadResult
import com.uacastplayer.playlist.BoundedTextReader
import com.uacastplayer.proxy.LiveHlsPlaylistBuilder
import com.uacastplayer.proxy.M3u8Rewriter
import com.uacastplayer.proxy.MpegTsSniffer
import com.uacastplayer.proxy.PlaylistDetector
import com.uacastplayer.proxy.PlaylistUnwrapPolicy
import com.uacastplayer.proxy.RawTsRemuxActivation
import com.uacastplayer.proxy.RemuxHandoffPolicy
import com.uacastplayer.proxy.RemuxReconnectPolicy
import com.uacastplayer.proxy.RemuxSegmentBuffer
import com.uacastplayer.proxy.TsPacketSegmenter
import com.uacastplayer.proxy.TsSegment
import com.uacastplayer.proxy.TsSegmenter
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val TAG = "ProxyServer"
private const val MAX_HEADER_BYTES = 16 * 1024
private const val MAX_RESOURCES = 512
private const val MAX_PLAYLIST_BYTES = 4 * 1024 * 1024
private const val THREAD_POOL_SIZE = 6
private const val PLAYLIST_SNIFF_BYTES = 16L
private const val TS_PROBE_BYTES = 128L * 1024
private const val TS_PACKET_SIZE = 188
private const val TS_SYNC_BYTE = 0x47
private const val REMUX_TARGET_SEGMENT_SECONDS = 5
// Long enough to cover a slow origin's first startup segment (a keyframe-less broadcast only cuts
// at the startup force-cut ceiling, ~4s of stream time, plus connect/read overhead) - an EMPTY
// initial playlist makes the receiver give up outright, which is strictly worse than it waiting a
// few extra seconds on its first manifest request.
private const val REMUX_INITIAL_PLAYLIST_WAIT_MILLIS = 8_000L
private const val REMUX_POLL_INTERVAL_MILLIS = 100L
private const val REMUX_READ_CHUNK_BYTES = 64 * 1024
private const val REMUX_READER_JOIN_TIMEOUT_MILLIS = 1_000L
private const val HTTP_SERVICE_UNAVAILABLE = 503
private const val HTTP_NO_CONTENT = 204
private const val CORS_MAX_AGE_SECONDS = "86400"

internal const val RESOURCE_TYPE_PLAYLIST = "playlist"
internal const val RESOURCE_TYPE_MEDIA = "media"

private data class ParsedRequest(val method: String, val path: String, val headers: Map<String, String>)

/** [userAgent] always has a value (defaulted to [HttpDefaults.BROWSER_USER_AGENT] at registration
 * time if the channel didn't specify one via `#EXTVLCOPT:http-user-agent=`); [referrer] is only
 * ever set from that same per-channel override. Resources discovered while rewriting a playlist
 * (segments, sub-playlists, key URIs) inherit both from their parent - see [ProxyServer.servePlaylist]. */
internal data class ResourceEntry(val type: String, val originalUrl: String, val userAgent: String, val referrer: String?)

/**
 * Local HLS proxy: rewrites and re-serves an HLS stream so a Cast receiver that can't (or won't)
 * play the origin URL directly can play it through the phone instead. Every path is
 * `/hls/<sessionToken>/<resourceId>`, `resourceId = SHA-256("type:url")`. Only GET/HEAD are
 * served; headers are capped at 16KB; the resource map is LRU-bounded to 512 entries.
 */
class ProxyServer(private val httpClient: OkHttpClient) {

    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null
    private var acceptThread: Thread? = null
    private var sessionToken: String = ""
    private var host: String = "127.0.0.1"
    // Fixed once in start() rather than read live off serverSocket?.localPort - that getter goes
    // null the instant stop() runs, and buildLocalUrl() racing a concurrent stop() would otherwise
    // silently produce "http://host:null/..." instead of either a real port or a loud failure.
    @Volatile private var boundPort: Int? = null
    @Volatile private var running = false

    private val resources = object : LinkedHashMap<String, ResourceEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ResourceEntry>?): Boolean =
            size > MAX_RESOURCES
    }
    private val resourcesLock = Any()

    // "One active remux stream per session" (docs/PROXY_RULES.md) - a channel switch replaces
    // activeRemuxSession, but doesn't stop() the whole server (see ensureStarted); the replaced
    // session is instead handed off gracefully, see RemuxHandoffPolicy.
    private var remuxEnabled = true
    private var activeRemuxSession: RawTsRemuxSession? = null
    private var drainingRemuxSession: RawTsRemuxSession? = null
    private var drainTimerThread: Thread? = null
    private val remuxLock = Any()

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
        val currentPort = boundPort
        val sameSession = this.sessionToken == sessionToken && this.host == host
        if (running && currentPort != null && sameSession) {
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
        val socket = ServerSocket(0, 50, InetAddress.getByName("0.0.0.0"))
        serverSocket = socket
        boundPort = socket.localPort
        val pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE)
        executor = pool
        running = true
        acceptThread = thread(name = "ProxyServer-accept") {
            // `running` alone isn't enough to tell this thread's own socket generation apart from
            // a later one: stop() then a fast start() (e.g. repeated cast fallback retries) can
            // flip the shared `running` flag back to true while this closure's `socket` is already
            // closed, and accept() on a closed socket throws immediately rather than blocking -
            // without this identity check that becomes a tight, unthrottled exception-logging spin
            // instead of a clean exit. `serverSocket` is reassigned to a new instance on every
            // start(), so `serverSocket === socket` is false as soon as this generation is stale.
            while (running && serverSocket === socket) {
                try {
                    val client = socket.accept()
                    pool.submit { handleConnection(client) }
                } catch (e: Exception) {
                    if (running && serverSocket === socket) {
                        AppLog.w(TAG) { "accept() failed: ${e.javaClass.simpleName}" }
                    }
                }
            }
        }
        return socket.localPort
    }

    fun stop() {
        running = false
        boundPort = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        executor?.shutdownNow()
        executor = null
        synchronized(resourcesLock) { resources.clear() }
        synchronized(remuxLock) {
            activeRemuxSession?.stop()
            activeRemuxSession = null
            drainingRemuxSession?.stop()
            drainingRemuxSession = null
            // Wake a pending drain timer (see beginDraining) so it exits now instead of sleeping
            // out the rest of its grace period against a server that no longer exists.
            drainTimerThread?.interrupt()
            drainTimerThread = null
        }
    }

    /** Called once the new channel's load is confirmed to have succeeded on the receiver (see
     * `cast/CastSessionRepository`) - lets a still-draining previous remux session be torn down
     * right away instead of waiting out the rest of [RemuxHandoffPolicy.DRAIN_TIMEOUT_MILLIS]. A
     * harmless no-op when nothing is draining. */
    fun confirmActiveSession() {
        synchronized(remuxLock) {
            val draining = drainingRemuxSession ?: return@synchronized
            if (RemuxHandoffPolicy.shouldKillDraining(confirmed = true, elapsedMillis = 0)) {
                draining.stop()
                drainingRemuxSession = null
                drainTimerThread?.interrupt()
                drainTimerThread = null
            }
        }
    }

    fun registerPlaylist(url: String, userAgent: String? = null, referrer: String? = null): String =
        registerResource(RESOURCE_TYPE_PLAYLIST, url, userAgent?.ifBlank { null } ?: HttpDefaults.BROWSER_USER_AGENT, referrer?.ifBlank { null })

    private fun registerResource(type: String, url: String, userAgent: String, referrer: String?): String {
        val id = Fingerprint.of("$type:$url")
        synchronized(resourcesLock) { resources[id] = ResourceEntry(type, url, userAgent, referrer) }
        return id
    }

    /** @throws IllegalStateException if called while the server isn't running - a caller asking
     * for a URL into a stopped proxy is a bug upstream, not something to paper over with a
     * malformed "http://host:null/..." URL. */
    fun buildLocalUrl(resourceId: String): String {
        val port = checkNotNull(boundPort) { "buildLocalUrl() called while the proxy server is not running" }
        return "http://$host:$port/hls/$sessionToken/$resourceId"
    }

    private fun buildRemuxSegmentUrl(resourceId: String, sequence: Int): String {
        val port = checkNotNull(boundPort) { "buildRemuxSegmentUrl() called while the proxy server is not running" }
        return "http://$host:$port/hls/$sessionToken/$resourceId/seg$sequence.ts"
    }

    /** Exposed only so tests can verify header inheritance (see [servePlaylist]) without going through a real socket. */
    internal fun resourcesForTesting(): Map<String, ResourceEntry> = synchronized(resourcesLock) { resources.toMap() }

    private fun handleConnection(socket: Socket) {
        socket.use {
            try {
                socket.soTimeout = 15_000
                val input = BufferedInputStream(socket.getInputStream())
                val output = BufferedOutputStream(socket.getOutputStream())
                val request = readRequest(input)
                when {
                    request == null -> writeError(output, 400, "Bad Request")
                    request.method == "OPTIONS" ->
                        writeCorsPreflight(output, request.headers["access-control-request-headers"])
                    request.method != "GET" && request.method != "HEAD" ->
                        writeError(output, 405, "Method Not Allowed")
                    else -> serveRequest(request, output)
                }
            } catch (e: Exception) {
                AppLog.w(TAG) { "Connection error: ${e.javaClass.simpleName}" }
            }
        }
    }

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
            writeError(output, 404, "Not Found")
            return
        }
        val resourceId = segments[2]
        val resource = synchronized(resourcesLock) { resources[resourceId] }
        if (resource == null) {
            writeError(output, 404, "Not Found")
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
        val existingSession = remuxSessionForPlaylist(resourceId)
        if (existingSession != null) {
            writePlaylistText(existingSession.currentPlaylist(), request.method, output)
            return
        }

        val upstreamRequest = Request.Builder().url(resource.originalUrl).apply {
            header("User-Agent", resource.userAgent)
            resource.referrer?.let { header("Referer", it) }
            request.headers["range"]?.let { header("Range", it) }
        }.build()
        val response = httpClient.newCall(upstreamRequest).execute()
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

        if (isPlaylist) {
            serveUpstreamPlaylist(resourceId, resource, response, request.method, output)
            return
        }

        if (shouldRemux) {
            serveRemuxedUpstream(resourceId, response, request.method, output)
        } else {
            response.use { servePassthrough(it, request.method, output) }
        }
    }

    /** Ownership of [response] passes to the remux session's background reader thread - it is
     * deliberately NOT wrapped in .use{} here, and is closed by the session itself once the reader
     * loop ends (stream EOF, stop(), or an error). A null session means the server was stopped
     * while this request's upstream fetch was in flight (see [startRemuxSession]) - the session was
     * never started, [response] is already closed, and the client gets a clean 503 rather than a
     * playlist from a server that no longer exists. */
    private fun serveRemuxedUpstream(resourceId: String, response: Response, method: String, output: OutputStream) {
        val session = startRemuxSession(resourceId, response)
        if (session == null) {
            writeError(output, HTTP_SERVICE_UNAVAILABLE, "Service Unavailable")
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
     * through [handleConnection]'s catch and silently drop the connection with no HTTP response at
     * all (which looks like a timeout to the receiver, not a clean error). */
    @Suppress("TooGenericExceptionCaught")
    private fun classifyTsProbe(tsProbe: ByteArray): CastCompatibilityVerdict = try {
        CastCompatibilityPolicy.classify(TsProgramInfoParser.parse(tsProbe))
    } catch (e: Exception) {
        AppLog.e(TAG, e) { "Failed to classify a probed TS stream's codecs; treating as Unknown" }
        CastCompatibilityVerdict.Unknown
    }

    /** Returns null - with [response] closed - if the server was stopped while this request's
     * upstream fetch was in flight. Without that check, a connection handler racing [stop] (the
     * receiver polls its playlist right as the cast session ends) could install and start a fresh
     * reader session *after* stop() already cleared everything, leaving a background thread reading
     * the upstream live stream indefinitely with no owner left to ever stop it. Both this check and
     * stop()'s cleanup run under [remuxLock], so whichever runs second sees the other's effect. */
    private fun startRemuxSession(resourceId: String, response: Response): RawTsRemuxSession? {
        val session = RawTsRemuxSession(resourceId, response, httpClient, ::buildRemuxSegmentUrl)
        synchronized(remuxLock) {
            if (!running) {
                runCatching { response.close() }
                return null
            }
            val previous = activeRemuxSession
            activeRemuxSession = session
            if (previous != null && previous.resourceId != resourceId) beginDraining(previous) else previous?.stop()
        }
        session.start()
        return session
    }

    /** See [RemuxHandoffPolicy]: keeps [previous] servable for a grace period instead of stopping
     * it the instant it's replaced, so an in-flight request for the channel just switched away from
     * doesn't turn a successful switch into a visible glitch. Only one session ever drains at a
     * time - a session already draining when a newer replacement lands has waited long enough. */
    private fun beginDraining(previous: RawTsRemuxSession) {
        drainingRemuxSession?.stop()
        drainingRemuxSession = previous
        drainTimerThread?.interrupt() // the session it was timing is stopped just above
        val startedAt = System.currentTimeMillis()
        drainTimerThread = thread(name = "ProxyServer-drain-${previous.resourceId}") {
            // An interrupt (stop(), or a newer drain replacing this one) just means "check now
            // instead of later" - the state checks below already handle every such case as a no-op.
            try {
                Thread.sleep(RemuxHandoffPolicy.DRAIN_TIMEOUT_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            synchronized(remuxLock) {
                val elapsed = System.currentTimeMillis() - startedAt
                val stillDraining = drainingRemuxSession === previous
                val expired = RemuxHandoffPolicy.shouldKillDraining(confirmed = false, elapsedMillis = elapsed)
                if (stillDraining && expired) {
                    previous.stop()
                    drainingRemuxSession = null
                }
            }
        }
    }

    /** Which session a *segment* fetch is served from - dead or alive, since a dead session's
     * already-buffered segments are still perfectly servable. Playlist polls use
     * [remuxSessionForPlaylist] instead, which is stricter about dead sessions. */
    private fun remuxSessionFor(resourceId: String): RawTsRemuxSession? = synchronized(remuxLock) {
        activeRemuxSession?.takeIf { it.resourceId == resourceId }
            ?: drainingRemuxSession?.takeIf { it.resourceId == resourceId }
    }

    /** Which session a *playlist* poll is served from, or null to fall through to a fresh upstream
     * fetch (and thus a fresh remux session - see [serveRemuxedUpstream]). The distinction that
     * matters is WHICH slot a dead session (see [RawTsRemuxSession.hasEnded]) occupies:
     * - a dead ACTIVE session reads as absent, so the poll restarts it - its playlist is frozen
     *   forever, and serving it would make every [com.uacastplayer.cast.CastRecoveryPolicy] reload
     *   of this same URL a guaranteed failure;
     * - a DRAINING session is served as-is even when dead: it's the channel just switched away
     *   from, and falling through for it would hand the OLD channel's resourceId to
     *   [startRemuxSession], which would then demote (and within the drain window kill) the LIVE
     *   active session of the CURRENT channel. A frozen playlist is exactly what a drained-out
     *   channel is allowed to end on. */
    private fun remuxSessionForPlaylist(resourceId: String): RawTsRemuxSession? = synchronized(remuxLock) {
        val active = activeRemuxSession?.takeIf { it.resourceId == resourceId }
        if (active != null) return@synchronized active.takeIf { !it.hasEnded }
        drainingRemuxSession?.takeIf { it.resourceId == resourceId }
    }

    private fun serveRemuxSegment(resourceId: String, segmentPathPart: String, method: String, output: OutputStream) {
        val sequence = parseSegmentSequence(segmentPathPart)
        val session = remuxSessionFor(resourceId)
        val bytes = sequence?.let { session?.segmentBytes(it) }
        if (bytes == null) {
            // Field-debugging aid (resourceId is a SHA fingerprint, never a raw URL): tells apart
            // "receiver asked for a segment that already rolled off / never existed" from
            // "receiver never asked for segments at all", which a sender-side logcat otherwise
            // can't distinguish - both just look like the receiver idling until IDLE/ERROR.
            AppLog.d(TAG) { "Remux segment miss: $segmentPathPart of $resourceId (session=${session != null})" }
            writeError(output, 404, "Not Found")
            return
        }
        AppLog.d(TAG) { "Remux segment served: seq=$sequence ${bytes.size}B of $resourceId" }
        val headers = mapOf("Content-Type" to "video/MP2T", "Content-Length" to bytes.size.toString())
        writeHeaders(output, 200, "OK", headers)
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
        writeHeaders(
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
            return
        }
        AppLog.d(TAG) { "Unwrapping single-stream wrapper playlist for resource $resourceId" }
        val mediaResponse = httpClient.newCall(
            Request.Builder().url(unwrapTarget).apply {
                header("User-Agent", resource.userAgent)
                resource.referrer?.let { header("Referer", it) }
            }.build(),
        ).execute()
        val shouldRemux = runCatching { shouldRemuxUpstream(mediaResponse) }
            .onFailure { runCatching { mediaResponse.close() } }
            .getOrThrow()
        if (shouldRemux) {
            serveRemuxedUpstream(resourceId, mediaResponse, method, output)
        } else {
            mediaResponse.use { servePassthrough(it, method, output) }
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
            writeError(output, 502, "Bad Gateway")
            return null
        }
        return when (val bounded = BoundedTextReader.readText(body.byteStream(), MAX_PLAYLIST_BYTES)) {
            is BoundedReadResult.Success -> bounded.text
            BoundedReadResult.SizeLimitExceeded -> {
                AppLog.w(TAG) { "Upstream playlist exceeded $MAX_PLAYLIST_BYTES bytes; rejecting" }
                writeError(output, 502, "Bad Gateway")
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
            buildLocalUrl(registerResource(type, absoluteUrl, parent.userAgent, parent.referrer))
        }
        writePlaylistText(rewritten, method, output)
    }

    private fun servePassthrough(response: Response, method: String, output: OutputStream) {
        // A non-2xx here is forwarded to the receiver as-is (below) - which looks identical to a
        // genuine codec/network failure on the sender's own logs, since the receiver just goes
        // IDLE/ERROR a moment later either way. This confirmed-single-connection-per-account
        // origin is expected to reject an occasional segment fetch under rapid channel switching
        // (multiple proxy fetches racing for the one slot) - this line is what tells that apart
        // from a genuine proxy defect on the next field capture.
        if (!response.isSuccessful) {
            AppLog.w(TAG) { "Passthrough upstream returned ${response.code} for ${response.request.url.encodedPath}" }
        }
        val headers = linkedMapOf("Content-Type" to (response.header("Content-Type") ?: "application/octet-stream"))
        response.header("Content-Range")?.let { headers["Content-Range"] = it }
        response.header("Accept-Ranges")?.let { headers["Accept-Ranges"] = it }
        response.header("Content-Length")?.let { headers["Content-Length"] = it }
        writeHeaders(output, response.code, response.message.ifEmpty { "OK" }, headers)
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

    // Deliberately always "close" rather than keep-alive: correctly framing a reused connection
    // needs the response's exact length known up front for every case (Content-Length here is
    // reliable, but a chunked or close-delimited upstream body is not), and a framing mistake
    // hangs or corrupts the next request on that socket - worse for a Chromecast receiver than
    // the extra per-segment TCP handshake this trades away. Revisit only with a real framing
    // layer (chunked-encoding support included), not a quick loop around handleConnection.
    //
    // CORS on every response is not optional: the Default Media Receiver is a web app, so every
    // playlist/segment fetch it makes is a cross-origin XHR - without Access-Control-Allow-Origin
    // the receiver's browser blocks the response *after* it arrives, and playback dies within
    // seconds as IDLE/ERROR (playedMs=0) even though this server behaved perfectly. Field
    // signature: direct cast fails (origin without CORS), proxy fallback then fails identically.
    private fun writeHeaders(output: OutputStream, status: Int, statusText: String, headers: Map<String, String>) {
        val builder = StringBuilder("HTTP/1.1 $status $statusText\r\n")
        for ((key, value) in headers) builder.append("$key: $value\r\n")
        builder.append("Access-Control-Allow-Origin: *\r\n")
        builder.append("Access-Control-Expose-Headers: Content-Length, Content-Range\r\n")
        builder.append("Connection: close\r\n\r\n")
        output.write(builder.toString().toByteArray(Charsets.ISO_8859_1))
    }

    /** A CORS preflight (the receiver's browser sends OPTIONS before any XHR with a non-safelisted
     * header - its Range segment requests qualify) must succeed generically: it carries no
     * credentials and gets no body, so there's nothing to protect by rejecting it - while a 405
     * here would fail the actual media request that follows before it's ever made. The requested
     * headers are echoed back verbatim; the fallback list covers what HLS players actually send. */
    private fun writeCorsPreflight(output: OutputStream, requestedHeaders: String?) {
        val headers = linkedMapOf(
            "Access-Control-Allow-Methods" to "GET, HEAD, OPTIONS",
            "Access-Control-Allow-Headers" to (requestedHeaders ?: "Range, Content-Type"),
            "Access-Control-Max-Age" to CORS_MAX_AGE_SECONDS,
            "Content-Length" to "0",
        )
        writeHeaders(output, HTTP_NO_CONTENT, "No Content", headers)
        output.flush()
    }

    private fun writeError(output: OutputStream, status: Int, statusText: String) {
        writeHeaders(output, status, statusText, mapOf("Content-Length" to "0"))
        output.flush()
    }

    private fun readRequest(input: InputStream): ParsedRequest? {
        val lines = mutableListOf<String>()
        var totalRead = 0
        val lineBuffer = ByteArrayOutputStream()

        while (true) {
            lineBuffer.reset()
            while (true) {
                val byte = input.read()
                if (byte == -1) return null
                totalRead++
                if (totalRead > MAX_HEADER_BYTES) return null
                if (byte == '\n'.code) break
                if (byte != '\r'.code) lineBuffer.write(byte)
            }
            val line = lineBuffer.toString(Charsets.ISO_8859_1.name())
            if (line.isEmpty()) break
            lines += line
        }
        if (lines.isEmpty()) return null

        val requestLine = lines[0].split(" ")
        if (requestLine.size < 2) return null

        val headers = mutableMapOf<String, String>()
        for (i in 1 until lines.size) {
            val separator = lines[i].indexOf(':')
            if (separator <= 0) continue
            headers[lines[i].substring(0, separator).trim().lowercase()] = lines[i].substring(separator + 1).trim()
        }
        return ParsedRequest(requestLine[0].uppercase(), requestLine[1], headers)
    }
}

/**
 * Owns one continuous upstream raw-TS read for the lifetime of a "raw TS remux" channel (see
 * docs/PROXY_RULES.md "Raw TS remux"): a single background thread reads the origin response body
 * in chunks, resyncs to TS packet boundaries the same way [com.uacastplayer.cast.TsProgramInfoParser]
 * does (a live HTTP stream isn't guaranteed to start exactly on a sync byte), feeds each packet to
 * a [TsSegmenter], and appends completed segments to a [RemuxSegmentBuffer] that [ProxyServer]
 * serves segment/playlist requests from. [initialResponse] is this session's own to close - callers
 * must not wrap it in their own `use {}`. If the connection drops mid-stream, the reader reconnects
 * to the same origin via [httpClient] with backoff (see [RemuxReconnectPolicy]) instead of ending
 * the session outright - see docs/PROXY_RULES.md "Raw TS remux" for the reconnect/discontinuity
 * behavior.
 */
internal class RawTsRemuxSession(
    val resourceId: String,
    initialResponse: Response,
    private val httpClient: OkHttpClient,
    private val segmentUrl: (resourceId: String, sequence: Int) -> String,
    private val segmenter: TsPacketSegmenter = TsSegmenter(targetDurationMillis = REMUX_TARGET_SEGMENT_SECONDS * 1000L),
) {
    private val buffer = RemuxSegmentBuffer()
    private val bufferLock = Any()
    @Volatile private var running = true
    private var readerThread: Thread? = null

    /** True once [readLoop] has exited for good - reconnect policy gave up, natural stream end, or
     * [stop] - meaning [currentPlaylist] is frozen and will never grow again. [ProxyServer] checks
     * this before serving an existing session's playlist: a dead session must be replaced with a
     * fresh one, not polled forever (see servePlaylistOrMediaResource). Buffered segments are still
     * servable after this flips - only the playlist short-circuit cares. */
    @Volatile var hasEnded = false
        private set

    // Only ever read/written from the reader thread itself - readLoop() owns it exclusively once
    // start() has been called, so it doesn't need the same synchronization as `buffer`.
    private var currentResponse = initialResponse

    fun start() {
        readerThread = thread(name = "ProxyServer-remux-$resourceId") { readLoop() }
    }

    /** Stops the reader loop and closes the upstream connection - safe to call from any thread,
     * including the reader thread itself (e.g. on natural stream end, where it's a no-op beyond
     * the close). */
    fun stop() {
        running = false
        runCatching { currentResponse.close() } // unblocks a blocking read() inside readLoop()
        val thread = readerThread
        thread?.interrupt() // unblocks a Thread.sleep() during a reconnect backoff wait
        if (thread != null && thread !== Thread.currentThread()) {
            runCatching { thread.join(REMUX_READER_JOIN_TIMEOUT_MILLIS) }
        }
    }

    /** Blocks the calling (request-handling) thread briefly for the first segment(s) to become
     * available, so the receiver's very first playlist fetch isn't an empty, useless list - then
     * returns whatever is ready, even if that's still nothing (a slow origin shouldn't hang the
     * connection forever). */
    fun awaitInitialPlaylist(): String {
        val deadline = System.currentTimeMillis() + REMUX_INITIAL_PLAYLIST_WAIT_MILLIS
        while (shouldAwaitMoreSegments(deadline)) {
            Thread.sleep(REMUX_POLL_INTERVAL_MILLIS)
        }
        return currentPlaylist()
    }

    /** [hasEnded] means no further segment can ever arrive (the final flush happens before the
     * flag flips), so once it's set whatever the buffer holds now is all there will ever be -
     * waiting out the rest of the deadline would just stall the request thread for nothing. */
    private fun shouldAwaitMoreSegments(deadline: Long): Boolean {
        if (!running || hasEnded) return false
        return isBufferEmpty() && System.currentTimeMillis() < deadline
    }

    fun currentPlaylist(): String {
        val snapshot = synchronized(bufferLock) { buffer.snapshot() }
        // One line per receiver playlist poll - shows whether the live window is actually growing
        // between polls (segments being produced) and what range the receiver gets to pick from.
        AppLog.d(TAG) {
            "Remux $resourceId playlist poll: ${snapshot.size} segments" +
                " [${snapshot.firstOrNull()?.sequence}..${snapshot.lastOrNull()?.sequence}]"
        }
        return LiveHlsPlaylistBuilder.build(
            segments = snapshot,
            segmentUrl = { sequence -> segmentUrl(resourceId, sequence) },
            configuredTargetDurationSeconds = REMUX_TARGET_SEGMENT_SECONDS,
        )
    }

    fun segmentBytes(sequence: Int): ByteArray? = synchronized(bufferLock) { buffer.segment(sequence)?.bytes }

    private fun isBufferEmpty(): Boolean = synchronized(bufferLock) { buffer.isEmpty }

    /** Reads from [currentResponse] until it ends (EOF, a dropped connection, or an uncaught
     * parsing error - see [readUntilDisconnected]), then - while still [running] - reconnects and
     * keeps going, rather than treating any of those as the end of the session. Only gives up once
     * [RemuxReconnectPolicy] does. */
    private fun readLoop() {
        var carry = ByteArray(0)
        var shouldContinue = true
        while (running && shouldContinue) {
            val input = currentResponse.body?.byteStream()
            if (input != null) {
                carry = readUntilDisconnected(input, carry)
                runCatching { currentResponse.close() }
                shouldContinue = running && reconnect()
                if (shouldContinue) carry = ByteArray(0) // stale trailing partial-packet bytes
            } else {
                AppLog.w(TAG) { "Raw TS remux for $resourceId: upstream response had no body" }
                shouldContinue = false
            }
        }
        // Idempotent last-resort close: normally currentResponse is already closed above, but a
        // stop() that lands while attemptReconnect is mid-flight can leave the freshly swapped-in
        // response unclosed (stop() closed the OLD one, the loop then exits on !running before
        // ever reading the new one) - field-confirmed as OkHttp connection-leak warnings against
        // the upstream segment hosts.
        runCatching { currentResponse.close() }
        segmenter.flush()?.let(::addSegment)
        hasEnded = true
    }

    /** Feeds [input] to the segmenter until it hits EOF, or *any* exception - not just an
     * IOException - reading or parsing this chunk. This is deliberately broad: [segmenter] is a
     * raw-byte parser fed directly from an arbitrary third-party server, and a corrupted packet
     * getting past [TsProgramInfoParser]/[TsSegmenter]'s own bounds checks must end this read
     * cycle and fall through to [reconnect], not crash the process (see [reconnect]'s caller,
     * [readLoop]) - the same guarantee an IOException from a dropped socket already got.
     * [TsSegmenter.feed] only mutates its internal buffer *after* it has finished parsing a packet
     * (see its source), so an exception thrown mid-parse never leaves it with a half-written
     * buffer - reusing the same [segmenter] instance across this catch and the next connection
     * attempt is safe, unlike a genuine reconnect where the PCR clock itself resets.
     * Returns the trailing incomplete packet bytes to prepend to the next read. */
    @Suppress("TooGenericExceptionCaught")
    private fun readUntilDisconnected(input: InputStream, initialCarry: ByteArray): ByteArray {
        var carry = initialCarry
        val chunk = ByteArray(REMUX_READ_CHUNK_BYTES)
        try {
            while (running) {
                val read = input.read(chunk)
                if (read == -1) return carry
                carry = consumePackets(if (carry.isEmpty()) chunk.copyOf(read) else carry + chunk.copyOf(read))
            }
        } catch (e: Exception) {
            AppLog.e(TAG, e) { "Raw TS remux reader for $resourceId lost the connection or hit a parsing error" }
        }
        return carry
    }

    /** Backoff-retries the upstream connection (see [RemuxReconnectPolicy]) and marks the next
     * segment produced as a discontinuity on success. Returns false once the policy gives up or
     * [stop] is called mid-backoff - the caller should end the session in that case. */
    private fun reconnect(): Boolean {
        segmenter.onReconnect()?.let(::addSegment)
        var attempt = 0
        var reconnected = false
        var giveUp = false
        while (running && !reconnected && !giveUp) {
            when (val decision = RemuxReconnectPolicy.onDisconnected(attempt)) {
                is RemuxReconnectPolicy.Decision.Retry -> {
                    attempt = decision.nextAttempt
                    reconnected = attemptReconnect(decision.delayMillis, attempt)
                    giveUp = !reconnected && !running
                }
                RemuxReconnectPolicy.Decision.GiveUp -> {
                    AppLog.w(TAG) { "Raw TS remux for $resourceId: giving up after repeated reconnect failures" }
                    giveUp = true
                }
            }
        }
        return reconnected
    }

    /** One reconnect attempt: waits out the backoff delay, then re-issues the same origin request.
     * Returns false both when the request fails and when [stop] interrupts the backoff wait -
     * [reconnect]'s loop tells those apart via [running], which [stop] clears first. */
    private fun attemptReconnect(delayMillis: Long, attempt: Int): Boolean {
        if (!sleepUnlessInterrupted(delayMillis)) return false
        val newResponse = runCatching { httpClient.newCall(currentResponse.request).execute() }.getOrNull()
        val usable = newResponse != null && newResponse.isSuccessful && newResponse.body != null
        if (usable) {
            currentResponse = requireNotNull(newResponse)
            AppLog.d(TAG) { "Raw TS remux for $resourceId: reconnected upstream" }
        } else {
            runCatching { newResponse?.close() }
            AppLog.w(TAG) { "Raw TS remux for $resourceId: reconnect attempt $attempt failed" }
        }
        return usable
    }

    /** Sleeps for [delayMillis], returning false (instead of throwing) if interrupted - e.g. [stop]
     * was called mid-backoff - so [reconnect]'s own control flow can stay a flat single-return. */
    private fun sleepUnlessInterrupted(delayMillis: Long): Boolean = try {
        Thread.sleep(delayMillis)
        true
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    /** Extracts every complete, sync-aligned packet from [data] (byte-scanning forward to resync
     * after any misaligned byte, same idea as [com.uacastplayer.cast.TsProgramInfoParser]),
     * feeding each to [segmenter]. Returns the trailing incomplete bytes to prepend to the next
     * read. */
    private fun consumePackets(data: ByteArray): ByteArray {
        var offset = 0
        while (offset + TS_PACKET_SIZE <= data.size) {
            if ((data[offset].toInt() and 0xFF) == TS_SYNC_BYTE) {
                segmenter.feed(data.copyOfRange(offset, offset + TS_PACKET_SIZE))?.let(::addSegment)
                offset += TS_PACKET_SIZE
            } else {
                offset++
            }
        }
        return data.copyOfRange(offset, data.size)
    }

    private fun addSegment(segment: TsSegment) {
        synchronized(bufferLock) { buffer.add(segment) }
        AppLog.d(TAG) {
            "Remux $resourceId segment cut: seq=${segment.sequence} ${segment.bytes.size}B" +
                " ${segment.durationMillis}ms disc=${segment.discontinuity}"
        }
    }
}
