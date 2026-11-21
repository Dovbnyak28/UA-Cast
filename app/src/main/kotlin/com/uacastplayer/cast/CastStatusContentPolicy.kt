package com.uacastplayer.cast

/**
 * Accepts a receiver status only when it can belong to the load currently owned by the sender.
 *
 * Cast callbacks are asynchronous: after a channel switch, a status for the previous media item
 * can still be delivered even though the SDK callback itself is owned by the same CastSession.
 * A missing content id is treated as unknown (some receiver states do not carry media info), so
 * only an explicit, different id is rejected.
 */
internal object CastStatusContentPolicy {
    fun shouldAccept(statusContentId: String?, expectedContentId: String?): Boolean =
        expectedContentId.isNullOrBlank() ||
            statusContentId.isNullOrBlank() ||
            statusContentId == expectedContentId
}
