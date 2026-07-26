package com.uacastplayer.data.cast

import com.uacastplayer.log.AppLog
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread

private const val TAG = "ProxyHttpServer"
private const val MAX_HEADER_BYTES = 16 * 1024
private const val THREAD_POOL_SIZE = 6
private const val SOCKET_READ_TIMEOUT_MILLIS = 15_000
private const val HTTP_NO_CONTENT = 204
private const val CORS_MAX_AGE_SECONDS = "86400"

internal data class ParsedRequest(val method: String, val path: String, val headers: Map<String, String>)

/**
 * Raw-socket HTTP/1.1 server: owns the accept loop, request parsing, and the low-level response
 * writers (headers, CORS preflight, plain errors). Knows nothing about resources, remux sessions,
 * or playlists - every GET/HEAD request that isn't a CORS preflight or an unsupported method is
 * handed to [onRequest], which is responsible for writing a response to it. Always binds all
 * interfaces (`0.0.0.0`) regardless of the `host` a caller later builds URLs against - the two are
 * unrelated, `host` only ever describes which interface a URL should point *at* for the receiver.
 */
internal class ProxyHttpServer(private val onRequest: (ParsedRequest, OutputStream) -> Unit) {

    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null
    private var acceptThread: Thread? = null
    // Fixed once in start() rather than read live off serverSocket?.localPort - that getter goes
    // null the instant stop() runs, and a caller racing a concurrent stop() would otherwise
    // silently see a null port instead of either a real port or a loud failure.
    @Volatile private var boundPort: Int? = null
    @Volatile private var running = false

    val port: Int? get() = boundPort
    val isRunning: Boolean get() = running

    fun start(): Int {
        stop()
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
    }

    private fun handleConnection(socket: Socket) {
        socket.use {
            try {
                socket.soTimeout = SOCKET_READ_TIMEOUT_MILLIS
                val input = BufferedInputStream(socket.getInputStream())
                val output = BufferedOutputStream(socket.getOutputStream())
                val request = readRequest(input)
                when {
                    request == null -> writeError(output, 400, "Bad Request")
                    request.method == "OPTIONS" ->
                        writeCorsPreflight(output, request.headers["access-control-request-headers"])
                    request.method != "GET" && request.method != "HEAD" ->
                        writeError(output, 405, "Method Not Allowed")
                    else -> onRequest(request, output)
                }
            } catch (e: Exception) {
                AppLog.w(TAG) { "Connection error: ${e.javaClass.simpleName}" }
            }
        }
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
