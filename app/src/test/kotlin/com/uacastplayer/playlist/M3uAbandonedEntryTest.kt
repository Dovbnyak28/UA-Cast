package com.uacastplayer.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uAbandonedEntryTest {
    @Test fun `unfinished record cannot leak provider headers and group into following channel`() {
        val parsed = M3uParser.parse("""
            #EXTM3U
            #EXTINF:-1,Abandoned
            #EXTGRP:Private
            #EXTVLCOPT:http-user-agent=private-agent
            #EXTVLCOPT:http-referrer=https://private.example/
            #EXTINF:-1,Actual
            https://public.example/live.ts
        """.trimIndent())
        val channel = parsed.channels.single()
        assertEquals(1, parsed.skippedLineCount)
        assertEquals("Actual", channel.displayName)
        assertNull(channel.userAgent)
        assertNull(channel.referrer)
        assertNull(channel.groupTitle)
    }

    @Test fun `options preceding first EXTINF are still supported`() {
        val channel = M3uParser.parse("""
            #EXTM3U
            #EXTVLCOPT:http-user-agent=required-agent
            #EXTINF:-1,Actual
            https://x/live.ts
        """.trimIndent()).channels.single()
        assertEquals("required-agent", channel.userAgent)
    }
}
