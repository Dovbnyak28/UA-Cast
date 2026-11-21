package com.uacastplayer.player

import android.content.Context
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.core.settings.PlayerResizeMode
import com.uacastplayer.data.prefs.AppPreferences
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerRelativeSeekTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var position = 1000L
    private var duration = 90_000L
    private var allowed = true
    private var engineSeekAllowed = true
    private val targets = mutableListOf<Long>()

    @After fun close() { scope.cancel() }

    @Test fun rapidTapsAccumulateAgainstTheLivePositionInsteadOfAStaleUiSnapshot() {
        val navigation = navigation()
        repeat(3) { navigation.seekBy(10_000L) }
        assertEquals(listOf(11_000L, 21_000L, 31_000L), targets)
        navigation.seekBy(Long.MAX_VALUE)
        assertEquals(90_000L, position)
        navigation.seekBy(Long.MIN_VALUE)
        assertEquals(0L, position)
    }

    @Test fun remoteOwnershipAndUnknownDurationRefuseRelativeSeeking() {
        val navigation = navigation()
        allowed = false
        navigation.seekBy(10_000L)
        allowed = true
        duration = -1L
        navigation.seekBy(10_000L)
        assertEquals(emptyList<Long>(), targets)
    }

    @Test fun revokedEngineCommandRejectsBothAbsoluteAndRelativeSeekDespiteStaleUi() {
        val navigation = navigation()
        engineSeekAllowed = false
        navigation.seekTo(20_000L)
        navigation.seekBy(10_000L)
        assertEquals(emptyList<Long>(), targets)
        engineSeekAllowed = true
        navigation.seekTo(20_000L)
        assertEquals(listOf(20_000L), targets)
    }

    private fun navigation(): PlayerNavigationController {
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) {
                _, method, args ->
            when (method.name) {
                "getCurrentPosition" -> position
                "getDuration" -> duration
                "isCommandAvailable" -> engineSeekAllowed && args!![0] == Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
                "seekTo" -> { position = args!![0] as Long; targets += position; null }
                else -> null
            }
        } as Player
        val context = ApplicationProvider.getApplicationContext<Context>()
        return PlayerNavigationController(
            ChannelNavigationContext(scope, PlayerSessionStateMachine(), { false }, {}), { allowed }, player,
            ResizeModeContext(AppPreferences(context), { PlayerResizeMode.DEFAULT }, {}),
        )
    }
}
