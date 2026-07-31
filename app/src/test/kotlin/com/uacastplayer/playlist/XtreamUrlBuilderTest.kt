package com.uacastplayer.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamUrlBuilderTest {

    @Test
    fun `adds http scheme when the server has none`() {
        val url = XtreamUrlBuilder.playlistUrl("example.com", "user", "pass")
        assertEquals("http://example.com/get.php?username=user&password=pass&type=m3u_plus&output=ts", url)
    }

    @Test
    fun `keeps an existing scheme instead of prefixing another one`() {
        val url = XtreamUrlBuilder.playlistUrl("https://example.com", "user", "pass")
        assertTrue(url.startsWith("https://example.com/get.php"))
        assertTrue(!url.contains("http://https://"))
    }

    @Test
    fun `preserves a port in the server address`() {
        val url = XtreamUrlBuilder.playlistUrl("example.com:8080", "user", "pass")
        assertTrue(url.startsWith("http://example.com:8080/get.php"))
    }

    @Test
    fun `strips a trailing slash so the path does not end up with a double slash`() {
        val url = XtreamUrlBuilder.playlistUrl("http://example.com/", "user", "pass")
        assertEquals("http://example.com/get.php?username=user&password=pass&type=m3u_plus&output=ts", url)
    }

    @Test
    fun `url-encodes special characters in the password`() {
        val url = XtreamUrlBuilder.playlistUrl("example.com", "user", "p@ss w0rd!")
        assertTrue(url.contains("password=p%40ss+w0rd%21"))
    }

    @Test
    fun `url-encodes special characters in the username`() {
        val url = XtreamUrlBuilder.playlistUrl("example.com", "u ser", "pass")
        assertTrue(url.contains("username=u+ser"))
    }

    @Test
    fun `trims surrounding whitespace from the server address`() {
        val url = XtreamUrlBuilder.playlistUrl("  example.com  ", "user", "pass")
        assertTrue(url.startsWith("http://example.com/get.php"))
    }

    @Test
    fun `epgUrl points at xmltv php with the same credentials`() {
        val url = XtreamUrlBuilder.epgUrl("example.com", "user", "pass")
        assertEquals("http://example.com/xmltv.php?username=user&password=pass", url)
    }

    @Test
    fun `serverHost extracts just the host without scheme, port, or path`() {
        assertEquals("example.com", XtreamUrlBuilder.serverHost("https://example.com:8080"))
        assertEquals("example.com", XtreamUrlBuilder.serverHost("example.com"))
    }
}
