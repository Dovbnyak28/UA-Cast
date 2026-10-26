package com.uacastplayer.playlist

import com.uacastplayer.testsupport.JvmAllocations
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uEpgBudgetTest {
    @Test fun `millions of optional metadata entries have bounded allocations`() {
        val input = "#EXTM3U url-tvg=\"" + "a,".repeat(3_500_000) +
            "\"\n#EXTINF:-1,News\nhttps://example.test/live"
        val before = JvmAllocations.currentThreadBytes()
        val parsed = M3uParser.parse(input)
        val allocated = JvmAllocations.currentThreadBytes() - before
        assertEquals(1, parsed.channels.size)
        assertTrue(parsed.epgUrls.isEmpty())
        if (before >= 0) assertTrue("metadata allocated $allocated bytes", allocated < 64L * 1024 * 1024)
    }

    @Test fun `URL budget is shared across header aliases and preserves first distinct URLs`() {
        val values = (0..100).joinToString(",") { "https://example.test/$it.xml" }
        val parsed = M3uParser.parse("#EXTM3U url-tvg=\"$values\" x-tvg-url=https://extra.test/a")
        assertEquals(M3uEpgUrls.MAX_URLS, parsed.epgUrls.size)
        assertEquals("https://example.test/0.xml", parsed.epgUrls.first())
    }

    @Test fun `invalid and oversized URLs do not hide a following valid Unicode URL`() {
        val valid = "https://example.test/програма.xml"
        val parsed = M3uParser.parse(
            "#EXTM3U url-tvg=\"file:///tmp/a,https://,https://bad url/a," +
                "https://example.test/" + "a".repeat(5_000) + ",$valid,$valid\"",
        )
        assertEquals(listOf(valid), parsed.epgUrls)
    }

    @Test(expected = CancellationException::class)
    fun `metadata scanning is cancellable even with only invalid entries`() {
        var checks = 0
        M3uEpgUrls { if (++checks == 5) throw CancellationException() }.add("a,".repeat(100_000))
    }
}
