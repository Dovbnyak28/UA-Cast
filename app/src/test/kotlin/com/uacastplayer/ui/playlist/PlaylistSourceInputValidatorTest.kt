package com.uacastplayer.ui.playlist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSourceInputValidatorTest {

    @Test
    fun `accepts complete http and https urls`() {
        assertTrue(PlaylistSourceInputValidator.isValidHttpUrl("https://provider.example/list.m3u"))
        assertTrue(PlaylistSourceInputValidator.isValidHttpUrl(" http://10.0.2.2:8765/list.m3u "))
    }

    @Test
    fun `rejects blank hostless and unsupported urls`() {
        assertFalse(PlaylistSourceInputValidator.isValidHttpUrl(""))
        assertFalse(PlaylistSourceInputValidator.isValidHttpUrl("provider.example/list.m3u"))
        assertFalse(PlaylistSourceInputValidator.isValidHttpUrl("https:///list.m3u"))
        assertFalse(PlaylistSourceInputValidator.isValidHttpUrl("file:///sdcard/list.m3u"))
    }

    @Test
    fun `xtream requires valid server username and password`() {
        assertTrue(PlaylistSourceInputValidator.isValidXtream("https://tv.example:8080", "user", "secret"))
        assertTrue(PlaylistSourceInputValidator.isValidXtream("tv.example:8080", "user", "secret"))
        assertTrue(PlaylistSourceInputValidator.isValidXtream("HTTPS://tv.example:8080", "user", "secret"))
        assertFalse(PlaylistSourceInputValidator.isValidXtream("https://tv.example", "", "secret"))
        assertFalse(PlaylistSourceInputValidator.isValidXtream("https://tv.example", "user", ""))
    }
}
