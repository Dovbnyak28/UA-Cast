package com.uacastplayer.data.playlist

import com.uacastplayer.playlist.ChannelGroup
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistError
import com.uacastplayer.playlist.PlaylistUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistOutcomeReducerTest {

    private fun channel(name: String) = M3uChannel(displayName = name, streamUrl = "http://example.com/$name")

    private val loadedState = PlaylistUiState(
        groups = listOf(GroupedChannels(ChannelGroup.Custom("Group"), listOf(channel("A")))),
        skippedLineCount = 0,
        activePlaylistId = "abcd1234",
        displayName = "My Playlist",
        sourceUrl = "https://example.com/playlist.m3u8",
    )

    @Test
    fun `a Loaded outcome replaces groups and clears any previous error`() {
        val outcome = PlaylistOutcome.Loaded(
            groups = listOf(GroupedChannels(ChannelGroup.Custom("New"), listOf(channel("B")))),
            skippedLineCount = 1,
            sourceFingerprint = "ffffffffff",
            sourceUrl = "https://example.com/new.m3u8",
        )
        val result = PlaylistOutcomeReducer.reduce(
            current = loadedState.copy(error = PlaylistError.Network),
            outcome = outcome,
            fromCache = false,
            displayName = "New Name",
        )

        assertEquals(listOf("B"), result.groups.single().channels.map { it.displayName })
        assertEquals(1, result.skippedLineCount)
        assertNull(result.error)
        assertEquals(false, result.isLoading)
        assertEquals("ffffffff", result.activePlaylistId)
        assertEquals("New Name", result.displayName)
        assertEquals("https://example.com/new.m3u8", result.sourceUrl)
    }

    @Test
    fun `a Loaded outcome from cache is flagged as restored from cache`() {
        val outcome = PlaylistOutcome.Loaded(groups = emptyList(), skippedLineCount = 0)
        val result = PlaylistOutcomeReducer.reduce(loadedState, outcome, fromCache = true, displayName = null)
        assertEquals(true, result.restoredFromCache)
    }

    @Test
    fun `a failed refresh keeps the previously loaded channels, sourceUrl, and displayName`() {
        val result = PlaylistOutcomeReducer.reduce(
            current = loadedState,
            outcome = PlaylistOutcome.HttpError(503),
            fromCache = false,
            displayName = loadedState.displayName,
        )

        assertEquals(loadedState.groups, result.groups)
        assertEquals(loadedState.sourceUrl, result.sourceUrl)
        assertEquals(loadedState.displayName, result.displayName)
        assertEquals(loadedState.activePlaylistId, result.activePlaylistId)
        assertEquals(PlaylistError.Http(503), result.error)
        assertEquals(false, result.isLoading)
    }

    @Test
    fun `a size limit failure also preserves the previously loaded channels`() {
        val result = PlaylistOutcomeReducer.reduce(loadedState, PlaylistOutcome.SizeLimitExceeded, fromCache = false, displayName = null)
        assertEquals(loadedState.groups, result.groups)
        assertEquals(PlaylistError.SizeLimitExceeded, result.error)
    }

    @Test
    fun `a network read error also preserves the previously loaded channels`() {
        val result = PlaylistOutcomeReducer.reduce(loadedState, PlaylistOutcome.ReadError("timeout"), fromCache = false, displayName = null)
        assertEquals(loadedState.groups, result.groups)
        assertEquals(PlaylistError.Network, result.error)
    }
}
