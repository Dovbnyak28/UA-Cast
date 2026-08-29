package com.uacastplayer.cast

/**
 * What a Cast SDK load-result status code actually means for THIS load request. A watchdog
 * fallback (or a fast channel switch) can issue a second `load()` while the first is still in
 * flight - the first request's own result callback then fires with a status that looks like a
 * failure (2103 REPLACED, or the media-queue noise of 2002, which this app never triggers on
 * purpose since it never uses Cast's queue APIs) but isn't one: the request was simply superseded
 * by the newer load, and reacting to it as a real failure would double-process the same channel
 * (recording a false incompatibility, bouncing back to local playback under a receiver that's
 * actually fine). Only 2001 (INVALID_REQUEST) is a genuine failure of the specific request that
 * got it; anything else unrecognized here (15 LOAD_FAILED, 2100 FAILED, or any other code) is a
 * generic failure worth surfacing the same way.
 */
sealed interface LoadStatusOutcome {
    val statusCode: Int
    val statusName: String

    data class Superseded(override val statusCode: Int, override val statusName: String) : LoadStatusOutcome
    data class Rejected(override val statusCode: Int, override val statusName: String) : LoadStatusOutcome
    data class Failed(override val statusCode: Int, override val statusName: String) : LoadStatusOutcome
}

object LoadStatusClassifier {
    private const val INVALID_REQUEST = 2001
    private const val MEDIA_QUEUE_NO_SESSION = 2002
    private const val REPLACED = 2103
    private const val LOAD_FAILED = 15
    private const val FAILED = 2100

    private val KNOWN_NAMES = mapOf(
        INVALID_REQUEST to "INVALID_REQUEST",
        MEDIA_QUEUE_NO_SESSION to "MEDIA_QUEUE_NO_SESSION",
        REPLACED to "REPLACED",
        LOAD_FAILED to "LOAD_FAILED",
        FAILED to "FAILED",
    )

    fun classify(statusCode: Int): LoadStatusOutcome {
        val name = KNOWN_NAMES[statusCode] ?: "UNKNOWN"
        return when (statusCode) {
            MEDIA_QUEUE_NO_SESSION, REPLACED -> LoadStatusOutcome.Superseded(statusCode, name)
            INVALID_REQUEST -> LoadStatusOutcome.Rejected(statusCode, name)
            else -> LoadStatusOutcome.Failed(statusCode, name)
        }
    }
}
