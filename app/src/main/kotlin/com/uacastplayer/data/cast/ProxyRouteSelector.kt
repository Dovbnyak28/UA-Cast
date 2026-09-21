package com.uacastplayer.data.cast

import com.uacastplayer.core.cast.CastCompatibilityPolicy
import com.uacastplayer.core.cast.CastCompatibilityVerdict
import com.uacastplayer.core.cast.TsProgramInfoParser
import com.uacastplayer.core.cast.CastRouteKind
import com.uacastplayer.log.AppLog
import com.uacastplayer.proxy.MpegTsSniffer
import com.uacastplayer.proxy.PlaylistDetector
import com.uacastplayer.proxy.RawTsRemuxActivation
import okhttp3.Response

private const val TAG = "ProxyRouteSelector"
private const val PLAYLIST_SNIFF_BYTES = 16L
private const val TS_PROBE_BYTES = 128L * 1024

internal enum class UpstreamRoute { PLAYLIST, REMUX, PASSTHROUGH }

internal data class ProxyRouteDecision(
    val route: UpstreamRoute,
    /** Null for a nested media segment: only top-level channel resources are route attempts. */
    val attemptedRoute: CastRouteKind?,
)

/**
 * Classifies an upstream response without serving it. This is the routing half of [ProxyServer];
 * response ownership, headers and byte copying remain in the server's serving half.
 */
internal object ProxyRouteSelector {
    fun select(
        resource: ResourceEntry,
        response: Response,
        remuxEnabled: Boolean,
    ): ProxyRouteDecision {
        val remuxEligible = resource.type == RESOURCE_TYPE_PLAYLIST
        if (!response.isSuccessful) {
            return ProxyRouteDecision(
                route = UpstreamRoute.PASSTHROUGH,
                attemptedRoute = if (remuxEligible) CastRouteKind.PROXY_REWRITE else null,
            )
        }

        val isPlaylist = PlaylistDetector.isPlaylist(
            response.header("Content-Type"),
            response.peekBody(PLAYLIST_SNIFF_BYTES).bytes(),
        )
        val shouldRemux = !isPlaylist && remuxEligible && shouldRemuxRaw(response, remuxEnabled)
        val route = when {
            isPlaylist -> UpstreamRoute.PLAYLIST
            shouldRemux -> UpstreamRoute.REMUX
            else -> UpstreamRoute.PASSTHROUGH
        }
        val attemptedRoute = if (remuxEligible) {
            if (shouldRemux) CastRouteKind.PROXY_REMUX else CastRouteKind.PROXY_REWRITE
        } else {
            null
        }
        return ProxyRouteDecision(route, attemptedRoute)
    }

    fun shouldRemuxRaw(response: Response, remuxEnabled: Boolean): Boolean {
        val tsProbe = response.peekBody(TS_PROBE_BYTES).bytes()
        val looksLikeTs = MpegTsSniffer.looksLikeMpegTs(tsProbe)
        val verdict = if (looksLikeTs) classifyTsProbe(tsProbe) else CastCompatibilityVerdict.Unknown
        return RawTsRemuxActivation.shouldActivate(
            isHlsPlaylist = false,
            looksLikeMpegTs = looksLikeTs,
            verdict = verdict,
            featureEnabled = remuxEnabled,
        )
    }

    /** An arbitrary third-party byte stream must degrade to Unknown, never break HTTP serving. */
    @Suppress("TooGenericExceptionCaught")
    private fun classifyTsProbe(tsProbe: ByteArray): CastCompatibilityVerdict = try {
        CastCompatibilityPolicy.classify(TsProgramInfoParser.parse(tsProbe))
    } catch (e: Exception) {
        AppLog.e(TAG, e) { "Failed to classify a probed TS stream's codecs; treating as Unknown" }
        CastCompatibilityVerdict.Unknown
    }
}
