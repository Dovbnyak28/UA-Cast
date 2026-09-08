package com.uacastplayer.playlist

import java.util.concurrent.CancellationException
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uHostileAttributeTest {
    @Test fun `long single EXTINF line observes cancellation inside the line`() {
        var probes = 0
        val input = "#EXTINF:-1 ${"a".repeat(8_192)},Name\nhttps://example.test/live"
        assertThrows(CancellationException::class.java) {
            M3uParser.parse(input) { if (++probes == 3) throw CancellationException("cancelled long line") }
        }
    }

    @Test fun `bounded malformed token cost diagnostic`() {
        repeat(3) { M3uParser.parse("#EXTINF:-1 ${"a".repeat(256)},Name\nhttps://example.test/live") }
        for (length in listOf(1_000, 2_000, 4_000, 8_000)) {
            val input = "#EXTINF:-1 ${"a".repeat(length)},Name\nhttps://example.test/live"
            val start = System.nanoTime()
            val result = M3uParser.parse(input)
            println("longAttributeToken length=$length elapsedNs=${System.nanoTime() - start}")
            assertEquals("Name", result.channels.single().displayName)
        }
    }

    @Test fun `long header observes cancellation inside the line`() {
        var probes = 0
        assertThrows(CancellationException::class.java) {
            M3uParser.parse("#EXTM3U ${"a".repeat(8_192)}") {
                if (++probes == 3) throw CancellationException("cancelled header")
            }
        }
    }

    @Test fun `long quoted and bare values are cancellable in the reader`() {
        for (prefix in listOf("tvg-logo=", "tvg-logo=\"", "garbage=\"")) {
            val input = prefix + "a".repeat(8_192)
            assertThrows(CancellationException::class.java) {
                M3uAttributeReader(input, 0, input.length, { throw CancellationException("inside value") })
                    .forEach { _, _ -> error("Cancelled attributes must not be published") }
            }
        }
    }

    @Test fun `many short attributes cannot skip cancellation checkpoints`() {
        val input = "a=\"\"".repeat(8_192)
        var probes = 0
        M3uAttributeReader(input, 0, input.length, { probes++ }).forEach { _, _ -> }
        assertTrue("Probes must not depend on delimiters landing at 1024 byte offsets", probes >= 16)
    }

    @Test fun `megabyte malformed key remains bounded and preserves following channel`() {
        val parsed = M3uParser.parse(
            "#EXTINF:-1 ${"a".repeat(1_048_576)} tvg-id=one,Перший\nhttps://example.test/1\n" +
                "#EXTINF:-1 tvg-id=two,España\nhttps://example.test/2",
        )
        assertEquals(listOf("Перший", "España"), parsed.channels.map { it.displayName })
        assertEquals(listOf("one", "two"), parsed.channels.map { it.tvgId })
    }

    @Test fun `duplicate blank values and case insensitive keys retain legacy semantics`() {
        val parsed = M3uParser.parse(
            "#EXTINF:-1 tvg-id=old TVG-ID=\"\" tvg-name=old TVG-NAME=\" España, Україна \" " +
                "group-title=first GROUP-TITLE=\" Новини \" tvg-logo=old TVG-LOGO=\"\",Name\nhttps://example.test/1",
        ).channels.single()
        assertEquals(null, parsed.tvgId)
        assertEquals(null, parsed.tvgLogo)
        assertEquals("España, Україна", parsed.tvgName)
        assertEquals("Новини", parsed.groupTitle)
    }

    @Test fun `quoted unknown attribute does not leak nested known attributes`() {
        assertEquals(
            listOf("unknown" to "tvg-id=wrong group-title=wrong", "tvg-id" to "right"),
            read("unknown=\"tvg-id=wrong group-title=wrong\"tvg-id=right"),
        )
    }

    @Test fun `unmatched quote retains bare fallback and later attributes`() {
        assertEquals(
            listOf("unknown" to "\"broken", "tvg-id" to "right"),
            read("unknown=\"broken tvg-id=right"),
        )
    }

    @Test fun `header aliases quotes commas and unicode urls remain supported`() {
        val parsed = M3uParser.parse(
            "#EXTM3U URL-TVG=\" https://example.test/Україна,https://example.test/España \" " +
                "x-tvg-url=https://example.test/3,https://example.test/4",
        )
        assertEquals(
            listOf(
                "https://example.test/Україна", "https://example.test/España",
                "https://example.test/3", "https://example.test/4",
            ),
            parsed.epgUrls,
        )
    }

    @Test fun `reader never consumes display name or content outside its range`() {
        val input = "prefix=bad tvg-id=right tvg-id=outside"
        val result = mutableListOf<Pair<String, String>>()
        M3uAttributeReader(input, "prefix=bad ".length, input.indexOf(" tvg-id=outside"), {})
            .forEach { key, value -> result += key to value }
        assertEquals(listOf("tvg-id" to "right"), result)
        val parsed = M3uParser.parse("#EXTINF:-1 tvg-id=id,Title tvg-id=wrong\nhttps://example.test")
        assertEquals("id", parsed.channels.single().tvgId)
    }

    @Test fun `deterministic dialect corpus agrees with legacy regex`() {
        // Android's ICU regex uses Unicode classes by default; the host JVM requires (?U).
        val legacy = Regex("""(?U)([a-zA-Z][\w-]*)=(?:"([^"]*)"|(\S+))""")
        val random = Random(37)
        val fragments = listOf(
            "a", "a_b-0", "tvg-id", "TVG-NAME", "tvg-logo", "group-title", "url-tvg", "unknown",
            "=", "=\"\"", "=\"Україна, España\"", "=bare", "\"", " ", "\t", "\r", "\n", "-1", ",", "_", ";",
            "\u00A0", "\u2003", "\u0085", "\u200B", "aїtvg-id", "a\u0301tvg-id", "a\u200Ctvg-id", "a𐐀tvg-id",
        )
        repeat(4_000) {
            val input = buildString { repeat(random.nextInt(1, 30)) { append(fragments.random(random)) } }
            val expected = legacy.findAll(input).map { match ->
                match.groupValues[1] to (match.groups[2]?.value ?: match.groups[3]?.value).orEmpty()
            }.toList()
            assertEquals(input, expected, read(input))
        }
    }

    private fun read(input: String): List<Pair<String, String>> = buildList {
        M3uAttributeReader(input, 0, input.length, {}).forEach { key, value -> add(key to value) }
    }
}
