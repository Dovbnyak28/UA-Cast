package com.uacastplayer.data.cast

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyRemuxOwnershipTest {
    @Test fun `selecting new root closes old remux before fetching the new origin`() = Fixture().use { f ->
        val a = f.register("a")
        val body = LiveBody()
        val previous = f.start(a, body = body)
        assertTrue(body.reading.await(3, TimeUnit.SECONDS))
        val b = f.register("b")
        assertTrue("old origin was still open when new channel can start fetching", body.closed.get())
        assertSame(previous, f.registry.remuxSessionFor(a))
        assertNull(f.registry.remuxSessionForPlaylist(b))
    }

    @Test fun `returning to draining A requires a fresh producer not its frozen playlist`() = Fixture().use { f ->
        val a = f.register("a")
        val body = LiveBody()
        val previous = f.start(a, body = body)
        assertTrue(body.reading.await(3, TimeUnit.SECONDS))
        f.register("b")
        f.register("a")
        assertNull(f.registry.remuxSessionForPlaylist(a))
        assertSame(previous, f.registry.remuxSessionFor(a))
    }

    @Test fun `repeating the root does not stop its existing live producer`() = Fixture().use { f ->
        val a = f.register("a")
        val body = LiveBody()
        val previous = f.start(a, body = body)
        assertTrue(body.reading.await(3, TimeUnit.SECONDS))
        f.register("a")
        assertTrue(!body.closed.get())
        assertSame(previous, f.registry.remuxSessionForPlaylist(a))
    }

    @Test fun `late A response cannot replace active B and is closed`() = Fixture().use { f ->
        val a = f.register("a")
        val stale = f.registry.captureRemuxLease(a)
        val b = f.register("b")
        val current = f.start(b)
        val rejected = TrackedBody()
        assertNull(f.start(a, stale, rejected))
        assertTrue(rejected.closed.get())
        assertSame(current, f.registry.remuxSessionForPlaylist(b))
        assertNull(f.registry.remuxSessionFor(a))
    }

    @Test fun `A B A rejects the original A lease despite equal resource IDs`() = Fixture().use { f ->
        val a = f.register("a")
        val stale = f.registry.captureRemuxLease(a)
        f.register("b")
        val again = f.register("a")
        val currentLease = f.registry.captureRemuxLease(again)
        assertNotSame(stale, currentLease)
        val current = f.start(again, currentLease)
        val rejected = TrackedBody()
        assertNull(f.start(a, stale, rejected))
        assertTrue(rejected.closed.get())
        assertSame(current, f.registry.remuxSessionForPlaylist(again))
    }

    @Test fun `restart with the same resource invalidates earlier lease`() = Fixture().use { f ->
        val a = f.register("a")
        val stale = f.registry.captureRemuxLease(a)
        f.registry.clearAll()
        f.registry.openSession()
        val again = f.register("a")
        val current = f.start(again)
        val rejected = TrackedBody()
        assertNull(f.start(a, stale, rejected))
        assertTrue(rejected.closed.get())
        assertSame(current, f.registry.remuxSessionForPlaylist(again))
    }

    @Test fun `same root registration keeps duplicate request lease valid`() = Fixture().use { f ->
        val a = f.register("a")
        val first = f.registry.captureRemuxLease(a)
        f.register("a")
        assertSame(first, f.registry.captureRemuxLease(a))
        assertNotNull(f.start(a, first))
    }

    @Test fun `null lease cannot launch an unowned producer`() = Fixture().use { f ->
        val a = f.register("a")
        val body = TrackedBody()
        assertNull(f.start(a, null, body))
        assertTrue(body.closed.get())
    }

    @Test fun `previous ready remux is retained for draining but cannot become producer again`() = Fixture().use { f ->
        val a = f.register("a")
        val oldLease = f.registry.captureRemuxLease(a)
        val previous = f.start(a, oldLease)
        val b = f.register("b")
        val current = f.start(b)
        assertSame(previous, f.registry.remuxSessionFor(a))
        assertSame(previous, f.registry.remuxSessionForPlaylist(a))
        assertNull(f.registry.captureRemuxLease(a))
        assertNull(f.start(a, oldLease))
        assertSame(current, f.registry.remuxSessionForPlaylist(b))
    }

    @Test fun `current nested playlist may remux under its root ownership`() = Fixture().use { f ->
        val root = f.register("master")
        val parent = checkNotNull(f.registry.get(root))
        var childId: String? = null
        val rootLease = f.registry.captureRemuxLease(root)
        f.registry.rewriteManifest("#EXTM3U\nchild.m3u8", parent.originalUrl, parent, rootLease) { id ->
            childId = id
            id
        }
        val child = checkNotNull(childId)
        val lease = f.registry.captureRemuxLease(child)
        assertSame(f.registry.captureRemuxLease(root), lease)
        assertNotNull(f.start(child, lease))
        f.register("next")
        assertNull(f.start(child, lease))
    }

    @Test fun `stopped registry rejects lease even if external running flag is true`() = Fixture().use { f ->
        val a = f.register("a")
        val lease = f.registry.captureRemuxLease(a)
        f.registry.clearAll()
        assertNull(f.start(a, lease))
    }

    private class Fixture : AutoCloseable {
        private val http = OkHttpClient()
        val registry = ProxyResourceRegistry(http)
        private val sessions = mutableSetOf<RawTsRemuxSession>()

        fun register(name: String) = registry.registerPlaylist("https://synthetic.test/$name.m3u8", null, null)

        fun start(
            id: String,
            lease: RemuxRequestLease? = registry.captureRemuxLease(id),
            body: ResponseBody = TrackedBody(),
        ): RawTsRemuxSession? {
            val response = Response.Builder().request(Request.Builder().url("https://synthetic.test/raw.ts").build())
                .protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
            return registry.startRemuxSession(id, response, lease, { resource, sequence -> "$resource/$sequence" }) {
                true
            }?.also { sessions += it }
        }

        override fun close() {
            registry.clearAll()
            sessions.forEach { it.awaitStopped() }
            http.connectionPool.evictAll()
            http.dispatcher.executorService.shutdownNow()
        }
    }

    private class LiveBody : ResponseBody() {
        val reading = CountDownLatch(1)
        val closed = AtomicBoolean()
        private val bytes = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                reading.countDown()
                while (!closed.get()) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5))
                return -1
            }
            override fun close() { closed.set(true) }
            override fun timeout() = Timeout.NONE
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength() = -1L
        override fun source() = bytes
    }

    private class TrackedBody : ResponseBody() {
        val closed = AtomicBoolean()
        private val bytes = object : ForwardingSource(Buffer()) {
            override fun close() {
                closed.set(true)
                super.close()
            }
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = 0
        override fun source() = bytes
    }
}
