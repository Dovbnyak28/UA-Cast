package com.uacastplayer.cast

import android.content.Context
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.uacastplayer.log.AppLog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "CastSessionRepository"

private data class ActiveChannel(val index: Int, val streamUrl: String, val title: String)

/**
 * App-wide singleton (not a ViewModel, since Cast session state must survive navigating away from
 * and back to the player) wrapping the real GMS Cast callbacks and feeding them through
 * [CastLoadResultReducer] / [CastReceiverStatusReducer]. Anything driven by [CastSideEffect] -
 * pausing/resuming the local player, applying a pending channel switch - is left for callers
 * (chiefly PlayerViewModel) to react to via [sideEffects].
 */
class CastSessionRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private var castContext: CastContext? = null
    private var currentSession: CastSession? = null
    private var activeChannel: ActiveChannel? = null

    private val _state = MutableStateFlow(CastPlaybackState())
    val state: StateFlow<CastPlaybackState> = _state.asStateFlow()

    private val _sideEffects = MutableSharedFlow<CastSideEffect>(extraBufferCapacity = 8)
    val sideEffects: SharedFlow<CastSideEffect> = _sideEffects.asSharedFlow()

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStarted(session: CastSession, sessionId: String) = onSessionActive(session)
        override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionEnded(session: CastSession, error: Int) = onSessionInactive()
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = onSessionActive(session)
        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
        override fun onSessionSuspended(session: CastSession, reason: Int) = onSessionInactive()
    }

    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val status = currentSession?.remoteMediaClient?.mediaStatus ?: return
            val receiverStatus = mapPlayerState(status.playerState)
            val idleReason = mapIdleReason(status.idleReason)
            applyResult(CastReceiverStatusReducer.reduce(_state.value, receiverStatus, idleReason))
        }
    }

    init {
        try {
            castContext = CastContext.getSharedInstance(appContext)
            castContext?.sessionManager?.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
        } catch (e: Exception) {
            AppLog.w(TAG) { "Cast context unavailable: ${e.javaClass.simpleName}" }
        }
    }

    /**
     * Called by the player whenever its active channel changes, cast or not. While a session is
     * connected this both queues the index as pending (for handoff on disconnect) and pushes a
     * fresh direct load to the receiver immediately.
     */
    fun setActiveChannel(index: Int, streamUrl: String, title: String) {
        activeChannel = ActiveChannel(index, streamUrl, title)
        if (currentSession != null) {
            _state.value = CastReceiverStatusReducer.requestChannelSwitch(_state.value, index)
            loadDirect(streamUrl, title)
        }
    }

    private fun onSessionActive(session: CastSession) {
        currentSession = session
        session.remoteMediaClient?.registerCallback(remoteMediaClientCallback)
        activeChannel?.let { loadDirect(it.streamUrl, it.title) }
    }

    private fun onSessionInactive() {
        currentSession?.remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
        currentSession = null
        applyResult(CastReceiverStatusReducer.reduce(_state.value, ReceiverStatus.DISCONNECTED))
    }

    private fun loadDirect(streamUrl: String, title: String) {
        val client = currentSession?.remoteMediaClient ?: return
        _state.update { it.copy(loadPhase = CastLoadPhase.LOADING) }
        val request = CastMediaLoader.buildRequest(streamUrl, title)
        client.load(request).setResultCallback { result ->
            val loadResult = if (result.status.isSuccess) {
                CastLoadResult.Success
            } else {
                CastLoadResult.Failure("status_${result.status.statusCode}")
            }
            applyResult(CastLoadResultReducer.reduce(_state.value, loadResult))
        }
    }

    private fun applyResult(result: CastReducerResult) {
        _state.value = result.state
        result.effects.forEach { effect ->
            if (!_sideEffects.tryEmit(effect)) {
                AppLog.w(TAG) { "Dropped cast side effect, no buffer space: ${effect.javaClass.simpleName}" }
            }
        }
    }

    private fun mapPlayerState(playerState: Int): ReceiverStatus = when (playerState) {
        MediaStatus.PLAYER_STATE_PLAYING -> ReceiverStatus.PLAYING
        MediaStatus.PLAYER_STATE_PAUSED -> ReceiverStatus.PAUSED
        MediaStatus.PLAYER_STATE_BUFFERING, MediaStatus.PLAYER_STATE_LOADING -> ReceiverStatus.BUFFERING
        else -> ReceiverStatus.IDLE
    }

    private fun mapIdleReason(reason: Int): IdleReason = when (reason) {
        MediaStatus.IDLE_REASON_FINISHED -> IdleReason.FINISHED
        MediaStatus.IDLE_REASON_ERROR -> IdleReason.ERROR
        MediaStatus.IDLE_REASON_CANCELED -> IdleReason.CANCELLED
        MediaStatus.IDLE_REASON_INTERRUPTED -> IdleReason.INTERRUPTED
        else -> IdleReason.NONE
    }

    companion object {
        @Volatile private var instance: CastSessionRepository? = null

        fun getInstance(context: Context): CastSessionRepository =
            instance ?: synchronized(this) {
                instance ?: CastSessionRepository(context).also { instance = it }
            }
    }
}
