package com.uacastplayer.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpgUrlPolicyTest {

    @Test
    fun `provider credentials and query remain intact`() {
        assertEquals("https://example.test/epg?user=test&key=abc",
            EpgUrlPolicy.normalize(" https://example.test/epg?user=test&key=abc "))
    }

    @Test
    fun `empty input has no default source`() {
        assertNull(EpgUrlPolicy.normalize("   "))
    }

    @Test
    fun `unsupported schemes are rejected`() {
        listOf("file:///private.xml", "content://provider/1", "ftp://example.test/a", "bad URL").forEach {
            assertNull(EpgUrlPolicy.normalize(it))
        }
    }

    @Test
    fun `control characters and oversized addresses are rejected`() {
        assertNull(EpgUrlPolicy.normalize("https://example.test/a\nb"))
        assertNull(EpgUrlPolicy.normalize("https://example.test/" + "a".repeat(8_192)))
    }

    @Test
    fun `local HTTP provider is allowed`() {
        assertEquals("http://192.168.1.8/guide.xml", EpgUrlPolicy.normalize("http://192.168.1.8/guide.xml"))
    }
}
