package com.uacastplayer.data.cast

import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.log.AppLog
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

private const val TAG = "ProxyHttpServer"
private const val MAX_HEADER_BYTES = 16 * 1024
private const val MAX_REQUEST_LINE_BYTES = 4 * 1024
private const val MAX_HEADER_LINE_BYTES = 8 * 1024
private const val MAX_HEADER_COUNT = 64
private const val REQUEST_LINE_PART_COUNT = 3

// Sized against the longest a single connection can legitimately occupy a thread without doing any
// work, not against expected concurrency: a playlist poll for a channel whose remux session is
// still warming up parks its handler thread inside RawTsRemuxSession.awaitInitialPlaylist for up to
// 8s waiting for the first segment to be cut. Those parked threads still count against this pool,
// so at the old size of 6 a burst of channel switches during a cast (each spinning up a fresh remux
// session, each with its own warm-up wait) could leave the receiver's segment fetches queued behind
// playlist polls that are doing nothing but sleeping. Every response also closes its connection (see
// writeHeaders), so threads are never held by idle keep-alive sockets - only by real in-flight work.
private const val THREAD_POOL_SIZE = 16
private const val ADMISSION_POOL_SIZE = 4
private const val MAX_QUEUED_ADMISSIONS = 16
private const val MAX_QUEUED_RESPONSES = 32
private const val MAX_CONNECTIONS_PER_IP = 8
private const val SOCKET_READ_TIMEOUT_MILLIS = 15_000
private const val HTTP_NO_CONTENT = 204
private const val CORS_MAX_AGE_SECONDS = "86400"
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_NOT_FOUND = 404
private const val HTTP_METHOD_NOT_ALLOWED = 405
private const val HTTP_SERVICE_UNAVAILABLE = 503
private const val HTTP_VERSION_1_0 = "HTTP/1.0"
private const val HTTP_VERSION_1_1 = "HTTP/1.1"
private const val SERVER_SOCKET_BACKLOG = 50
private const val ASCII_SPACE = 32
private const val ASCII_VISIBLE_FIRST = 33
private const val ASCII_VISIBLE_LAST = 126
private const val ASCII_DELETE = 127
private val HTTP_HEADER_NAME_SEPARATORS = "()<>@,;:\\\"/[]?={}".toSet()

internal data class ParsedRequest(val method: String, val path: String, val headers: Map<String, String>)

/**
 * One admission against the exact per-IP counter generation it incremented.
 *
 * [ProxyHttpServer.stop] clears [ProxyHttpServer.connectionsPerIp]. A handler from the stopped
 * generation can still be unwinding while a fast [ProxyHttpServer.start] accepts new clients from
 * the same IP. Releasing by IP alone would then decrement the new generation's counter and silently
 * weaken its connection limit. Keeping the counter identity makes a stale release a no-op.
 */
private data class IpConnectionLease(val clientIp: String, val counter: AtomicInteger)

/** Technical state that follows one accepted socket through parsing and response admission. */
private class AdmittedConnection(
    val socket: Socket,
    val ipLease: IpConnectionLease,
    val output: OutputStream,
    val responsePool: ExecutorService,
    val metrics: ProxyHttpMetrics,
)

internal data class ProxyHttpMetricsSnapshot(
    val acceptedConnections: Long,
    val rejectedPerIp: Long,
    val rejectedAdmissionQueue: Long,
    val rejectedResponseQueue: Long,
    val malformedRequests: Long,
    val unauthorizedRequests: Long,
)

/** Counters with one stable identity per server generation. A stopped generation can still have a
 * handler unwinding, so resetting shared atomics would let that handler contaminate the next run. */
private class ProxyHttpMetrics {
    val acceptedConnections = AtomicLong()
    val rejectedPerIp = AtomicLong()
    val rejectedAdmissionQueue = AtomicLong()
    val rejectedResponseQueue = AtomicLong()
    val malformedRequests = AtomicLong()
    val unauthorizedRequests = AtomicLong()

    fun snapshot() = ProxyHttpMetricsSnapshot(
        acceptedConnections = acceptedConnections.get(),
        rejectedPerIp = rejectedPerIp.get(),
        rejectedAdmissionQueue = rejectedAdmissionQueue.get(),
        rejectedResponseQueue = rejectedResponseQueue.get(),
        malformedRequests = malformedRequests.get(),
        unauthorizedRequests = unauthorizedRequests.get(),
    )
}

/**
 * Raw-socket HTTP/1.1 server: owns the accept loop, request parsing, and the low-level response
 * writers (headers, CORS preflight, plain errors). Knows nothing about resources, remux sessions,
 * or playlists - every GET/HEAD request that isn't a CORS preflight or an unsupported method is
 * handed to [onRequest], which is responsible for writing a response to it. Always binds all
 * interfaces (`0.0.0.0`) regardless of the `host` a caller later builds URLs against - the two are
 * unrelated, `host` only ever describes which interface a URL should point *at* for the receiver.
 */
internal class ProxyHttpServer(
    /** Runs after bounded parsing but before a request can occupy the response-serving pool. */
    private val isRequestAuthorized: (ParsedRequest) -> Boolean = { true },
    private val onRequest: (ParsedRequest, OutputStream) -> Unit,
) {

    private var serverSocket: ServerSocket? = null
    private var admissionExecutor: ExecutorService? = null
    private var executor: ExecutorService? = null
    private var acceptThread: Thread? = null
    /** Every accepted socket, including work still queued in [executor]. `shutdownNow()` only
     * interrupts worker threads; it neither closes sockets already inside a handler nor the sockets
     * captured by queued tasks that will now never run. Tracking at accept time is therefore what
     * makes [stop] a real network teardown instead of only a listening-socket teardown. */
    private val clientSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val connectionsPerIp = ConcurrentHashMap<String, AtomicInteger>()
    @Volatile private var activeMetrics = ProxyHttpMetrics()
    // Fixed once in start() rather than read live off serverSocket?.localPort - that getter goes
    // null the instant stop() runs, and a caller racing a concurrent stop() would otherwise
    // silently see a null port instead of either a real port or a loud failure.
    @Volatile private var boundPort: Int? = null
    @Volatile private var running = false

    val port: Int? get() = boundPort
    val isRunning: Boolean get() = running

    @Synchronized
    fun start(): Int {
        stop()
        val socket = ServerSocket(0, SERVER_SOCKET_BACKLOG, InetAddress.getByName("0.0.0.0"))
        serverSocket = socket
        boundPort = socket.localPort
        val responsePool = boundedExecutor(THREAD_POOL_SIZE, MAX_QUEUED_RESPONSES)
        val admissionPool = boundedExecutor(ADMISSION_POOL_SIZE, MAX_QUEUED_ADMISSIONS)
        val metrics = ProxyHttpMetrics()
        executor = responsePool
        admissionExecutor = admissionPool
        activeMetrics = metrics
        running = true
        acceptThread = thread(name = "ProxyServer-accept") {
            acceptLoop(socket, admissionPool, responsePool, metrics)
        }
        return socket.localPort
    }

    // The accept loop's catch has to be broad: one bad connection must not kill the server.
    @Suppress("TooGenericExceptionCaught")
    private fun acceptLoop(
        socket: ServerSocket,
        admissionPool: ExecutorService,
        responsePool: ExecutorService,
        metrics: ProxyHttpMetrics,
    ) {
        // Socket identity distinguishes this generation from a fast stop()+start(); without it an
        // old accept thread can spin on a closed socket after the shared running flag turns true.
        while (running && serverSocket === socket) {
            try {
                acceptClient(socket, admissionPool, responsePool, metrics)
            } catch (e: Exception) {
                if (running && serverSocket === socket) {
                    AppLog.w(TAG) { "accept() failed: ${e.javaClass.simpleName}" }
                }
            }
        }
    }

    private fun acceptClient(
        socket: ServerSocket,
        admissionPool: ExecutorService,
        responsePool: ExecutorService,
        metrics: ProxyHttpMetrics,
    ) {
        val client = socket.accept()
        metrics.acceptedConnections.incrementAndGet()
        clientSockets += client
        if (!running || serverSocket !== socket) {
            closeClient(client)
            return
        }

        val clientIp = client.inetAddress.hostAddress ?: client.inetAddress.toString()
        val ipLease = acquireIpSlot(clientIp)
        if (ipLease == null) {
            metrics.rejectedPerIp.incrementAndGet()
            closeClient(client)
            return
        }
        try {
            admissionPool.submit { admitConnection(client, ipLease, responsePool, metrics) }
        } catch (e: RejectedExecutionException) {
            AppLog.d(TAG) { "Admission queue rejected a client: ${e.javaClass.simpleName}" }
            if (running && serverSocket === socket) metrics.rejectedAdmissionQueue.incrementAndGet()
            releaseIpSlot(ipLease)
            closeClient(client)
        }
    }

    @Synchronized
    fun stop() {
        running = false
        boundPort = null
        runCatchingNonFatal { serverSocket?.close() }.onFailure { error ->
            AppLog.d(TAG) { "Listening socket close failed: ${error.javaClass.simpleName}" }
        }
        serverSocket = null
        // Close before interrupting the pool: socket reads/writes do not reliably react to thread
        // interruption, while close() unblocks both immediately. This also closes accepted sockets
        // still sitting in the executor queue and therefore never entered handleConnection().
        // Do not snapshot this concurrent set with toList(). Kotlin special-cases a Collection
        // whose observed size is one by calling iterator().next() directly; a handler can remove
        // that last socket between the size read and next(), making stop() itself throw
        // NoSuchElementException. ConcurrentHashMap's forEach is weakly consistent and safe while
        // closeClient() removes entries in parallel.
        clientSockets.forEach(::closeClient)
        clientSockets.clear()
        connectionsPerIp.clear()
        admissionExecutor?.shutdownNow()
        admissionExecutor = null
        executor?.shutdownNow()
        executor = null
        acceptThread = null
    }

    /**
     * Same reasoning as start()'s accept loop: one client's malformed request or dropped socket
     * must not propagate past this connection - the thread pool serves every other client fine.
     *
     * The failure is reported with the phase it happened in and the path when one was parsed,
     * because "Connection error: SocketException" on its own turned out to be a dead end in a real
     * field capture: it cannot distinguish a receiver that never sent a request (a reachability
     * probe opening and closing a socket - routine, and not a warning) from one that gave up
     * mid-response, which is a real symptom. The path is this proxy's own
     * `/hls/<token>/<resourceId>`, where both parts are already opaque fingerprints.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun admitConnection(
        socket: Socket,
        ipLease: IpConnectionLease,
        responsePool: ExecutorService,
        metrics: ProxyHttpMetrics,
    ) {
        var handedToResponsePool = false
        var request: ParsedRequest? = null
        try {
            socket.soTimeout = SOCKET_READ_TIMEOUT_MILLIS
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())
            request = readRequest(input)
            handedToResponsePool = routeAdmittedRequest(
                request,
                AdmittedConnection(socket, ipLease, output, responsePool, metrics),
            )
        } catch (e: SocketTimeoutException) {
            logConnectionFailure(e, request, routineDisconnect = true)
        } catch (e: SocketException) {
            logConnectionFailure(e, request, routineDisconnect = true)
        } catch (e: Exception) {
            logConnectionFailure(e, request, routineDisconnect = false)
        } finally {
            if (!handedToResponsePool) {
                releaseIpSlot(ipLease)
                closeClient(socket)
            }
        }
    }

    private fun routeAdmittedRequest(
        request: ParsedRequest?,
        connection: AdmittedConnection,
    ): Boolean = when {
        request == null -> {
            connection.metrics.malformedRequests.incrementAndGet()
            AppLog.d(TAG) { "Connection sent a malformed or incomplete request" }
            writeError(connection.output, HTTP_BAD_REQUEST, "Bad Request")
            false
        }
        request.method == "OPTIONS" -> {
            writeCorsPreflight(connection.output, request.headers["access-control-request-headers"])
            false
        }
        request.method != "GET" && request.method != "HEAD" -> {
            writeError(connection.output, HTTP_METHOD_NOT_ALLOWED, "Method Not Allowed")
            false
        }
        !isRequestAuthorized(request) -> {
            connection.metrics.unauthorizedRequests.incrementAndGet()
            writeError(connection.output, HTTP_NOT_FOUND, "Not Found")
            false
        }
        else -> submitAuthorizedRequest(request, connection)
    }

    private fun submitAuthorizedRequest(
        request: ParsedRequest,
        connection: AdmittedConnection,
    ): Boolean = try {
        connection.responsePool.submit {
            serveAuthorizedRequest(connection.socket, connection.ipLease, request)
        }
        true
    } catch (e: RejectedExecutionException) {
        AppLog.d(TAG) { "Response queue rejected a client: ${e.javaClass.simpleName}" }
        if (running) connection.metrics.rejectedResponseQueue.incrementAndGet()
        writeError(connection.output, HTTP_SERVICE_UNAVAILABLE, "Service Unavailable")
        false
    }

    @Suppress("TooGenericExceptionCaught")
    private fun serveAuthorizedRequest(socket: Socket, ipLease: IpConnectionLease, request: ParsedRequest) {
        try {
            val output = BufferedOutputStream(socket.getOutputStream())
            onRequest(request, output)
        } catch (e: SocketTimeoutException) {
            logConnectionFailure(e, request, routineDisconnect = true)
        } catch (e: SocketException) {
            logConnectionFailure(e, request, routineDisconnect = true)
        } catch (e: Exception) {
            logConnectionFailure(e, request, routineDisconnect = false)
        } finally {
            releaseIpSlot(ipLease)
            closeClient(socket)
        }
    }

    private fun logConnectionFailure(
        error: Exception,
        request: ParsedRequest?,
        routineDisconnect: Boolean,
    ) {
        val phase = if (request == null) "while reading the request" else "serving ${request.path}"
        if (!running || routineDisconnect) {
            // Receiver reloads and reachability probes routinely abandon sockets. The proxy path
            // already logs a partial byte count when this cut a body short, so a second WARN
            // containing only SocketException adds noise, not evidence.
            AppLog.d(TAG) { "Connection closed $phase: ${error.javaClass.simpleName}" }
        } else {
            AppLog.w(TAG) { "Connection error $phase: ${error.javaClass.simpleName}" }
        }
    }

    /** Test-only observability for proving that [stop] drains accepted and queued clients. */
    internal fun activeClientCountForTesting(): Int = clientSockets.size

    internal fun metricsSnapshot(): ProxyHttpMetricsSnapshot = activeMetrics.snapshot()

    private fun acquireIpSlot(clientIp: String): IpConnectionLease? {
        val count = connectionsPerIp.computeIfAbsent(clientIp) { AtomicInteger() }
        while (true) {
            val current = count.get()
            if (current >= MAX_CONNECTIONS_PER_IP) return null
            if (count.compareAndSet(current, current + 1)) return IpConnectionLease(clientIp, count)
        }
    }

    private fun releaseIpSlot(lease: IpConnectionLease) {
        connectionsPerIp.computeIfPresent(lease.clientIp) { _, currentCounter ->
            when {
                currentCounter !== lease.counter -> currentCounter
                currentCounter.decrementAndGet() <= 0 -> null
                else -> currentCounter
            }
        }
    }

    private fun closeClient(socket: Socket) {
        clientSockets -= socket
        runCatchingNonFatal { socket.close() }
    }

    private fun boundedExecutor(threads: Int, queueCapacity: Int): ExecutorService =
        ThreadPoolExecutor(
            threads,
            threads,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(queueCapacity),
        )

    private fun readRequest(input: InputStream): ParsedRequest? {
        val lines = readRequestLines(input) ?: return null
        return parseRequestLines(lines)
    }

    private fun readRequestLines(input: InputStream): List<String>? {
        val lines = mutableListOf<String>()
        val budget = RequestReadBudget()
        val lineBuffer = ByteArrayOutputStream()
        var valid = true
        var complete = false
        while (valid && !complete) {
            val lineLimit = if (lines.isEmpty()) MAX_REQUEST_LINE_BYTES else MAX_HEADER_LINE_BYTES
            val line = readHttpLine(input, lineBuffer, budget, lineLimit)
            when {
                line == null -> valid = false
                line.isEmpty() -> complete = true
                lines.size > MAX_HEADER_COUNT -> valid = false
                else -> lines += line
            }
        }
        return lines.takeIf { valid && complete && it.isNotEmpty() }
    }

    private fun parseRequestLines(lines: List<String>): ParsedRequest? {
        val requestLine = lines[0].trim().split(Regex("\\s+"), limit = REQUEST_LINE_PART_COUNT)
        if (requestLine.size != REQUEST_LINE_PART_COUNT || !isSupportedHttpVersion(requestLine[2])) return null
        return parseHeaders(lines.drop(1))?.let { headers ->
            ParsedRequest(requestLine[0].uppercase(), requestLine[1], headers)
        }
    }

    private fun isSupportedHttpVersion(version: String): Boolean =
        version == HTTP_VERSION_1_0 || version == HTTP_VERSION_1_1

    private fun parseHeaders(lines: List<String>): Map<String, String>? = buildMap {
        for (line in lines) {
            val separator = line.indexOf(':')
            if (separator <= 0) return null
            val name = line.substring(0, separator)
            val value = line.substring(separator + 1).trim()
            if (!isValidHeaderName(name) || !isValidHeaderValue(value)) return null
            put(name.lowercase(), value)
        }
    }

    private fun isValidHeaderName(name: String): Boolean = name.isNotEmpty() && name.all { character ->
        character.code in ASCII_VISIBLE_FIRST..ASCII_VISIBLE_LAST && character !in HTTP_HEADER_NAME_SEPARATORS
    }

    private fun isValidHeaderValue(value: String): Boolean = value.all { character ->
        character == '\t' || character.code >= ASCII_SPACE && character.code != ASCII_DELETE
    }

    private fun readHttpLine(
        input: InputStream,
        lineBuffer: ByteArrayOutputStream,
        budget: RequestReadBudget,
        lineLimit: Int,
    ): String? {
        lineBuffer.reset()
        while (true) {
            when (consumeHttpByte(input.read(), lineBuffer, budget, lineLimit)) {
                LineReadStatus.CONTINUE -> Unit
                LineReadStatus.COMPLETE -> return lineBuffer.toString(Charsets.ISO_8859_1.name())
                LineReadStatus.INVALID -> return null
            }
        }
    }

    private fun consumeHttpByte(
        byte: Int,
        lineBuffer: ByteArrayOutputStream,
        budget: RequestReadBudget,
        lineLimit: Int,
    ): LineReadStatus {
        if (byte == -1) return LineReadStatus.INVALID
        budget.totalBytes++
        return when {
            budget.totalBytes > MAX_HEADER_BYTES -> LineReadStatus.INVALID
            byte == '\n'.code -> LineReadStatus.COMPLETE
            byte == '\r'.code -> LineReadStatus.CONTINUE
            else -> {
                lineBuffer.write(byte)
                if (lineBuffer.size() > lineLimit) LineReadStatus.INVALID else LineReadStatus.CONTINUE
            }
        }
    }

    private data class RequestReadBudget(var totalBytes: Int = 0)
    private enum class LineReadStatus { CONTINUE, COMPLETE, INVALID }

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
    fun writeHeaders(output: OutputStream, status: Int, statusText: String, headers: Map<String, String>) {
        val builder = StringBuilder("HTTP/1.1 $status $statusText\r\n")
        for ((key, value) in headers) builder.append("$key: $value\r\n")
        builder.append("Access-Control-Allow-Origin: *\r\n")
        builder.append("Access-Control-Expose-Headers: Content-Length, Content-Range\r\n")
        builder.append("Connection: close\r\n\r\n")
        output.write(builder.toString().toByteArray(Charsets.ISO_8859_1))
        // Flushed here rather than left to each caller, because the caller that forgets loses the
        // whole response silently and nothing anywhere says so. [handleConnection] wraps the socket
        // in a BufferedOutputStream and then closes the *socket*, not the stream - so bytes still
        // sitting in that buffer are never written at all. A HEAD on the flattened-HLS path was
        // exactly that: headers written, nothing flushed, connection closed, and a renderer asking
        // what the resource is got a socket that opened and shut with nothing on it.
        //
        // Headers are also the one thing worth sending early on their own. A response whose body
        // arrives a segment at a time - see HlsFlattenedStream - leaves a renderer waiting on a
        // status line it could have had immediately, and some give up before it comes.
        //
        // The flushes callers do after writing a body are still theirs to do; this only covers the
        // head of the response.
        output.flush()
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

    fun writeError(output: OutputStream, status: Int, statusText: String) {
        writeHeaders(output, status, statusText, mapOf("Content-Length" to "0"))
        output.flush()
    }
}
