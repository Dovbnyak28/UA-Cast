package com.uacastplayer.core.concurrent

import kotlinx.coroutines.CancellationException

/**
 * The boundary-safe counterpart of Kotlin's [runCatching].
 *
 * `runCatching` catches [Throwable], which includes coroutine cancellation and fatal VM conditions
 * such as `OutOfMemoryError`. Boundary code often does want to turn an ordinary provider, socket,
 * parser, or framework [Exception] into a fallback value, but neither of those two categories is
 * an ordinary failure. Keeping this rule in one function prevents each call site from having to
 * remember the cancellation-first catch order.
 */
@Suppress("TooGenericExceptionCaught") // Exception is exactly the non-fatal boundary defined here
inline fun <T> runCatchingNonFatal(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
