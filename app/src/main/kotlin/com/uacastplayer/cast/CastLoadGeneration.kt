package com.uacastplayer.cast

import com.uacastplayer.core.concurrent.LatestResultGuard

/**
 * Identifies the only Cast load callback that may still affect repository state.
 *
 * A newer load supersedes the previous one, while a session transition invalidates the current
 * id even when no replacement load is issued. The latter matters when a GMS `PendingResult`
 * completes after its Cast session has already ended: that callback must not restart the proxy or
 * mutate the disconnected state.
 */
internal typealias CastLoadGeneration = LatestResultGuard
