package com.uacastplayer.data.cast

import android.os.CancellationSignal
import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.data.media.LocalVideoHandle
import com.uacastplayer.data.media.LocalVideoSource
import com.uacastplayer.media.LocalVideo
import com.uacastplayer.proxy.LocalVideoRange
import java.io.IOException
import java.io.OutputStream

private const val HTTP_NOT_FOUND = 404
private const val HTTP_RANGE_NOT_SATISFIABLE = 416
private const val HTTP_SERVICE_UNAVAILABLE = 503
private const val HTTP_PARTIAL_CONTENT = 206
private const val HTTP_OK = 200

/** Finite, seekable file responses. HTTP admission/token limits remain owned by ProxyHttpServer. */
internal class LocalVideoResponseServing(
    private val source: LocalVideoSource?,
    private val responses: ProxyResponseServing,
) {
    private val lock = Any()
    private var current: LocalVideo? = null
    private val readers = mutableSetOf<LocalVideoRead>()

    fun select(video: LocalVideo?) {
        val retired = synchronized(lock) {
            if (current == video) return
            current = video
            readers.toList().also { readers.clear() }
        }
        retired.forEach(LocalVideoRead::close)
    }

    fun stop() {
        val retired = synchronized(lock) {
            current = null
            readers.toList().also { readers.clear() }
        }
        retired.forEach(LocalVideoRead::close)
    }

    fun serve(video: LocalVideo, request: ParsedRequest, output: OutputStream) {
        val read = synchronized(lock) {
            if (current != video || source == null) null else LocalVideoRead().also { readers += it }
        }
        if (read == null) {
            responses.writeError(output, HTTP_NOT_FOUND, "Not Found")
            return
        }
        var headersSent = false
        try {
            val handle = read.attach(checkNotNull(source).open(video, read.cancellation))
            respond(video, request, output, handle) { headersSent = true }
        } catch (_: IOException) {
            if (!headersSent) responses.writeError(output, HTTP_NOT_FOUND, "Not Found")
        } catch (_: SecurityException) {
            if (!headersSent) responses.writeError(output, HTTP_NOT_FOUND, "Not Found")
        } catch (_: IllegalStateException) {
            if (!headersSent) responses.writeError(output, HTTP_SERVICE_UNAVAILABLE, "Service Unavailable")
        } finally {
            read.close()
            synchronized(lock) { readers -= read }
        }
    }

    private fun respond(
        video: LocalVideo, request: ParsedRequest, output: OutputStream, handle: LocalVideoHandle,
        beforeHeaders: () -> Unit,
    ) {
        val range = LocalVideoRange.parse(request.headers["range"], handle.length)
        if (range == null) {
            beforeHeaders()
            responses.writeHeaders(output, HTTP_RANGE_NOT_SATISFIABLE, "Range Not Satisfiable", mapOf(
                "Content-Range" to "bytes */${handle.length}", "Content-Length" to "0",
            ))
        } else {
            if (request.method != "HEAD") handle.seek(range.start)
            val partial = request.headers.containsKey("range")
            beforeHeaders()
            responses.writeHeaders(output, if (partial) HTTP_PARTIAL_CONTENT else HTTP_OK,
                if (partial) "Partial Content" else "OK", localVideoHeaders(video, handle.length, range, partial))
            if (request.method == "GET") copyRange(handle, range, responses.countedBody(output))
            output.flush()
        }
    }

    private fun copyRange(handle: LocalVideoHandle, range: LocalVideoRange, output: OutputStream) {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var remaining = range.length
        while (remaining > 0) {
            val count = handle.input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count <= 0) throw IOException("Video ended before its declared length")
            output.write(buffer, 0, count)
            remaining -= count
        }
    }

    private companion object { const val COPY_BUFFER_BYTES = 64 * 1024 }
}

private fun localVideoHeaders(video: LocalVideo, length: Long, range: LocalVideoRange, partial: Boolean) =
    buildMap {
        put("Content-Type", video.mimeType)
        put("Content-Length", range.length.toString())
        put("Accept-Ranges", "bytes")
        put("Cache-Control", "no-store")
        put("transferMode.dlna.org", "Streaming")
        put("contentFeatures.dlna.org", "DLNA.ORG_OP=01;DLNA.ORG_CI=0")
        if (partial) put("Content-Range", "bytes ${range.start}-${range.end}/$length")
    }

/** Registered before provider access, so stop cancels an in-flight open as well as an attached FD. */
private class LocalVideoRead {
    val cancellation = CancellationSignal()
    private var handle: LocalVideoHandle? = null
    private var closed = false

    @Synchronized fun attach(opened: LocalVideoHandle): LocalVideoHandle {
        if (closed) {
            opened.close()
            throw IOException("Video request was retired")
        }
        handle = opened
        return opened
    }

    fun close() {
        val retired = synchronized(this) {
            if (closed) return
            closed = true
            handle.also { handle = null }
        }
        // A provider callback can throw. It must not suppress FD closure or abort server teardown.
        runCatchingNonFatal { cancellation.cancel() }
        runCatchingNonFatal { retired?.close() }
    }
}
