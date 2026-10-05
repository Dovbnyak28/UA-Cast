package com.uacastplayer.data.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProxyManifestAmplificationTest {
    private val parent = ResourceEntry(RESOURCE_TYPE_PLAYLIST, "https://x/root.m3u8", "Agent", null)

    private fun existingLeases() = ProxyManifestResources().apply {
        beginRoot(parent)
        assertNotNull(rewrite("#EXTM3U\nold.ts", parent.originalUrl, parent) { it })
        assertNotNull(rewrite("#EXTM3U\ncurrent.ts", parent.originalUrl, parent) { it })
    }

    @Test fun `million repeated URI lines reject before eager split or URL construction`() {
        val leases = existingLeases()
        val before = leases.snapshot()
        val text = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n" + "a\n".repeat(1_000_000)
        var mapped = 0

        assertNull(leases.rewrite(text, parent.originalUrl, parent) {
            mapped++
            "http://127.0.0.1:12345/hls/token/$it"
        })

        assertEquals(0, mapped)
        assertEquals(before, leases.snapshot())
    }

    @Test fun `blank line flood also rejects before shared rewriter allocation`() {
        val leases = existingLeases()
        val before = leases.snapshot()
        val text = "#EXTM3U\n" + "\n".repeat(ProxyManifestResources.MAX_MANIFEST_LINES)

        assertNull(leases.rewrite(text, parent.originalUrl, parent) { error("No URLs expected") })
        assertEquals(before, leases.snapshot())
    }

    @Test fun `repeated URI attributes cannot evade occurrence cap through a single line`() {
        val leases = existingLeases()
        val before = leases.snapshot()
        val text = "#EXTM3U\n#EXT-X-KEY:" +
            "URI=\"a\",".repeat(ProxyManifestResources.MAX_REFERENCE_OCCURRENCES + 1)
        var mapped = 0

        assertNull(leases.rewrite(text, parent.originalUrl, parent) { mapped++; "local" })

        assertEquals(ProxyManifestResources.MAX_REFERENCE_OCCURRENCES, mapped)
        assertEquals(before, leases.snapshot())
    }

    @Test fun `occurrence budget allows its boundary without changing distinct identity`() {
        val leases = existingLeases()
        val text = "#EXTM3U\n" + "a\n".repeat(ProxyManifestResources.MAX_REFERENCE_OCCURRENCES)
        var mapped = 0

        assertNotNull(leases.rewrite(text, parent.originalUrl, parent) { mapped++; it })

        assertEquals(ProxyManifestResources.MAX_REFERENCE_OCCURRENCES, mapped)
        // Root, current resource, and the retained previous manifest's resource.
        assertEquals(3, leases.snapshot().size)
    }

    @Test fun `expanded output rejects before a replacement crosses output budget`() {
        val leases = existingLeases()
        val before = leases.snapshot()
        val text = "#EXTM3U\n" + "a\n".repeat(10)
        val replacement = "x".repeat(ProxyManifestResources.MAX_REWRITTEN_CHARS / 4)
        var mapped = 0

        assertNull(leases.rewrite(text, parent.originalUrl, parent) { mapped++; replacement })

        assertEquals(4, mapped)
        assertEquals(before, leases.snapshot())
    }
}
