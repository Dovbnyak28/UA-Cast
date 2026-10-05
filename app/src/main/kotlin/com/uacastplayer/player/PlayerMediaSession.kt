package com.uacastplayer.player

import android.app.PendingIntent
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.uacastplayer.log.AppLog

private const val TAG = "PlayerMediaSession"

/** Creates the optional system-media-controls bridge independently of PlayerViewModel's playback
 * state and Media3 listener wiring. Failure is non-fatal: live TV remains usable without headset,
 * watch and lock-screen controls. */
@UnstableApi
internal object PlayerMediaSessionFactory {
    fun create(
        context: Context,
        player: SessionPlayer,
    ): MediaSession? = try {
        MediaSession.Builder(context, player)
            .setId(PLAYER_SESSION_ID)
            .apply {
                // Resolve the app's launcher activity through PackageManager instead of importing
                // the app-root Activity. This keeps the player feature independent from :app and
                // remains explicit on API 30+, where an implicit package intent may be hidden.
                context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
                    setSessionActivity(
                        PendingIntent.getActivity(
                            context,
                            0,
                            intent,
                            PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                }
            }
            .build()
    } catch (e: IllegalStateException) {
        AppLog.w(TAG) { "MediaSession creation failed, continuing without system media controls: ${e.message}" }
        null
    }
}
