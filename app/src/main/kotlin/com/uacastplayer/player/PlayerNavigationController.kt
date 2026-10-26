package com.uacastplayer.player

import androidx.media3.common.Player
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.core.settings.PlayerResizeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CHANNEL_SWITCH_DEBOUNCE_MILLIS = 220L

/** Channel navigation, seeking, and persisted resize actions, separate from Media3 event wiring. */
internal data class ChannelNavigationContext(
    val scope: CoroutineScope,
    val sessionStateMachine: PlayerSessionStateMachine,
    val wrapAroundEnabled: () -> Boolean,
    val switchImmediately: (Int) -> Unit,
)

internal data class ResizeModeContext(
    val preferences: AppPreferences,
    val current: () -> PlayerResizeMode,
    val update: (PlayerResizeMode) -> Unit,
)

class PlayerNavigationController internal constructor(
    private val channel: ChannelNavigationContext,
    private val canSeek: () -> Boolean,
    private val player: Player,
    private val resizeMode: ResizeModeContext,
) {
    private var pendingSwitchJob: Job? = null

    fun requestSwitch(index: Int) {
        if (index < 0) return
        pendingSwitchJob?.cancel()
        pendingSwitchJob = channel.scope.launch {
            delay(CHANNEL_SWITCH_DEBOUNCE_MILLIS)
            channel.switchImmediately(index)
        }
    }

    fun requestNext() {
        channel.sessionStateMachine.nextIndex(channel.wrapAroundEnabled())?.let(::requestSwitch)
    }

    fun requestPrevious() {
        channel.sessionStateMachine.previousIndex(channel.wrapAroundEnabled())?.let(::requestSwitch)
    }

    /** Last watched channel, not the previous adjacent list item. */
    fun requestPreviousChannel() {
        channel.sessionStateMachine.previousChannelIndex?.let(::requestSwitch)
    }

    fun seekTo(positionMs: Long) {
        if (canSeek()) player.seekTo(positionMs)
    }

    fun cycleResizeMode() {
        val next = ResizeModeCycle.next(resizeMode.current())
        resizeMode.preferences.playerResizeMode = next
        resizeMode.update(next)
    }

    fun cancelPendingSwitch() {
        pendingSwitchJob?.cancel()
        pendingSwitchJob = null
    }
}
