package com.uacastplayer.testsupport

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/**
 * Minimal raw-socket HTTP origin for instrumented tests: serves a fixed M3U playlist at
 * [PLAYLIST_PATH] and a small dummy body for any other path, so both the playlist fetch and
 * ExoPlayer's own HTTP data source have a real (if not actually playable) origin to talk to on
 * 127.0.0.1 - no mocking library is on the androidTest classpath, and none of these tests need the
 * stream to actually decode.
 */
class FakeOriginServer private constructor(
    private val serverSocket: ServerSocket,
    private val playlistBody: String,
) {
    private val executor = Executors.newCachedThreadPool()

    @Volatile
    private var running = true

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
                executor.execute { handle(socket) }
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            val path = requestLine.split(" ").getOrNull(1) ?: "/"
            val body = if (path == PLAYLIST_PATH) playlistBody else DUMMY_SEGMENT
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
            s.getOutputStream().write(response.toByteArray(StandardCharsets.UTF_8))
            s.getOutputStream().write(bytes)
            s.getOutputStream().flush()
        }
    }

    fun shutdown() {
        running = false
        runCatching { serverSocket.close() }
        executor.shutdownNow()
    }

    companion object {
        private const val PLAYLIST_PATH = "/playlist.m3u8"
        private const val DUMMY_SEGMENT = "not a real stream - just enough bytes for a data source to connect to"

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
