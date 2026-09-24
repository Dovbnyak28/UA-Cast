package com.uacastplayer.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.uacastplayer.R
import com.uacastplayer.data.prefs.withAppLocale
import com.uacastplayer.data.cast.CastWakeLocks
import com.uacastplayer.dlna.DlnaSessionRepository
import com.uacastplayer.log.AppLog
import java.util.concurrent.atomic.AtomicLongArray

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
private const val EXTRA_TARGET = "target"
private const val EXTRA_COMMAND_GENERATION = "command_generation"

/**
 * Which cast target the proxy session being protected belongs to - the only thing this service
 * needs to tell them apart, both for owner-scoped teardown and so the notification's Stop action
 * ends the session currently shown in the notification.
 */
enum class CastProxyTarget { CHROMECAST, DLNA }

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
    private val mainHandler = Handler(Looper.getMainLooper())

    private var ownership = CastProxyOwnership()
    private val notificationDetails = mutableMapOf<CastProxyTarget, NotificationDetails>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runningInstance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground() must run before anything else in this method - the 5s ANR window
        // applies regardless of which action we were started with.
        ensureNotificationChannel()
        val commandTarget = intent.targetOrDefault()
        val channelTitle = intent?.getStringExtra(EXTRA_CHANNEL_TITLE).orEmpty()
        val receiverName = intent?.getStringExtra(EXTRA_RECEIVER_NAME).orEmpty()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(channelTitle, receiverName, commandTarget),
        )

        val commandGeneration = intent?.getLongExtra(EXTRA_COMMAND_GENERATION, 0L) ?: 0L
        // A stop can be posted to this service's main handler while a newer start is still being
        // delivered by ActivityManager. Ignore the older service command after satisfying the FGS
        // start requirement; otherwise the service would be left foreground-less while the proxy
        // socket is still serving the new session.
        if (!CastProxyCommandPolicy.accepts(latestRequestedGeneration(commandTarget), commandGeneration)) {
            if (ownership.activeTargets.isEmpty()) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            } else {
                // A stale command for one target must not tear down protection for another target
                // that is still active in this shared service instance.
                refreshNotification()
            }
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_END_SESSION -> {
                AppLog.d(TAG) { "Stop action tapped - ending the $commandTarget session" }
                when (commandTarget) {
                    CastProxyTarget.CHROMECAST -> CastSessionRepository.getInstance(applicationContext).endSession()
                    CastProxyTarget.DLNA -> DlnaSessionRepository.getInstance(applicationContext).stop()
                }
                removeOwner(commandTarget)
            }
            ACTION_STOP_FOREGROUND -> {
                AppLog.d(TAG) { "$commandTarget proxy no longer needs foreground protection" }
                removeOwner(commandTarget)
            }
            else -> {
                val hadOwners = ownership.activeTargets.isNotEmpty()
                ownership = CastProxyOwnershipPolicy.started(ownership, commandTarget)
                notificationDetails[commandTarget] = NotificationDetails(channelTitle, receiverName)
                if (!hadOwners) wakeLocks.acquire()
                refreshNotification()
            }
        }

        // System-killed means the proxy state is gone anyway; the receiver will surface its own
        // error, there is nothing meaningful left here to restart into.
        return START_NOT_STICKY
    }

    private fun removeOwner(target: CastProxyTarget) {
        ownership = CastProxyOwnershipPolicy.stopped(ownership, target)
        notificationDetails -= target
        if (ownership.activeTargets.isEmpty()) {
            wakeLocks.release()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            refreshNotification()
        }
    }

    private fun refreshNotification() {
        val target = ownership.displayedTarget ?: return
        val details = notificationDetails[target] ?: NotificationDetails("", "")
        startForeground(
            NOTIFICATION_ID,
            buildNotification(details.channelTitle, details.receiverName, target),
        )
    }

    override fun onDestroy() {
        if (runningInstance === this) runningInstance = null
        mainHandler.removeCallbacksAndMessages(null)
        ownership = CastProxyOwnership()
        notificationDetails.clear()
        wakeLocks.release()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                ?.takeIf { it.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null }
                ?.createNotificationChannel(
                    NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        localizedContext.getString(R.string.cast_session_channel_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
        }
    }

    private fun buildNotification(
        channelTitle: String,
        receiverName: String,
        target: CastProxyTarget,
    ): Notification {
        // Resolve the launcher activity by package so this cast-layer service does not depend on
        // the app-root MainActivity. The launcher intent is explicit and therefore remains
        // resolvable on API 30+ even with package-visibility restrictions.
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            PendingIntent.getActivity(
                this,
                0,
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            target.ordinal + 1,
            endSessionIntent(this, target),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_cast)
            .setContentTitle(channelTitle.ifEmpty { localizedContext.getString(R.string.app_name) })
            .setContentText(localizedContext.getString(R.string.cast_session_notification_text, receiverName))
            .addAction(0, localizedContext.getString(R.string.cast_session_stop_action), stopPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (contentIntent != null) builder.setContentIntent(contentIntent)
        return builder.build()
    }

    companion object {
        private fun startIntent(
            context: Context,
            channelTitle: String,
            receiverName: String,
            target: CastProxyTarget,
            commandGeneration: Long,
        ): Intent = Intent(context, CastProxyService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_CHANNEL_TITLE, channelTitle)
            putExtra(EXTRA_RECEIVER_NAME, receiverName)
            putExtra(EXTRA_TARGET, target.name)
            putExtra(EXTRA_COMMAND_GENERATION, commandGeneration)
        }

        private fun stopForegroundIntent(context: Context, target: CastProxyTarget): Intent =
            Intent(context, CastProxyService::class.java).apply {
                action = ACTION_STOP_FOREGROUND
                putExtra(EXTRA_TARGET, target.name)
            }

        private fun endSessionIntent(context: Context, target: CastProxyTarget): Intent =
            Intent(context, CastProxyService::class.java).apply {
                action = ACTION_END_SESSION
                putExtra(EXTRA_TARGET, target.name)
            }

        fun start(
            context: Context,
            channelTitle: String,
            receiverName: String,
            target: CastProxyTarget = CastProxyTarget.CHROMECAST,
        ) {
            val commandGeneration = nextCommandGeneration(target)
            startForegroundServiceSafely(
                context,
                startIntent(context, channelTitle, receiverName, target, commandGeneration),
            )
        }

        /** Called 1:1 with [com.uacastplayer.data.cast.ProxyServer.stop] - tears down only this
         * service's own foreground/wake-lock lifetime, never the Cast session itself (see
         * [ACTION_STOP_FOREGROUND]). When the service is already alive, dispatch directly to its
         * main thread. Starting a foreground service just to deliver a stop command is forbidden
         * from the background on Android 12+ and used to leave the wake locks running silently. */
        fun stop(
            context: Context,
            target: CastProxyTarget = CastProxyTarget.CHROMECAST,
        ) {
            val commandGeneration = nextCommandGeneration(target)
            val service = runningInstance
            if (service != null) {
                service.mainHandler.post {
                    if (runningInstance === service &&
                        CastProxyCommandPolicy.accepts(
                            latestRequestedGeneration(target),
                            commandGeneration,
                        )
                    ) {
                        service.removeOwner(target)
                    }
                }
            } else {
                // There is no owner to remove if the service has not been created (or was already
                // destroyed). stopService is safe from the background and also cancels a pending
                // start request without attempting another restricted FGS launch.
                context.stopService(Intent(context, CastProxyService::class.java))
            }
        }

        /**
         * On API 31+, starting a foreground service from the background (e.g. a Cast session
         * reconnecting while the app is backgrounded) can throw ForegroundServiceStartNotAllowedException.
         * The [com.uacastplayer.data.cast.ProxyServer] this service exists to protect keeps running
         * either way - losing the FGS only means the process is more likely to be reclaimed in the
         * background, not that the stream itself stops - so this degrades instead of crashing.
         */
        @Suppress("TooGenericExceptionCaught") // not narrowed to ForegroundServiceStartNotAllowedException
        // (see doc above) - any other framework failure starting the service should degrade the same way.
        private fun startForegroundServiceSafely(context: Context, intent: Intent) {
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                AppLog.w(TAG) { "Foreground service start not allowed: ${e.javaClass.simpleName}" }
            }
        }

        @Volatile
        private var runningInstance: CastProxyService? = null
        private val latestRequestedGenerations = AtomicLongArray(CastProxyTarget.entries.size)

        private fun nextCommandGeneration(target: CastProxyTarget): Long =
            latestRequestedGenerations.incrementAndGet(target.ordinal)

        private fun latestRequestedGeneration(target: CastProxyTarget): Long =
            latestRequestedGenerations.get(target.ordinal)
    }

    private data class NotificationDetails(
        val channelTitle: String,
        val receiverName: String,
    )
}

private fun Intent?.targetOrDefault(): CastProxyTarget = this?.getStringExtra(EXTRA_TARGET)
    ?.let { name -> CastProxyTarget.entries.firstOrNull { it.name == name } }
    ?: CastProxyTarget.CHROMECAST
