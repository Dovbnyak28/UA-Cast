package com.uacastplayer.core.net

import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/**
 * Executes an OkHttp call without turning a cancelled coroutine into a detached network call.
 *
 * OkHttp's blocking [Call.execute] cannot observe coroutine cancellation by itself. That is
 * especially harmful for IPTV origins which allow only one connection: a cancelled warm-up can
 * otherwise keep that connection occupied while real playback is trying to start. [readResponse]
 * deliberately runs before the continuation is resumed, so cancellation also calls [Call.cancel]
 * while a response body is blocked waiting for more bytes, not only while headers are pending.
 * The response is closed on every outcome.
 */
@Suppress("TooGenericExceptionCaught") // Every failure must resume the suspended caller; none is swallowed here.
internal suspend fun <T> Call.executeCancellable(readResponse: (Response) -> T): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use { Result.success(readResponse(it)) }
                    } catch (failure: Throwable) {
                        Result.failure(failure)
                    }
                    continuation.resumeWith(result)
                }
            },
        )
    }
