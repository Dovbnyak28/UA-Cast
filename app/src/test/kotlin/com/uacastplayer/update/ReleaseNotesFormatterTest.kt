package com.uacastplayer.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesFormatterTest {
    @Test
    fun formatsHeadingsBulletsAndLinksAsPlainText() {
        assertEquals(
            "Що нового\n• Стабільніше DLNA\nДокументація",
            ReleaseNotesFormatter.fromMarkdown(
                "## Що нового\n- **Стабільніше** DLNA\n[Документація](https://example.com)",
            ),
        )
    }

    @Test
    fun emptyAndControlOnlyNotesAreAbsent() {
        assertNull(ReleaseNotesFormatter.fromMarkdown(null))
        assertNull(ReleaseNotesFormatter.fromMarkdown(" \n "))
        assertNull(ReleaseNotesFormatter.fromMarkdown("\u0000\u0001"))
    }

    @Test
    fun hugeReleaseNotesAreBounded() {
        val result = ReleaseNotesFormatter.fromMarkdown("a".repeat(100_000))!!
        assertTrue(result.length <= 1_801)
        assertTrue(result.endsWith("…"))
    }
}
