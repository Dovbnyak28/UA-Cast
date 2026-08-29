package com.uacastplayer.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.uacastplayer.MainActivity
import com.uacastplayer.log.AppLog

private const val TAG = "PlayerMediaSession"

/** Creates the optional system-media-controls bridge independently of PlayerViewModel's playback
 * state and Media3 listener wiring. Failure is non-fatal: live TV remains usable without headset,
 * watch and lock-screen controls. */
internal object PlayerMediaSessionFactory {
    fun create(
        context: Context,
        player: Player,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
    ): MediaSession? = try {
        MediaSession.Builder(context, player)
            .setId(PLAYER_SESSION_ID)
            .setCallback(PlayerMediaSessionCallback(onNext, onPrevious))
            // Use an explicit component. Media3's implicit fallback can be unresolvable on API 30+
            // because of package-visibility restrictions.
            .setSessionActivity(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
    } catch (e: IllegalStateException) {
        AppLog.w(TAG) { "MediaSession creation failed, continuing without system media controls: ${e.message}" }
        null
    }
}

/**
 * Makes this app's channel navigation visible to system controllers even though ExoPlayer itself
 * contains exactly one MediaItem at a time.
 */
private class PlayerMediaSessionCallback(
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
) : MediaSession.Callback {

    @UnstableApi
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val connectionResult = super.onConnect(session, controller)
        val availablePlayerCommands = connectionResult.availablePlayerCommands.buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
        return MediaSession.ConnectionResult.accept(
            connectionResult.availableSessionCommands,
            availablePlayerCommands,
        )
    }

    /**
     * Deprecated in Media3 1.10.1 in favour of wrapping the player in a ForwardingPlayer. Retained
     * until that migration can be verified through real notification/headset/watch surfaces.
     *
     * The callback must return success after dispatching. Media3 rejects the command entirely for
     * every other result code; the previous `RESULT_ERROR_NOT_SUPPORTED` therefore told the
     * controller that a channel switch which had actually happened had failed. The subsequent
     * built-in player call allowed by success remains a no-op because this app exposes one
     * MediaItem, while [MediaSessionPlayerCommandDispatcher] performs channel navigation.
     */
    @Suppress("DEPRECATION")
    @Deprecated("Kept until the ForwardingPlayer migration can be verified on a device.")
    override fun onPlayerCommandRequest(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        @Player.Command playerCommand: Int,
    ): Int = MediaSessionPlayerCommandDispatcher.dispatch(
        playerCommand = playerCommand,
        onNext = onNext,
        onPrevious = onPrevious,
    ) ?: super.onPlayerCommandRequest(session, controller, playerCommand)
}

/** Pure/testable side of [PlayerMediaSessionCallback]. Null delegates an unrelated command to the
 * Media3 default callback. */
internal object MediaSessionPlayerCommandDispatcher {
    fun dispatch(
        playerCommand: Int,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
    ): Int? {
        when (MediaSessionCommandPolicy.mapCommand(playerCommand)) {
            MediaSessionCommandPolicy.Action.NEXT -> onNext()
            MediaSessionCommandPolicy.Action.PREVIOUS -> onPrevious()
            null -> return null
        }
        return SessionResult.RESULT_SUCCESS
    }
}
