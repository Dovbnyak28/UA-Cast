package com.uacastplayer.player

import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

internal data class SessionPlayerControls(
    val canPlay: Boolean,
    val canStop: Boolean,
    val canSeek: Boolean,
    val canGoNext: Boolean,
    val canGoPrevious: Boolean,
)

internal data class SessionPlayerActions(
    val playWhenReady: (Boolean) -> Unit,
    val prepare: () -> Unit,
    val stop: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
)

/** MediaSession boundary, not another playback owner. Reads live owner policy on every command.
 * SimpleBasePlayer publishes capability changes consistently to controllers and listeners.
 * The VM releases this wrapper once; the underlying engine is released by the superclass. */
@UnstableApi
internal class SessionPlayer(
    engine: Player,
    private val controls: () -> SessionPlayerControls,
    private val actions: SessionPlayerActions,
) : ForwardingSimpleBasePlayer(engine) {
    fun refreshCommands() = invalidateState()

    override fun getState(): State {
        val state = super.getState()
        val policy = controls()
        val commands = Player.Commands.Builder()
            .addAll(*READ_COMMANDS.filter(state.availableCommands::contains).toIntArray())
            .addIf(Player.COMMAND_PLAY_PAUSE, policy.canPlay)
            .addIf(Player.COMMAND_PREPARE, policy.canPlay)
            .addIf(Player.COMMAND_STOP, policy.canStop)
            .addIf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, policy.canGoNext)
            .addIf(Player.COMMAND_SEEK_TO_NEXT, policy.canGoNext)
            .addIf(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, policy.canGoPrevious)
            .addIf(Player.COMMAND_SEEK_TO_PREVIOUS, policy.canGoPrevious)
        for (command in SEEK_COMMANDS) {
            commands.addIf(command, policy.canSeek && state.availableCommands.contains(command))
        }
        return state.buildUpon().setAvailableCommands(commands.build()).build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (controls().canPlay) actions.playWhenReady(playWhenReady)
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        if (controls().canPlay) actions.prepare()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        if (controls().canStop) actions.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val policy = controls()
        when (MediaSessionCommandPolicy.mapCommand(seekCommand)) {
            MediaSessionCommandPolicy.Action.NEXT -> if (policy.canGoNext) actions.next()
            MediaSessionCommandPolicy.Action.PREVIOUS -> if (policy.canGoPrevious) actions.previous()
            null -> if (policy.canSeek) return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
        return Futures.immediateVoidFuture()
    }

    private companion object {
        val READ_COMMANDS = listOf(
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_TRACKS, Player.COMMAND_GET_AUDIO_ATTRIBUTES, Player.COMMAND_GET_VOLUME,
            Player.COMMAND_GET_TEXT, Player.COMMAND_GET_DEVICE_VOLUME,
        )
        val SEEK_COMMANDS = listOf(
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD,
        )
    }
}
