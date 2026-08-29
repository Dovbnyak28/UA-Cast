package com.uacastplayer.testsupport

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal raw-socket HTTP origin for instrumented tests: serves a fixed M3U playlist at
 * [PLAYLIST_PATH] and a valid, empty HLS VOD for any other path, so both the playlist fetch and
 * ExoPlayer's own HTTP data source have a real origin to talk to on 127.0.0.1. The empty VOD ends
 * cleanly instead of feeding Media3 malformed bytes: these tests exercise lifecycle/resize state,
 * not decoding, and repeated parser recovery from deliberately corrupt media used to saturate a
 * weak device before the later test methods could even parse their three-channel setup playlist.
 */
class FakeOriginServer private constructor(
    private val serverSocket: ServerSocket,
    private val playlistBody: String,
) {
    private val executor = Executors.newFixedThreadPool(WORKER_COUNT)
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()

    @Volatile
    private var running = true

    private val playlistRequests = AtomicInteger()
    private val completedResponses = AtomicInteger()
    private val failedResponses = AtomicInteger()

    val playlistRequestCount: Int get() = playlistRequests.get()
    val completedResponseCount: Int get() = completedResponses.get()
    val failedResponseCount: Int get() = failedResponses.get()
    val activeSocketCount: Int get() = activeSockets.size

    private val port get() = serverSocket.localPort

    fun playlistUrl(): String = "http://127.0.0.1:$port$PLAYLIST_PATH"

    private fun start() {
        executor.execute {
            while (running) {
                val socket = try {
                    serverSocket.accept()
                } catch (_: Exception) {
                    break
                }
                activeSockets += socket
                executor.execute { handle(socket) }
            }
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                val path = requestLine.split(" ").getOrNull(1) ?: "/"
                if (path == PLAYLIST_PATH) playlistRequests.incrementAndGet()
                val body = if (path == PLAYLIST_PATH) playlistBody else EMPTY_HLS_STREAM
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                val response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/octet-stream\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Connection: close\r\n\r\n"
                s.getOutputStream().write(response.toByteArray(StandardCharsets.UTF_8))
                s.getOutputStream().write(bytes)
                s.getOutputStream().flush()
                completedResponses.incrementAndGet()
            }
        } catch (_: Exception) {
            failedResponses.incrementAndGet()
        } finally {
            activeSockets -= socket
        }
    }

    fun shutdown() {
        running = false
        closeQuietly(serverSocket)
        activeSockets.toList().forEach(::closeQuietly)
        executor.shutdownNow()
        try {
            executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        private const val PLAYLIST_PATH = "/playlist.m3u8"
        private val EMPTY_HLS_STREAM = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-PLAYLIST-TYPE:VOD
            #EXT-X-TARGETDURATION:1
            #EXT-X-MEDIA-SEQUENCE:0
            #EXT-X-ENDLIST
        """.trimIndent()
        private const val WORKER_COUNT = 4
        private const val SHUTDOWN_TIMEOUT_SECONDS = 2L

        private fun closeQuietly(closeable: AutoCloseable) {
            try {
                closeable.close()
            } catch (_: Exception) {
                // Test teardown is best-effort; every owned socket is attempted independently.
            }
        }

        /** Starts a server on an ephemeral local port serving [channelCount] channels, each
         * named "Channel N" and pointed at this same server for its (fake) stream. */
        fun startWithChannels(channelCount: Int): FakeOriginServer {
            val serverSocket = ServerSocket(0)
            val port = serverSocket.localPort
            val playlist = buildString {
                appendLine("#EXTM3U")
                repeat(channelCount) { i ->
                    appendLine("#EXTINF:-1 group-title=\"Test\",Channel ${i + 1}")
                    appendLine("http://127.0.0.1:$port/stream${i + 1}.ts")
                }
            }
            val server = FakeOriginServer(serverSocket, playlist)
            server.start()
            return server
        }
    }
}
