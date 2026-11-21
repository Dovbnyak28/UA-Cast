package com.uacastplayer.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.uacastplayer.media.LocalVideo
import com.uacastplayer.playlist.M3uChannel
import java.lang.reflect.Proxy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerLocalVideoFailureTest {

    @Test fun aLateLocalDecoderErrorDoesNotInvalidateHealthyRemotePlayback() = runTest {
        val video = LocalVideo("content://media/video/1", "Movie.mp4", "video/mp4")
        val initial = PlayerUiState(currentChannel = M3uChannel(video.displayName, video.uri, localVideo = video))
        val state = MutableStateFlow(initial)
        var effects = 0
        val retry = PlayerRetryScheduler(this, { effects++ }, { effects++ })
        val noEngineAccess = Proxy.newProxyInstance(
            Player::class.java.classLoader, arrayOf(Player::class.java),
        ) { _, _, _ -> error("The remote renderer, not Media3, owns this playback") } as Player
        val handler = PlayerFailureHandler(PlayerFailureContext(noEngineAccess, PlayerSessionStateMachine(), retry,
            state, { PlayerFailureFlags(false, true, true, true) }, { effects++; true }, { effects++ },
            { effects++ }, { _, _ -> effects++ }, { effects++ }))
        handler.handle(PlaybackException(
            "late local codec failure", null, PlaybackException.ERROR_CODE_DECODING_FAILED,
        ))
        advanceUntilIdle()
        assertEquals(initial, state.value)
        assertEquals(0, effects)
    }

    @Test fun aLostFileGrantIsFatalImmediatelyWithoutInternetWaitOrChannelAutoSkip() = runTest {
        val video = LocalVideo("content://media/video/1", "Movie.mp4", "video/mp4")
        val state = MutableStateFlow(PlayerUiState(
            currentChannel = M3uChannel(video.displayName, video.uri, localVideo = video), isBuffering = true,
        ))
        var retries = 0
        var networkQueries = 0
        var controls = 0
        var stallCancelled = 0
        val retry = PlayerRetryScheduler(this, { networkQueries++ }, { retries++ })
        retry.after(1000)
        runCurrent()
        val noEngineAccess = Proxy.newProxyInstance(
            Player::class.java.classLoader, arrayOf(Player::class.java),
        ) { _, _, _ ->
            error("A terminal file error must not restart the engine")
        } as Player
        val handler = PlayerFailureHandler(PlayerFailureContext(noEngineAccess, PlayerSessionStateMachine(), retry,
            state, { PlayerFailureFlags(false, false, true, true) }, { networkQueries++; false }, { retries++ },
            { stallCancelled++ }, { _, _ -> error("A local file must not skip into an IPTV channel") }, { controls++ }))
        handler.handle(PlaybackException("grant revoked", null, PlaybackException.ERROR_CODE_IO_NO_PERMISSION))
        advanceUntilIdle()
        assertTrue(state.value.fatalError)
        assertFalse(state.value.isBuffering)
        assertEquals(0, networkQueries)
        assertEquals(0, retries)
        assertEquals(1, controls)
        assertEquals(1, stallCancelled)
    }
}
