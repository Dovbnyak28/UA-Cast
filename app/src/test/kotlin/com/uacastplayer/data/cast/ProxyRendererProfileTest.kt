package com.uacastplayer.data.cast

import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the proxy hands a renderer that is not a Chromecast.
 *
 * The two behaviours separated here used to be one flag, and a field report is what pulled them
 * apart. A Hisense VIDAA set was handed an HLS manifest over DLNA, fetched it once, refused
 * SetAVTransportURI and put "Archivo no compatible" on screen - never having asked for a single
 * segment. The proxy's own counters said the same from this side: `1 playlist(s), 0 segment(s),
 * 0MB`.
 *
 * The reason is in this project's own documents, in two places that had not been read against each
 * other. docs/PROXY_RULES.md explains the remux: "Chromecast's Default Receiver generally won't
 * play [a continuous raw MPEG-TS] directly ... it's a *container* problem", so raw TS is turned
 * into HLS for it. `AvTransportSoapBuilder` describes the opposite audience: "the ones that cannot
 * do HLS over DLNA at all - which is most sets that are not Samsung". A DMR's native food is
 * precisely what the remux converts away.
 *
 * Driven through the proxy's real socket with a real HTTP client rather than through an internal
 * seam - the routing under test is decided inside the request path, and there is no MockWebServer
 * in this project (see `CastRoutingIntegrationTest`), so the origin is a hand-rolled
 * [ServerSocket] too.
 */
class ProxyRendererProfileTest {

    private var proxy: ProxyServer? = null
    private var origin: Origin? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @After
    fun tearDown() {
        proxy?.stop()
        origin?.close()
    }

    /** Serves fixed bodies by path and counts what was actually requested. */
    private class Origin(private val routes: MutableMap<String, Pair<String, ByteArray>>) : AutoCloseable {
        private val socket = ServerSocket(0)
        private val worker = Executors.newCachedThreadPool()
        private val hits = mutableMapOf<String, AtomicInteger>()

        val port: Int get() = socket.localPort

        fun urlFor(path: String) = "http://127.0.0.1:$port$path"

        fun hitsFor(path: String): Int = synchronized(hits) { hits[path]?.get() ?: 0 }

        fun addRoute(path: String, contentType: String, body: ByteArray) {
            synchronized(routes) { routes[path] = contentType to body }
        }

        init {
            worker.execute {
                while (!socket.isClosed) {
                    try {
                        val client = socket.accept()
                        worker.execute { serve(client) }
                    } catch (_: IOException) {
                        return@execute
                    }
                }
            }
        }

        private fun serve(client: java.net.Socket) {
            client.use {
                val head = ByteArray(2048)
                val read = it.getInputStream().read(head)
                val requestLine = String(head, 0, maxOf(read, 0)).lineSequence().firstOrNull().orEmpty()
                val path = requestLine.split(' ').getOrNull(1).orEmpty().substringBefore('?')
                synchronized(hits) { hits.getOrPut(path) { AtomicInteger(0) }.incrementAndGet() }
                val out = it.getOutputStream()
                val route = synchronized(routes) { routes[path] }
                if (route == null) {
                    out.write("HTTP/1.1 404 Nope\r\nContent-Length: 0\r\n\r\n".toByteArray())
                } else {
                    val (type, body) = route
                    val header = "HTTP/1.1 200 OK\r\nContent-Type: $type\r\n" +
                        "Content-Length: ${body.size}\r\n\r\n"
                    out.write(header.toByteArray())
                    out.write(body)
                }
                out.flush()
            }
        }

        override fun close() {
            runCatching { socket.close() }
            worker.shutdownNow()
        }
    }

    /**
     * Well-formed MPEG-TS: 0x47 every 188 bytes, which is what the proxy sniffs for when deciding
     * whether an upstream is a raw stream. Built rather than faked, so the sniff under test is the
     * real one.
     */
    private fun tsBytes(packets: Int = 40): ByteArray = ByteArray(packets * TS_PACKET_SIZE) { index ->
        if (index % TS_PACKET_SIZE == 0) 0x47.toByte() else (index % 251).toByte()
    }

    /**
     * Serves a channel whose top-level URL is the IPTV "wrapper playlist" - an m3u8 whose whole
     * content is one pointer at an endless stream, with no `#EXT-X-TARGETDURATION` to make it a
     * real media playlist (see [com.uacastplayer.proxy.PlaylistUnwrapPolicy]).
     */
    private fun fetchWrappedChannel(unwrap: Boolean, remux: Boolean): Pair<String, String?> {
        // The origin is bound first so the wrapper body can name the origin's own port - which is
        // the whole shape under test: a playlist whose content is a pointer somewhere else.
        val origin = Origin(mutableMapOf("/live.ts" to ("video/mp2t" to tsBytes()))).also { this.origin = it }
        origin.addRoute(
            path = "/wrapper.m3u8",
            contentType = "application/vnd.apple.mpegurl",
            body = "#EXTM3U\n#EXTINF:-1,Channel\n${origin.urlFor("/live.ts")}\n".toByteArray(),
        )
        return fetch(origin.urlFor("/wrapper.m3u8"), unwrap, remux)
    }

    /** Returns the response body and its Content-Type. */
    private fun fetch(originUrl: String, unwrap: Boolean, remux: Boolean): Pair<String, String?> {
        val server = ProxyServer(OkHttpClient()).also { proxy = it }
        server.start(
            sessionToken = "session",
            host = "127.0.0.1",
            remuxEnabled = remux,
            unwrapWrapperPlaylists = unwrap,
        )
        val resourceId = server.registerPlaylist(originUrl)
        val request = Request.Builder().url(server.buildLocalUrl(resourceId)).build()
        client.newCall(request).execute().use { response ->
            return response.body.string() to response.header("Content-Type")
        }
    }

    /**
     * The DLNA profile: the pointer is followed and the stream behind it is passed through
     * untouched, so the renderer receives continuous MPEG-TS rather than a manifest.
     */
    @Test
    fun `a wrapper playlist is unwrapped to a passthrough stream when the remux is off`() {
        val (body, contentType) = fetchWrappedChannel(unwrap = true, remux = false)

        assertEquals("video/mp2t", contentType)
        assertFalse("a manifest is the one thing this renderer cannot read", body.contains("#EXTM3U"))
        assertEquals("the pointed-at stream must actually be fetched", 1, origin!!.hitsFor("/live.ts"))
    }

    /**
     * The control, and the bug: with the unwrap tied to the remux flag, turning the remux off
     * turned the unwrap off with it - and the renderer got a manifest whose single "segment" is an
     * endless raw stream. This is the shape the Hisense refused.
     */
    @Test
    fun `without the unwrap the same channel comes back as a manifest`() {
        val (body, _) = fetchWrappedChannel(unwrap = false, remux = false)

        assertTrue("expected a rewritten manifest, got:\n${body.take(200)}", body.contains("#EXTM3U"))
        assertEquals("and the stream itself is never fetched", 0, origin!!.hitsFor("/live.ts"))
    }

    /**
     * A real media playlist is not a pointer, and must go through the normal rewrite path whatever
     * the profile - unwrapping one would hand the renderer a single segment as if it were the
     * whole channel.
     */
    @Test
    fun `a real media playlist is never unwrapped`() {
        val media = "#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,\nseg1.ts\n"
        val origin = Origin(
            mutableMapOf("/media.m3u8" to ("application/vnd.apple.mpegurl" to media.toByteArray())),
        ).also { this.origin = it }

        val (body, _) = fetch(origin.urlFor("/media.m3u8"), unwrap = true, remux = false)

        assertTrue(
            "expected the rewritten media playlist, got:\n${body.take(200)}",
            body.contains("#EXT-X-TARGETDURATION"),
        )
    }

    private companion object {
        const val TS_PACKET_SIZE = 188
    }
}
