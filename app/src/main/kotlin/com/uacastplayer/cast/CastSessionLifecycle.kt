package com.uacastplayer.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.log.AppLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor

private const val TAG = "CastSessionLifecycle"

/** Bridges Cast SDK initialization and callbacks without owning playback decisions. */
internal class CastSessionLifecycle(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.io,
    private val hasCurrentSession: () -> Boolean,
    private val onEvent: (CastSdkSessionEvent) -> Unit,
) {

    private val appContext = context.applicationContext
    private var castContext: CastContext? = null
    private val sessionManagerListener = CastSdkSessionListener(onEvent)

    /** Resolves Cast off the main thread, while GMS delivers the callback on main. */
    @Suppress("TooGenericExceptionCaught")
    fun initialize() {
        try {
            CastContext.getSharedInstance(appContext, ioDispatcher.asExecutor())
                .addOnSuccessListener { context ->
                    castContext = context
                    context.sessionManager.addSessionManagerListener(
                        sessionManagerListener,
                        CastSession::class.java,
                    )
                    adoptSessionAlreadyRunning(context.sessionManager)
                }
                .addOnFailureListener { error ->
                    AppLog.w(TAG) { "Cast context unavailable: ${error.javaClass.simpleName}" }
                }
        } catch (error: Exception) {
            AppLog.w(TAG) { "Cast context unavailable: ${error.javaClass.simpleName}" }
        }
    }

    fun endSession() {
        runCatchingNonFatal { castContext?.sessionManager?.endCurrentSession(true) }.onFailure { error ->
            AppLog.w(TAG) { "Cast stop rejected: ${error.javaClass.simpleName}" }
        }
    }

    fun isCurrent(session: CastSession): Boolean = castContext?.sessionManager?.currentCastSession === session

    private fun adoptSessionAlreadyRunning(sessionManager: SessionManager) {
        if (hasCurrentSession()) return
        sessionManager.currentCastSession
            ?.takeIf { it.isConnected }
            ?.let { session ->
                AppLog.d(TAG) { "Adopting a cast session that was already connected" }
                onEvent(CastSdkSessionEvent.Started(session, null))
            }
    }
}
