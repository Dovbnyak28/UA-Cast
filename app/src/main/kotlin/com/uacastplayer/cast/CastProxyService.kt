package com.uacastplayer.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.uacastplayer.MainActivity
import com.uacastplayer.R
import com.uacastplayer.core.i18n.withAppLocale
import com.uacastplayer.data.cast.CastWakeLocks
import com.uacastplayer.log.AppLog

private const val TAG = "CastProxyService"
private const val NOTIFICATION_CHANNEL_ID = "cast_session"
private const val NOTIFICATION_ID = 1001
private const val ACTION_START = "com.uacastplayer.cast.action.START"
// Fires every time CastSessionRepository's CloseProxySession side effect runs - disconnect,
// receiver error, and receiver-finished all go through this, not just the user tapping the
// notification's own Stop button (see ACTION_END_SESSION) - so this must NEVER end the Cast
// session itself, only tear down this service's own foreground/wake-lock lifetime. It used to
// share one ACTION_STOP with the notification button, which meant a plain receiver_idle_error
// fallback (direct cast failing over to the local proxy) disconnected the whole Cast session
// instead of just resuming local playback.
private const val ACTION_STOP_FOREGROUND = "com.uacastplayer.cast.action.STOP_FOREGROUND"
// Only ever sent by the notification's own Stop action (see buildNotification) - the one place
// where ending the Cast session outright is actually the intended behavior.
private const val ACTION_END_SESSION = "com.uacastplayer.cast.action.END_SESSION"
private const val EXTRA_CHANNEL_TITLE = "channel_title"
private const val EXTRA_RECEIVER_NAME = "receiver_name"

/**
 * Keeps the process (and the local HLS [com.uacastplayer.data.cast.ProxyServer] it hosts) alive
 * while a Cast session is being served through the proxy fallback, so MIUI/HyperOS and friends
 * don't kill the app in the background mid-cast. Owns no delivery logic - [CastSessionRepository]
 * starts/stops this 1:1 with the proxy's own start()/stop() (see [ProxySessionPolicy]) via
 * [start]/[stop], command-intent only, no binding. Also owns the wake/wifi locks for exactly its
 * own lifetime, since they only make sense while this service (and the proxy) is alive.
 */
class CastProxyService : Service() {

    private val wakeLocks by lazy { CastWakeLocks(applicationContext) }
    private val localizedContext by lazy { applicationContext.withAppLocale() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground() must run before anything else in this method - the 5s ANR window
        // applies regardless of which action we were started with.
        ensureNotificationChannel()
        val channelTitle = intent?.getStringExtra(EXTRA_CHANNEL_TITLE).orEmpty()
        val receiverName = intent?.getStringExtra(EXTRA_RECEIVER_NAME).orEmpty()
        startForeground(NOTIFICATION_ID, buildNotification(channelTitle, receiverName))

        when (intent?.action) {
            ACTION_END_SESSION -> {
                AppLog.d(TAG) { "Stop action tapped - ending the Cast session" }
                CastSessionRepository.getInstance(applicationContext).endSession()
                stopServiceForeground()
            }
            ACTION_STOP_FOREGROUND -> {
                AppLog.d(TAG) { "Proxy no longer needed - stopping the foreground service only" }
                stopServiceForeground()
            }
            else -> wakeLocks.acquire()
        }

        // System-killed means the proxy state is gone anyway; the receiver will surface its own
        // error, there is nothing meaningful left here to restart into.
        return START_NOT_STICKY
    }

    private fun stopServiceForeground() {
        wakeLocks.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        wakeLocks.release()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                localizedContext.getString(R.string.cast_session_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun buildNotification(channelTitle: String, receiverName: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).setFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            ),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            endSessionIntent(this),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_cast)
            .setContentTitle(channelTitle.ifEmpty { localizedContext.getString(R.string.app_name) })
            .setContentText(localizedContext.getString(R.string.cast_session_notification_text, receiverName))
            .setContentIntent(contentIntent)
            .addAction(0, localizedContext.getString(R.string.cast_session_stop_action), stopPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private fun startIntent(context: Context, channelTitle: String, receiverName: String): Intent =
            Intent(context, CastProxyService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CHANNEL_TITLE, channelTitle)
                putExtra(EXTRA_RECEIVER_NAME, receiverName)
            }

        private fun stopForegroundIntent(context: Context): Intent =
            Intent(context, CastProxyService::class.java).apply { action = ACTION_STOP_FOREGROUND }

        private fun endSessionIntent(context: Context): Intent =
            Intent(context, CastProxyService::class.java).apply { action = ACTION_END_SESSION }

        fun start(context: Context, channelTitle: String, receiverName: String) {
            startForegroundServiceSafely(context, startIntent(context, channelTitle, receiverName))
        }

        /** Called 1:1 with [com.uacastplayer.data.cast.ProxyServer.stop] - tears down only this
         * service's own foreground/wake-lock lifetime, never the Cast session itself (see
         * [ACTION_STOP_FOREGROUND]). */
        fun stop(context: Context) {
            startForegroundServiceSafely(context, stopForegroundIntent(context))
        }

        /**
         * On API 31+, starting a foreground service from the background (e.g. a Cast session
         * reconnecting while the app is backgrounded) can throw ForegroundServiceStartNotAllowedException.
         * The [com.uacastplayer.data.cast.ProxyServer] this service exists to protect keeps running
         * either way - losing the FGS only means the process is more likely to be reclaimed in the
         * background, not that the stream itself stops - so this degrades instead of crashing.
         */
        private fun startForegroundServiceSafely(context: Context, intent: Intent) {
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                AppLog.w(TAG) { "Foreground service start not allowed: ${e.javaClass.simpleName}" }
            }
        }
    }
}
