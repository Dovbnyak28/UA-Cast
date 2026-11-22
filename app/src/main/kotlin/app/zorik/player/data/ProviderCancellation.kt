package app.zorik.player.data

import android.os.CancellationSignal
import app.zorik.player.log.AppLog

/**
 * Retire provider access without letting its callback break the owner's cancellation or FD cleanup.
 * Even a provider's CancellationException is a callback failure here: the owning request is already
 * being retired. The coroutine body must still propagate its own cancellation normally. Fatal VM
 * errors remain outside this Exception boundary.
 */
@Suppress("TooGenericExceptionCaught") // The untrusted provider's cancellation callback is the boundary.
internal fun CancellationSignal.cancelProviderSafely() {
    try {
        cancel()
    } catch (error: Exception) {
        AppLog.w("ProviderCancellation") { "Provider cancellation callback failed: ${error.javaClass.simpleName}" }
    }
}
