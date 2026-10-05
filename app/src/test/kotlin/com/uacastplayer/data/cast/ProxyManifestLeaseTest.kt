package com.uacastplayer.data.cast

import java.io.ByteArrayOutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyManifestLeaseTest {
    private fun root(name: String) = ResourceEntry(RESOURCE_TYPE_PLAYLIST, "https://x/$name.m3u8", "Agent", null)
    private fun playlist(first: Int, count: Int) = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n" +
        (first until first + count).joinToString("\n") {
        "#EXTINF:6,\nhttps://x/$it.ts"
    }

    @Test fun `all 1000 emitted segment URLs and root remain fetchable over HTTP`() {
        val manifest = playlist(0, 1_000) + "\n#EXT-X-ENDLIST"
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = if (request.url.encodedPath.endsWith(".m3u8")) manifest else "segment"
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        val server = ProxyServer(client)
        try {
            server.start("lease-test", "127.0.0.1", remuxEnabled = false)
            val rootUrl = server.buildLocalUrl(server.registerPlaylist("https://x/vod.m3u8"))
            val firstResponse = fetch(rootUrl)
            assertTrue(firstResponse.startsWith("HTTP/1.1 200"))
            val urls = firstResponse.lineSequence().filter { it.startsWith("http://") }.toList()
            assertEquals(firstResponse.take(500), 1_000, urls.size)
            urls.forEach { assertTrue("unfetchable $it", fetch(it).startsWith("HTTP/1.1 200")) }
            assertTrue(fetch(rootUrl).startsWith("HTTP/1.1 200"))
        } finally {
            server.stop()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
        }
    }

    @Test fun `live windows keep one previous revision but retire older segments`() {
        val leases = ProxyManifestResources()
        val parent = root("live")
        leases.beginRoot(parent)
        val old = leases.rewrite(playlist(0, 600), parent.originalUrl, parent) { it }!!.lineSequence()
            .first { !it.startsWith("#") }
        leases.rewrite(playlist(600, 600), parent.originalUrl, parent) { it }
        assertNotNull(leases.get(old))
        leases.rewrite(playlist(1_200, 600), parent.originalUrl, parent) { it }
        assertNull(leases.get(old))
        assertNotNull(leases.get(parent.resourceId()))
        assertEquals(1_201, leases.snapshot().size)
    }

    @Test fun `master audio and video graphs survive child refresh and channel replacement is bounded`() {
        val leases = ProxyManifestResources()
        val master = root("master")
        leases.beginRoot(master)
        leases.rewrite("#EXTM3U\nvideo.m3u8\naudio.m3u8", master.originalUrl, master) { it }
        val video = root("video")
        val audio = root("audio")
        assertNotNull(leases.rewrite(playlist(0, 600), video.originalUrl, video) { it })
        assertNotNull(leases.rewrite(playlist(600, 600), audio.originalUrl, audio) { it })
        assertNotNull(leases.get(master.resourceId()))
        assertEquals(1_203, leases.snapshot().size)
        leases.beginRoot(root("next"))
        assertNotNull(leases.get(video.resourceId()))
        assertNull(leases.rewrite(playlist(0, 5), master.originalUrl, master) { it })
        leases.beginRoot(root("third"))
        assertNull(leases.get(video.resourceId()))
        leases.clear()
        assertTrue(leases.snapshot().isEmpty())
        assertNull(leases.rewrite(playlist(0, 5), master.originalUrl, master) { it })
        assertTrue(leases.snapshot().isEmpty())
    }

    @Test fun `over budget rewrite publishes nothing and does not damage previous manifest`() {
        val leases = ProxyManifestResources()
        val parent = root("budget")
        leases.beginRoot(parent)
        assertNotNull(leases.rewrite(playlist(0, 2), parent.originalUrl, parent) { it })
        val before = leases.snapshot()
        val excessive = playlist(0, ProxyManifestResources.MAX_ENTRIES + 1)
        assertNull(leases.rewrite(excessive, parent.originalUrl, parent) { it })
        assertEquals(before, leases.snapshot())
        assertNotNull(leases.rewrite("#EXTM3U", parent.originalUrl, parent) { it })
    }

    private fun fetch(url: String): String {
        val uri = URI(url)
        return Socket(uri.host, uri.port).use { socket ->
            socket.soTimeout = 5_000
            socket.getOutputStream().write("GET ${uri.rawPath} HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
            val received = ByteArrayOutputStream()
            try {
                socket.getInputStream().copyTo(received)
                received.toString(Charsets.UTF_8.name())
            } catch (timeout: SocketTimeoutException) {
                // Keep the original deadline: capture the serving threads before finally stops
                // the server, so a CI timeout identifies the blocked phase rather than hiding it.
                val servingThreads = Thread.getAllStackTraces().filter { (thread, stack) ->
                    thread.name.startsWith("pool-") ||
                        stack.any { it.className.startsWith("com.uacastplayer.data.cast.") }
                }.entries.joinToString("\n\n") { (thread, stack) ->
                    "${thread.name} (${thread.state})\n${stack.take(12).joinToString("\n")}"
                }
                throw AssertionError(
                    "Timed out fetching ${uri.rawPath}, ${socket.localPort}->${socket.port}\n" +
                        "received=${received.size()} bytes\n$servingThreads",
                    timeout,
                )
            }
        }
    }
}
