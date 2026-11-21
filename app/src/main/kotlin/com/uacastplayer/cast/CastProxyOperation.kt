package com.uacastplayer.cast

import com.uacastplayer.core.concurrent.runCatchingNonFatal

/** The URL prepared for one receiver load, plus the proxy resource used for diagnostics. */
internal data class PreparedCastProxy(val resourceId: String, val localUrl: String)

/**
 * The non-fatal boundary around local-proxy setup invoked from Cast SDK callbacks.
 *
 * A socket bind, resource registration, or URL construction can fail synchronously. Cast callbacks
 * run on the main thread, so allowing one of those failures to escape crashes the app rather than
 * merely losing the fallback. Cancellation and fatal VM errors retain their normal semantics via
 * [runCatchingNonFatal].
 */
internal object CastProxyOperation {
    fun <T> run(operation: () -> T): Result<T> = runCatchingNonFatal(operation)
}
