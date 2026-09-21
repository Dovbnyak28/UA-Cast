package com.uacastplayer.data.update

import com.uacastplayer.update.InstallSessionOutcome
import com.uacastplayer.update.InstallSessionResult
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Carries an install session's result from [UpdateInstallReceiver] to whoever is showing progress.
 *
 * A process-wide object, in the same shape as [com.uacastplayer.log.LogBuffer] and
 * [com.uacastplayer.log.CrashLog], and for the same reason: the two ends cannot be handed to each
 * other. A `BroadcastReceiver` is constructed by the system with no arguments, and the thing
 * waiting for its answer is a controller inside a `ViewModel` that may not have existed when the
 * session was committed and may not exist when it ends.
 *
 * In memory only, and that is correct rather than a shortcut. If the process died in between - which
 * a successful install of this very app guarantees - there is no progress left on screen to
 * correct, and the next launch starts at [com.uacastplayer.update.UpdateInstallState.Idle] already.
 *
 * `extraBufferCapacity = 1` with [BufferOverflow.DROP_OLDEST] so [report] never suspends and never
 * blocks: it is called from `onReceive`, on the main thread, inside the window the system gives a
 * broadcast to return. A result nobody is listening for is dropped, which is the right answer -
 * there is no UI to move.
 */
object InstallOutcomeBus {

    private val _outcomes = MutableSharedFlow<InstallSessionResult>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val outcomes: SharedFlow<InstallSessionResult> = _outcomes.asSharedFlow()

    fun report(sessionId: Int, outcome: InstallSessionOutcome) {
        _outcomes.tryEmit(InstallSessionResult(sessionId, outcome))
    }
}
