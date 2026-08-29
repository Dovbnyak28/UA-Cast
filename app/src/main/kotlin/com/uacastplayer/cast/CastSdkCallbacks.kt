package com.uacastplayer.cast

import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient

/** Small SDK-facing adapter; repository policy consumes typed lifecycle events instead of callbacks. */
internal class CastSdkSessionListener(
    private val onEvent: (CastSdkSessionEvent) -> Unit,
) : SessionManagerListener<CastSession> {
    override fun onSessionStarting(session: CastSession) = Unit

    override fun onSessionStarted(session: CastSession, sessionId: String) {
        onEvent(CastSdkSessionEvent.Started(session, sessionId))
    }

    override fun onSessionStartFailed(session: CastSession, error: Int) {
        onEvent(CastSdkSessionEvent.StartFailed(error))
    }

    override fun onSessionEnding(session: CastSession) = Unit

    override fun onSessionEnded(session: CastSession, error: Int) {
        onEvent(CastSdkSessionEvent.Ended(session, error))
    }

    override fun onSessionResuming(session: CastSession, sessionId: String) {
        onEvent(CastSdkSessionEvent.Resuming(sessionId))
    }

    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
        onEvent(CastSdkSessionEvent.Resumed(session))
    }

    override fun onSessionResumeFailed(session: CastSession, error: Int) {
        onEvent(CastSdkSessionEvent.ResumeFailed(error))
    }

    override fun onSessionSuspended(session: CastSession, reason: Int) {
        onEvent(CastSdkSessionEvent.Suspended(session, reason))
    }
}

internal sealed interface CastSdkSessionEvent {
    data class Started(val session: CastSession, val sessionId: String) : CastSdkSessionEvent
    data class StartFailed(val error: Int) : CastSdkSessionEvent
    data class Ended(val session: CastSession, val error: Int) : CastSdkSessionEvent
    data class Resuming(val sessionId: String) : CastSdkSessionEvent
    data class Resumed(val session: CastSession) : CastSdkSessionEvent
    data class ResumeFailed(val error: Int) : CastSdkSessionEvent
    data class Suspended(val session: CastSession, val reason: Int) : CastSdkSessionEvent
}

/** Keeps the GMS callback object free of playback and recovery decisions. */
internal class CastSdkRemoteMediaCallback(
    private val onStatusUpdated: () -> Unit,
) : RemoteMediaClient.Callback() {
    override fun onStatusUpdated() = onStatusUpdated.invoke()
}
