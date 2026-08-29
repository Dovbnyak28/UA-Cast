package com.uacastplayer.data.playlist

import com.uacastplayer.playlist.ChannelGroup
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistError
import com.uacastplayer.playlist.PlaylistUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

    /**
     * A file that was read perfectly and had nothing in it: a zero-byte pick, a JPEG chosen by
     * mistake, an M3U whose every line was skipped.
     *
     * This used to reduce to an ordinary success with an empty list, which the app draws as the
     * screen a user with no playlist at all sees - so "I added my playlist and nothing happened"
     * had no sentence anywhere explaining that the file held no channels. Nothing failed here,
     * which is exactly why it needs its own answer rather than one of the failure ones.
     */
    @Test
    fun `a source that parsed to no channels is reported rather than shown as an empty app`() {
        val outcome = PlaylistOutcome.Loaded(groups = emptyList(), skippedLineCount = 0, sourceFingerprint = "ff")

        val result = PlaylistOutcomeReducer.reduce(loadedState, outcome, fromCache = false, displayName = null)

        assertEquals(PlaylistError.Empty, result.error)
        assertFalse(result.isLoading)
    }

    /** Groups with no channels in them are the same nothing, arriving in a different shape. */
    @Test
    fun `groups that are all empty count as no channels`() {
        val hollow = listOf(GroupedChannels(ChannelGroup.Custom("Group"), emptyList()))
        val outcome = PlaylistOutcome.Loaded(groups = hollow, skippedLineCount = 0, sourceFingerprint = "ff")

        val result = PlaylistOutcomeReducer.reduce(loadedState, outcome, fromCache = false, displayName = null)

        assertEquals(PlaylistError.Empty, result.error)
    }

    /** And one real channel is a real playlist - the guard must not swallow a working load. */
    @Test
    fun `a single channel is not empty`() {
        val outcome = PlaylistOutcome.Loaded(
            groups = listOf(GroupedChannels(ChannelGroup.Custom("G"), listOf(channel("A")))),
            skippedLineCount = 0,
            sourceFingerprint = "ff",
        )

        val result = PlaylistOutcomeReducer.reduce(loadedState, outcome, fromCache = false, displayName = null)

        assertNull(result.error)
    }

    @Test
    fun `a loaded state reuses the flat channel list prepared off the main thread`() {
        val first = channel("A")
        val second = channel("B")
        val groups = listOf(
            GroupedChannels(ChannelGroup.Custom("One"), listOf(first)),
            GroupedChannels(ChannelGroup.Custom("Two"), listOf(second)),
        )
        val prepared = listOf(first, second)

        val result = PlaylistOutcomeReducer.reduce(
            current = loadedState,
            outcome = PlaylistOutcome.Loaded(groups = groups, skippedLineCount = 0),
            fromCache = false,
            displayName = null,
            loadedChannels = prepared,
        )

        assertSame(prepared, result.channels)
        assertEquals(listOf("A", "B"), result.channels.map { it.displayName })
    }

    @Test
    fun `a hollow group is not reported as a loaded playlist`() {
        val state = PlaylistUiState(
            groups = listOf(GroupedChannels(ChannelGroup.Custom("Empty"), emptyList())),
        )

        assertFalse(state.hasChannels)
        assertEquals(emptyList<M3uChannel>(), state.channels)
    }

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
        // A channel, not an empty list: this is about the restoredFromCache flag, and an empty
        // snapshot now reduces to PlaylistError.Empty - which is correct, and was never what this
        // test meant to describe.
        val outcome = PlaylistOutcome.Loaded(
            groups = listOf(GroupedChannels(ChannelGroup.Custom("G"), listOf(channel("A")))),
            skippedLineCount = 0,
        )
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
        assertSame(loadedState.channels, result.channels)
        assertEquals(PlaylistError.Http(503), result.error)
        assertEquals(false, result.isLoading)
    }

    @Test
    fun `a size limit failure also preserves the previously loaded channels`() {
        val result = PlaylistOutcomeReducer.reduce(
            loadedState,
            PlaylistOutcome.SizeLimitExceeded,
            fromCache = false,
            displayName = null,
        )
        assertEquals(loadedState.groups, result.groups)
        assertEquals(PlaylistError.SizeLimitExceeded, result.error)
    }

    @Test
    fun `a network read error also preserves the previously loaded channels`() {
        val result = PlaylistOutcomeReducer.reduce(
            loadedState,
            PlaylistOutcome.ReadError("timeout"),
            fromCache = false,
            displayName = null,
        )
        assertEquals(loadedState.groups, result.groups)
        assertEquals(PlaylistError.Network, result.error)
    }
}
