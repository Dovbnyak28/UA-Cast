package com.uacastplayer.cast

import com.uacastplayer.data.cast.DiagnosticResultCache
import com.uacastplayer.data.cast.ProxySourceDiagnostic
import com.uacastplayer.core.net.HttpHeaderValuePolicy
import com.uacastplayer.data.cast.RESOURCE_TYPE_PLAYLIST
import com.uacastplayer.data.cast.ResourceEntry
import com.uacastplayer.data.cast.resourceId

/**
 * Owns codec/source diagnostic cache lifecycle without knowing anything about the Cast
 * SDK or playback state. Only observations from the current proxy producer reach this cache.
 * Access headers are part of the identity, exactly as they are for the proxy's resources.
 * This collaborator does no network I/O, including during direct Cast playback.
 */
internal class CastDiagnosticCoordinator(
    private val cache: DiagnosticResultCache = DiagnosticResultCache(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun cached(channel: CastChannel) = cache.get(identity(channel), nowMillis())

    /**
     * Caller must validate the proxy attempt/producer after dispatching to Main, before recording.
     */
    fun record(channel: CastChannel, diagnostic: ProxySourceDiagnostic) = cache.merge(
        identity(channel), diagnostic.verdict, diagnostic.sourceKind, nowMillis(),
    )

    private fun identity(channel: CastChannel): String = ResourceEntry(
        RESOURCE_TYPE_PLAYLIST, channel.streamUrl,
        HttpHeaderValuePolicy.userAgentOrDefault(channel.userAgent), HttpHeaderValuePolicy.sanitize(channel.referrer),
    ).resourceId()
}
