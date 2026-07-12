package com.uacastplayer.data.cast

import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.log.AppLog
import com.uacastplayer.proxy.M3u8Rewriter
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val TAG = "ProxyServer"
private const val MAX_HEADER_BYTES = 16 * 1024
private const val MAX_RESOURCES = 512
private const val THREAD_POOL_SIZE = 6

private const val RESOURCE_TYPE_PLAYLIST = "playlist"
private const val RESOURCE_TYPE_MEDIA = "media"

private data class ParsedRequest(val method: String, val path: String, val headers: Map<String, String>)
private data class ResourceEntry(val type: String, val originalUrl: String)

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
    @Volatile private var running = false

    private val resources = object : LinkedHashMap<String, ResourceEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ResourceEntry>?): Boolean =
            size > MAX_RESOURCES
    }
    private val resourcesLock = Any()

    val port: Int? get() = serverSocket?.localPort

    fun start(sessionToken: String, host: String): Int {
        stop()
        this.sessionToken = sessionToken
        this.host = host
        val socket = ServerSocket(0, 50, InetAddress.getByName("0.0.0.0"))
        serverSocket = socket
        val pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE)
        executor = pool
        running = true
        acceptThread = thread(name = "ProxyServer-accept") {
            while (running) {
                try {
                    val client = socket.accept()
                    pool.submit { handleConnection(client) }
                } catch (e: Exception) {
                    if (running) AppLog.w(TAG) { "accept() failed: ${e.javaClass.simpleName}" }
                }
            }
        }
        return socket.localPort
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        executor?.shutdownNow()
        executor = null
        synchronized(resourcesLock) { resources.clear() }
    }

    fun registerPlaylist(url: String): String = registerResource(RESOURCE_TYPE_PLAYLIST, url)

    private fun registerResource(type: String, url: String): String {
        val id = Fingerprint.of("$type:$url")
        synchronized(resourcesLock) { resources[id] = ResourceEntry(type, url) }
        return id
    }

    fun buildLocalUrl(resourceId: String): String = "http://$host:$port/hls/$sessionToken/$resourceId"

    private fun handleConnection(socket: Socket) {
        socket.use {
            try {
                socket.soTimeout = 15_000
                val input = BufferedInputStream(socket.getInputStream())
                val output = BufferedOutputStream(socket.getOutputStream())
                val request = readRequest(input)
                if (request == null) {
                    writeError(output, 400, "Bad Request")
                    return
                }
                if (request.method != "GET" && request.method != "HEAD") {
                    writeError(output, 405, "Method Not Allowed")
                    return
                }
                serveRequest(request, output)
            } catch (e: Exception) {
                AppLog.w(TAG) { "Connection error: ${e.javaClass.simpleName}" }
            }
        }
    }

    private fun serveRequest(request: ParsedRequest, output: OutputStream) {
        val segments = request.path.substringBefore('?').split('/').filter { it.isNotEmpty() }
        if (segments.size != 3 || segments[0] != "hls" || segments[1] != sessionToken) {
            writeError(output, 404, "Not Found")
            return
        }
        val resource = synchronized(resourcesLock) { resources[segments[2]] }
        if (resource == null) {
            writeError(output, 404, "Not Found")
            return
        }

        val upstreamRequest = Request.Builder().url(resource.originalUrl).apply {
            request.headers["range"]?.let { header("Range", it) }
        }.build()

        httpClient.newCall(upstreamRequest).execute().use { response ->
            if (resource.type == RESOURCE_TYPE_PLAYLIST) {
                servePlaylist(response, request.method, output)
            } else {
                servePassthrough(response, request.method, output)
            }
        }
    }

    private fun servePlaylist(response: Response, method: String, output: OutputStream) {
        val text = response.body?.string().orEmpty()
        val finalUrl = response.request.url.toString()
        val rewritten = M3u8Rewriter.rewrite(text, finalUrl) { absoluteUrl ->
            val type = if (looksLikePlaylist(absoluteUrl)) RESOURCE_TYPE_PLAYLIST else RESOURCE_TYPE_MEDIA
            buildLocalUrl(registerResource(type, absoluteUrl))
        }
        val bytes = rewritten.toByteArray(Charsets.UTF_8)
        writeHeaders(
            output, 200, "OK",
            mapOf("Content-Type" to "application/vnd.apple.mpegurl", "Content-Length" to bytes.size.toString()),
        )
        if (method == "GET") output.write(bytes)
        output.flush()
    }

    private fun servePassthrough(response: Response, method: String, output: OutputStream) {
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

    private fun looksLikePlaylist(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".m3u")
    }

    private fun writeHeaders(output: OutputStream, status: Int, statusText: String, headers: Map<String, String>) {
        val builder = StringBuilder("HTTP/1.1 $status $statusText\r\n")
        for ((key, value) in headers) builder.append("$key: $value\r\n")
        builder.append("Connection: close\r\n\r\n")
        output.write(builder.toString().toByteArray(Charsets.ISO_8859_1))
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
