package com.uacastplayer.data.cast

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyResponseServingTest {

    private val httpServer = ProxyHttpServer(
        onRequest = { _, _ -> },
        isRequestAuthorized = { true },
    )
    private val serving = ProxyResponseServing(httpServer)

    @Test fun `failed manifest body read returns 502 before headers are committed`() {
        assertFailedPlaylistResponse(partial = false)
    }

    @Test fun `partial manifest body never leaks as a successful playlist`() {
        assertFailedPlaylistResponse(partial = true)
    }

    private fun assertFailedPlaylistResponse(partial: Boolean) {
        var prefixPending = partial
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (!prefixPending) throw IOException("private provider detail")
                prefixPending = false
                sink.writeUtf8("#EXTM3U\n")
                return 8
            }
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() = Unit
        }.buffer()
        val body = object : ResponseBody() {
            override fun contentLength() = -1L
            override fun contentType() = null
            override fun source() = source
        }
        val upstream = response(body = byteArrayOf()).newBuilder().body(body).build()
        val output = ByteArrayOutputStream()
        upstream.use { assertNull(serving.readPlaylistText(it, output)) }
        val response = output.toString(Charsets.UTF_8.name())
        assertTrue(response.startsWith("HTTP/1.1 502 Bad Gateway\r\n"))
        assertTrue(response.contains("Content-Length: 0"))
        assertTrue(response.contains("Access-Control-Allow-Origin: *"))
        assertFalse(response.contains("#EXTM3U"))
        assertFalse(response.contains("private provider detail"))
        assertEquals(0L, serving.bytesServedToReceiver())
    }

    @Test
    fun `counted streaming body advances progress for every delivered write`() {
        val output = ByteArrayOutputStream()
        val body = serving.countedBody(output)

        body.write(byteArrayOf(1, 2, 3))
        body.write(4)

        assertArrayEquals(byteArrayOf(1, 2, 3, 4), output.toByteArray())
        assertEquals(4L, serving.bytesServedToReceiver())
    }

    @Test
    fun `chunked output frames payload and terminates the live response`() {
        val output = ByteArrayOutputStream()
        val chunked = ChunkedOutputStream(output)

        chunked.write("hello".toByteArray())
        chunked.finish()

        assertEquals("5\r\nhello\r\n0\r\n\r\n", output.toString(Charsets.ISO_8859_1.name()))
    }

    @Test
    fun `chunked output finish is idempotent`() {
        val output = ByteArrayOutputStream()
        val chunked = ChunkedOutputStream(output)

        chunked.write(byteArrayOf(1, 2))
        chunked.finish()
        chunked.finish()

        assertEquals("2\r\n\u0001\u0002\r\n0\r\n\r\n", output.toString(Charsets.ISO_8859_1.name()))
    }

    @Test
    fun `failed streaming write is not reported as delivered`() {
        val output = FailAfterFirstWriteOutputStream()
        val body = serving.countedBody(output)

        body.write(byteArrayOf(1, 2, 3))
        assertThrows(IOException::class.java) { body.write(byteArrayOf(4, 5)) }

        assertEquals(3L, serving.bytesServedToReceiver())
    }

    @Test
    fun `playlist HEAD declares body but does not send or count it`() {
        val output = ByteArrayOutputStream()

        serving.writePlaylistText("#EXTM3U", "HEAD", output)

        val response = output.toString(Charsets.ISO_8859_1.name())
        assertTrue(response.contains("Content-Length: 7"))
        assertTrue(response.endsWith("\r\n\r\n"))
        assertEquals(0L, serving.bytesServedToReceiver())
    }

    @Test
    fun `passthrough preserves range headers and counts only its body`() {
        val payload = byteArrayOf(11, 12, 13)
        val output = ByteArrayOutputStream()
        val response = response(
            code = 206,
            message = "Partial Content",
            body = payload,
            headers = mapOf(
                "Content-Type" to "video/mp2t",
                "Content-Range" to "bytes 2-4/10",
                "Accept-Ranges" to "bytes",
                "Content-Length" to payload.size.toString(),
            ),
        )

        response.use { serving.servePassthrough("resource-hash", it, "GET", output) }

        val raw = output.toByteArray()
        val text = raw.toString(Charsets.ISO_8859_1)
        assertTrue(text.startsWith("HTTP/1.1 206 Partial Content"))
        assertTrue(text.contains("Content-Range: bytes 2-4/10"))
        assertTrue(text.contains("Accept-Ranges: bytes"))
        assertTrue(raw.takeLast(payload.size).toByteArray().contentEquals(payload))
        assertEquals(payload.size.toLong(), serving.bytesServedToReceiver())
    }

    @Test
    fun `non GET passthrough never consumes an upstream body`() {
        val output = ByteArrayOutputStream()
        val upstream = response(body = byteArrayOf(1, 2, 3))

        upstream.use { serving.servePassthrough("resource-hash", it, "HEAD", output) }

        val text = output.toString(Charsets.ISO_8859_1.name())
        assertFalse(text.endsWith("\u0001\u0002\u0003"))
        assertEquals(0L, serving.bytesServedToReceiver())
    }

    @Test
    fun `generic passthrough MIME is corrected when the body is MPEG TS`() {
        val payload = ByteArray(188 * 2).also {
            it[0] = 0x47
            it[188] = 0x47
        }
        val output = ByteArrayOutputStream()
        val upstream = response(
            body = payload,
            headers = mapOf("Content-Type" to "application/octet-stream"),
        )

        upstream.use { serving.servePassthrough("resource-hash", it, "HEAD", output) }

        val text = output.toString(Charsets.ISO_8859_1.name())
        assertTrue(text.contains("Content-Type: video/mp2t"))
        assertFalse(text.contains("Content-Type: application/octet-stream"))
    }

    @Test
    fun `generic passthrough MIME stays generic when the body is not MPEG TS`() {
        val output = ByteArrayOutputStream()
        val upstream = response(
            body = "not a transport stream".toByteArray(),
            headers = mapOf("Content-Type" to "application/octet-stream"),
        )

        upstream.use { serving.servePassthrough("resource-hash", it, "HEAD", output) }

        val text = output.toString(Charsets.ISO_8859_1.name())
        assertTrue(text.contains("Content-Type: application/octet-stream"))
    }

    private fun response(
        code: Int = 200,
        message: String = "OK",
        body: ByteArray,
        headers: Map<String, String> = emptyMap(),
    ): Response = Response.Builder()
        .request(Request.Builder().url("https://origin.example/media.ts").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(message)
        .apply { headers.forEach { (name, value) -> header(name, value) } }
        .body(body.toResponseBody())
        .build()

    private class FailAfterFirstWriteOutputStream : OutputStream() {
        private var writes = 0

        override fun write(byte: Int) {
            error("Single-byte writes are not expected")
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (writes++ > 0) throw IOException("receiver disconnected")
        }
    }
}
