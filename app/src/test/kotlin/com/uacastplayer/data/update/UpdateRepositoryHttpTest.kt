package com.uacastplayer.data.update

import com.uacastplayer.update.ReleaseLookup
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [UpdateRepository] over real HTTP, which had no test at all until this one.
 *
 * Everything about the update check was covered as pure functions - version comparison, the JSON
 * parser, the schedule, the status mapping - and the one piece that actually speaks to the network
 * was covered by nothing. That is where the 404 defect lived: `!response.isSuccessful` collapsed
 * "this repository has published no release" into the same `null` as a 5xx, and no test could see
 * it because no test ever gave the repository a response to read.
 *
 * There is no MockWebServer in this project (see `CastRoutingIntegrationTest`), so the server here
 * is a socket that answers exactly once. That is enough: the repository makes one request and the
 * whole question is what it does with the answer.
 */
class UpdateRepositoryHttpTest {

    /** Answers a single request with a canned status and body, then closes. */
    private class CannedServer(status: String, body: String) : AutoCloseable {
        private val socket = ServerSocket(0)
        private val worker = Executors.newSingleThreadExecutor()

        /** What the client actually sent, so the request shape can be asserted too. */
        @Volatile
        var requestLine: String? = null

        @Volatile
        var acceptHeader: String? = null

        val url: String get() = "http://127.0.0.1:${socket.localPort}/releases/latest"

        init {
            worker.submit {
                try {
                    socket.accept().use { client ->
                        val reader = client.getInputStream().bufferedReader()
                        requestLine = reader.readLine()
                        // Headers end at the first blank line, and a closed stream ends them too -
                        // both are the same condition here, so the loop needs no jumps at all.
                        var header = reader.readLine()
                        while (!header.isNullOrEmpty()) {
                            if (header.startsWith("Accept:", ignoreCase = true)) {
                                acceptHeader = header.substringAfter(':').trim()
                            }
                            header = reader.readLine()
                        }
                        val payload = body.toByteArray()
                        val head = "HTTP/1.1 $status\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${payload.size}\r\n" +
                            "Connection: close\r\n\r\n"
                        client.getOutputStream().apply {
                            write(head.toByteArray())
                            write(payload)
                            flush()
                        }
                    }
                } catch (_: IOException) {
                    // The socket being closed by close() below is how this thread ends.
                }
            }
        }

        override fun close() {
            worker.shutdownNow()
            socket.close()
        }
    }

    private fun lookupFrom(status: String, body: String, assert: (ReleaseLookup, CannedServer) -> Unit) {
        CannedServer(status, body).use { server ->
            val lookup = runBlocking { UpdateRepository(releasesUrl = server.url).fetchLatestRelease() }
            assert(lookup, server)
        }
    }

    private val realShapedRelease = """
        {"tag_name":"v1.2.0",
         "html_url":"https://github.com/Dovbnyak28/UA-Cast/releases/tag/v1.2.0",
         "draft":false,"prerelease":false}
    """.trimIndent()

    /**
     * The defect, on the path it actually lived on. GitHub answers 404 here for a repository with
     * no published release - verified against the real repository on 2026-08-13, which returned 200
     * for the repository itself and 404 for `/releases/latest` with an empty `/releases`.
     */
    @Test
    fun `404 means no release published rather than a failed check`() {
        lookupFrom("404 Not Found", """{"message":"Not Found","status":"404"}""") { lookup, _ ->
            assertEquals(ReleaseLookup.NonePublished, lookup)
        }
    }

    @Test
    fun `a published release is read, and asked for the way GitHub documents`() {
        lookupFrom("200 OK", realShapedRelease) { lookup, server ->
            val found = lookup as ReleaseLookup.Found
            assertEquals("v1.2.0", found.release.tagName)
            assertEquals(
                "https://github.com/Dovbnyak28/UA-Cast/releases/tag/v1.2.0",
                found.release.releaseUrl,
            )
            assertTrue(server.requestLine.orEmpty().startsWith("GET /releases/latest"))
            assertEquals("application/vnd.github+json", server.acceptHeader)
        }
    }

    /**
     * `/releases/latest` excludes drafts, so reaching this means the endpoint behaved unexpectedly -
     * and a half-finished draft must not become every user's update banner.
     */
    @Test
    fun `a draft that reaches the client is a failure, not an update`() {
        val draft = """{"tag_name":"v9.9.9","html_url":"https://example.test/r","draft":true}"""
        lookupFrom("200 OK", draft) { lookup, _ ->
            assertEquals(ReleaseLookup.Failed, lookup)
        }
    }

    /** A release exists but this build cannot read it - not the same as none existing, because
     * "up to date" would then be a claim made on no evidence. */
    @Test
    fun `a release tagged with something that is not a version is a failure`() {
        val nightly = """{"tag_name":"nightly","html_url":"https://example.test/r","draft":false}"""
        lookupFrom("200 OK", nightly) { lookup, _ ->
            assertEquals(ReleaseLookup.Failed, lookup)
        }
    }

    @Test
    fun `a server error stays a failure`() {
        lookupFrom("500 Internal Server Error", """{"message":"boom"}""") { lookup, _ ->
            assertEquals(ReleaseLookup.Failed, lookup)
        }
    }

    /** A captive portal answering the request with its own login page is ordinary, not exceptional -
     * it must leave the app silent rather than crash it on the launch path. */
    @Test
    fun `an HTML login page where JSON was expected is a failure, not a crash`() {
        lookupFrom("200 OK", "<html><body>Sign in to continue</body></html>") { lookup, _ ->
            assertEquals(ReleaseLookup.Failed, lookup)
        }
    }

    /** Nothing listening at all: the offline case, which must not escape as an exception. */
    @Test
    fun `an unreachable host is a failure rather than a thrown IOException`() {
        val deadUrl = ServerSocket(0).use { "http://127.0.0.1:${it.localPort}/releases/latest" }
        val lookup = runBlocking { UpdateRepository(releasesUrl = deadUrl).fetchLatestRelease() }

        assertEquals(ReleaseLookup.Failed, lookup)
    }
}
