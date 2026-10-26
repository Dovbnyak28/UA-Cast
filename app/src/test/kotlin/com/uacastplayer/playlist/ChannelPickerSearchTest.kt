package com.uacastplayer.playlist

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChannelPickerSearchTest {
    private val channels = listOf("Новини Київ", "España HD", "Кіно").mapIndexed { i, name ->
        M3uChannel(name, "https://example.test/$i")
    }

    @Test fun `blank query retains order and scrolls to the current session channel`() {
        assertEquals(ChannelPickerMatches(listOf(0, 1, 2), 2), ChannelPickerSearch.search(channels, "  ", channels[2]))
    }

    @Test fun `query trims once matches Unicode without case and preserves session indices`() {
        assertEquals(listOf(0), ChannelPickerSearch.search(channels, "  київ  ", null).positions)
        assertEquals(listOf(1), ChannelPickerSearch.search(channels, "ESPAÑA", null).positions)
        assertEquals(listOf(0), ChannelPickerSearch.search(channels.drop(1), "España", null).positions)
    }

    @Test fun `no matches and empty session have a safe initial scroll`() {
        assertEquals(ChannelPickerMatches(emptyList(), 0), ChannelPickerSearch.search(channels, "missing", null))
        assertEquals(ChannelPickerMatches(emptyList(), 0), ChannelPickerSearch.search(emptyList(), "", null))
    }

    @Test fun `obsolete huge search cooperatively stops without publishing partial matches`() {
        var visited = 0
        assertThrows(CancellationException::class.java) {
            ChannelPickerSearch.search(List(100_000) { channels[0] }, "missing", null) {
                if (++visited == 64) throw CancellationException()
            }
        }
        assertEquals(64, visited)
    }
}
