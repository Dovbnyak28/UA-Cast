package com.uacastplayer.data.cast

import com.uacastplayer.log.AppLog
import com.uacastplayer.playlist.BoundedReadResult
import com.uacastplayer.playlist.BoundedTextReader
import com.uacastplayer.proxy.MAX_HLS_PLAYLIST_BYTES
import com.uacastplayer.proxy.MpegTsSniffer
import com.uacastplayer.proxy.ProxyServeRollup
import java.io.IOException
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Response

private const val TAG = "ProxyServer"
private const val HTTP_BAD_GATEWAY = 502
private const val HTTP_OK = 200
private const val HTTP_LAST_SUCCESS = 299
private const val TS_PACKET_SIZE_BYTES = 188L
private const val TS_CONTENT_TYPE_SNIFF_BYTES = TS_PACKET_SIZE_BYTES * 2
private val HTTP_OK_RANGE = HTTP_OK..HTTP_LAST_SUCCESS
private val GENERIC_BINARY_CONTENT_TYPES = setOf(
    "application/octet-stream",
    "application/binary",
    "binary/octet-stream",
)

/**
 * Owns the response-serving half of [ProxyServer]: response headers and bodies, delivered-byte
 * accounting, bounded playlist reads and the healthy-traffic log rollup.
 *
 * Routing deliberately remains in [ProxyServer]. Keeping the byte counter beside every body write
 * makes it much harder for a new route to silently bypass the progress signal consumed by the cast
 * stall watchdog. [countedBody] is the escape hatch for streaming producers such as
 * [HlsFlattenedStream], whose body is not available as one byte array or input stream.
 */
internal class ProxyResponseServing(
    private val httpServer: ProxyHttpServer,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** Monotonic for the process lifetime. The watchdog reads deltas, so a session restart must not
     * reset this and manufacture an apparent lack of progress. */
    private val bytesServed = AtomicLong(0)
    private val serveRollup = ProxyServeRollup()

    fun bytesServedToReceiver(): Long = bytesServed.get()

    fun flushRollup() {
        serveRollup.flush(now())?.let { summary -> AppLog.d(TAG) { summary.sentence() } }
    }

    fun writeError(output: OutputStream, status: Int, statusText: String) {
        httpServer.writeError(output, status, statusText)
    }

    fun writeHeaders(
        output: OutputStream,
        status: Int,
        statusText: String,
        headers: Map<String, String>,
    ) {
        httpServer.writeHeaders(output, status, statusText, headers)
    }

    fun writePlaylistText(text: String, method: String, output: OutputStream) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        writePlaylistBytes(bytes, method, output)
    }

    fun writeRewrittenPlaylist(
        text: String,
        rewrittenCount: Int,
        method: String,
        output: OutputStream,
    ) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        recordRewrittenPlaylist(rewrittenCount, bytes.size)
        writePlaylistBytes(bytes, method, output)
    }

    private fun writePlaylistBytes(bytes: ByteArray, method: String, output: OutputStream) {
        httpServer.writeHeaders(
            output,
            HTTP_OK,
            "OK",
            mapOf(
                "Content-Type" to "application/vnd.apple.mpegurl",
                "Content-Length" to bytes.size.toString(),
            ),
        )
        if (method == "GET") writeBody(bytes, output)
        output.flush()
    }

    fun writeRemuxSegment(bytes: ByteArray, method: String, output: OutputStream) {
        httpServer.writeHeaders(
            output,
            HTTP_OK,
            "OK",
            mapOf("Content-Type" to "video/MP2T", "Content-Length" to bytes.size.toString()),
        )
        if (method == "GET") writeBody(bytes, output)
        output.flush()
    }

    /** Null means an error response has already been written to [output]. */
    fun readPlaylistText(response: Response, output: OutputStream): String? =
        when (val bounded = BoundedTextReader.readText(response.body.byteStream(), MAX_HLS_PLAYLIST_BYTES)) {
            is BoundedReadResult.Success -> bounded.text
            BoundedReadResult.SizeLimitExceeded -> {
                AppLog.w(TAG) { "Upstream playlist exceeded $MAX_HLS_PLAYLIST_BYTES bytes; rejecting" }
                httpServer.writeError(output, HTTP_BAD_GATEWAY, "Bad Gateway")
                null
            }
        }

    /** Records one successful rewritten playlist without filling the diagnostics ring per poll. */
    private fun recordRewrittenPlaylist(rewrittenCount: Int, byteCount: Int) {
        if (rewrittenCount == 0) {
            AppLog.d(TAG) { "Rewritten playlist served: nothing to rewrite, ${byteCount}B" }
        } else {
            serveRollup.playlistServed(now())?.let { summary -> AppLog.d(TAG) { summary.sentence() } }
        }
    }

    fun servePassthrough(resourceId: String, response: Response, method: String, output: OutputStream) {
        // Resource ids are opaque SHA fingerprints. Never log the upstream path: IPTV credentials
        // are commonly embedded in it rather than in a query string.
        if (!response.isSuccessful) {
            AppLog.w(TAG) { "Passthrough upstream returned ${response.code} for resource $resourceId" }
        }
        val headers = linkedMapOf("Content-Type" to passthroughContentType(response))
        response.header("Content-Range")?.let { headers["Content-Range"] = it }
        response.header("Accept-Ranges")?.let { headers["Accept-Ranges"] = it }
        response.header("Content-Length")?.let { headers["Content-Length"] = it }
        httpServer.writeHeaders(output, response.code, response.message.ifEmpty { "OK" }, headers)

        // Keep partial progress when a receiver disconnects mid-segment. A returned byte count
        // would lose precisely the failure case diagnostics needs to distinguish.
        val copied = LongArray(1)
        var completed = false
        try {
            if (method == "GET") {
                response.body.byteStream().use { copyCounting(it, output, copied) }
            }
            output.flush()
            completed = true
        } finally {
            recordPassthrough(resourceId, response.code, copied[0], completed, expectedBody = method == "GET")
        }
    }

    /**
     * Corrects only an absent/generic origin type, and only when the body proves it is MPEG-TS.
     * IPTV endpoints routinely answer an endless transport stream as `application/octet-stream`;
     * forwarding that label makes strict DLNA renderers reject valid television before reading it.
     * A specific origin type is preserved even when it looks surprising: the proxy is not a general
     * MIME rewriter, and two TS sync bytes are evidence only for the generic binary case.
     */
    private fun passthroughContentType(response: Response): String {
        val declared = response.header("Content-Type")
        val normalized = declared?.substringBefore(';')?.trim()?.lowercase()
        val isGeneric = normalized.isNullOrEmpty() || normalized in GENERIC_BINARY_CONTENT_TYPES
        if (!isGeneric) return declared
        val looksLikeTs = try {
            MpegTsSniffer.looksLikeMpegTs(response.peekBody(TS_CONTENT_TYPE_SNIFF_BYTES).bytes())
        } catch (_: IOException) {
            false
        }
        return if (looksLikeTs) "video/mp2t" else declared ?: "application/octet-stream"
    }

    /**
     * Wraps a streaming response body so every successfully handed-off chunk advances the same
     * monotonic progress counter as playlists, passthrough media and remux segments. Headers must
     * still be written to the original output, otherwise they would be counted as media progress.
     */
    fun countedBody(output: OutputStream): OutputStream = DeliveredBodyOutputStream(output, bytesServed)

    private fun writeBody(body: ByteArray, output: OutputStream) {
        output.write(body)
        bytesServed.addAndGet(body.size.toLong())
    }

    private fun recordPassthrough(
        resourceId: String,
        code: Int,
        bytes: Long,
        completed: Boolean,
        expectedBody: Boolean,
    ) {
        if (code !in HTTP_OK_RANGE) return
        val cutShort = !completed
        val empty = expectedBody && bytes == 0L
        if (cutShort || empty) {
            val why = if (cutShort) "cut short" else "no bytes"
            AppLog.d(TAG) { "Passthrough served: $code, ${bytes}B of resource $resourceId ($why)" }
            return
        }
        serveRollup.segmentServed(bytes, now())?.let { summary -> AppLog.d(TAG) { summary.sentence() } }
    }

    private fun copyCounting(input: InputStream, output: OutputStream, progress: LongArray) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            progress[0] += read
            bytesServed.addAndGet(read.toLong())
        }
    }
}

private class DeliveredBodyOutputStream(
    output: OutputStream,
    private val deliveredBytes: AtomicLong,
) : FilterOutputStream(output) {

    override fun write(byte: Int) {
        out.write(byte)
        deliveredBytes.incrementAndGet()
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        out.write(bytes, offset, length)
        deliveredBytes.addAndGet(length.toLong())
    }
}
