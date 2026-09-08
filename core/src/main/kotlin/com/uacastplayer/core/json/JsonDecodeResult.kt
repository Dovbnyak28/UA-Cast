package com.uacastplayer.core.json

/** Typed outcome for tolerant persisted-JSON readers. The failure carries no input or exception
 * message because either may contain a channel URL or another user-owned value. */
sealed interface JsonDecodeResult<out T> {
    data class Success<T>(val value: T) : JsonDecodeResult<T>
    data class Malformed(val failureType: String) : JsonDecodeResult<Nothing>
}

/** One non-fatal boundary for all small [MiniJson]-backed stores. */
@Suppress("TooGenericExceptionCaught")
inline fun <T> jsonDecodeResult(decode: () -> T): JsonDecodeResult<T> = try {
    JsonDecodeResult.Success(decode())
} catch (error: Exception) {
    JsonDecodeResult.Malformed(error.javaClass.simpleName.ifBlank { "Exception" })
}
