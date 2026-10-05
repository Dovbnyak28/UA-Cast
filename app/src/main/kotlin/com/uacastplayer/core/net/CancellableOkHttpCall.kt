package com.uacastplayer.core.net

import com.uacastplayer.core.concurrent.AppDispatchers
import java.io.IOException
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/**
 * Executes an OkHttp call without turning a cancelled coroutine into a detached network call.
 *
 * OkHttp's blocking [Call.execute] cannot observe coroutine cancellation by itself. That is
 * especially harmful for IPTV origins which allow only one connection: a cancelled warm-up can
 * otherwise keep that connection occupied while real playback is trying to start. [Call.cancel]
 * remains bound to the owner while [readResponse] waits for body bytes, not only while headers are
 * pending. The response is closed on every outcome.
 *
 * Only headers are handed off by the OkHttp callback. Blocking body work belongs to the caller's
 * IO coroutine, so cancellation cannot release its mutexes/files while a detached callback keeps
 * using them. A response lost during the cancellable header handoff is closed without being read.
 */
@OptIn(InternalCoroutinesApi::class)
internal suspend fun <T> Call.executeCancellable(readResponse: (Response) -> T): T =
    withContext(AppDispatchers.io) {
        val owner = currentCoroutineContext().job
        owner.ensureActive()
        val cancelOnCompletion = owner.invokeOnCompletion(onCancelling = true) { cause ->
            if (cause != null) cancel()
        }
        try {
            awaitResponse().use { response ->
                owner.ensureActive()
                val result = try {
                    readResponse(response)
                } catch (error: IOException) {
                    // Call.cancel can surface as a socket IOException while reading. The retired
                    // owner must still observe cancellation, not retry it as a network failure.
                    owner.ensureActive()
                    throw error
                }
                owner.ensureActive()
                result
            }
        } finally {
            cancelOnCompletion.dispose()
        }
    }

private suspend fun Call.awaitResponse(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response, onCancellation = { _, unclaimed, _ ->
                        unclaimed.close()
                    })
                }
            },
        )
    }
