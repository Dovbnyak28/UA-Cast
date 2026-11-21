package com.uacastplayer.cast

import com.uacastplayer.core.cast.CastRouteKind
import com.uacastplayer.diagnostics.CastRouteOutcome

internal data class CastRouteObservation(
    val streamUrl: String?,
    val receiverId: String?,
    val deliveryMode: CastDeliveryMode,
    val route: CastRouteKind,
)

/** Channel-scoped evidence, not SDK lifecycle state. A direct failure alone is not proof that a
 * receiver needs proxying. Only successful proxy playback of that same stream earns that record. */
internal class CastRouteHistory(
    private val observe: () -> CastRouteObservation,
    private val remember: (streamUrl: String, receiverId: String) -> Unit,
    private val record: (CastRouteKind, CastRouteOutcome) -> Unit,
) {
    var everReachedPlaying = false
        private set
    private var abandonedDirectStream: String? = null

    fun reset() {
        everReachedPlaying = false
        abandonedDirectStream = null
    }

    fun abandonDirect(streamUrl: String) {
        abandonedDirectStream = streamUrl
        record(CastRouteKind.DIRECT, CastRouteOutcome.FAILED)
    }

    fun clearAbandonedRoute() {
        abandonedDirectStream = null
    }

    fun onStatus(status: ReceiverStatus) {
        val observation = observe()
        rememberProvenProxyRoute(status, observation)
        if (status == ReceiverStatus.PLAYING && !everReachedPlaying) {
            everReachedPlaying = true
            record(observation.route, CastRouteOutcome.REACHED_PLAYING)
        }
    }

    fun onGiveUp(streamUrl: String, incompatible: Boolean) {
        val observation = observe()
        if (IncompatibilityRecordingPolicy.shouldRecord(incompatible, everReachedPlaying)) {
            observation.receiverId?.let { remember(streamUrl, it) }
        }
        if (!everReachedPlaying) record(observation.route, CastRouteOutcome.FAILED)
    }

    private fun rememberProvenProxyRoute(status: ReceiverStatus, observation: CastRouteObservation) {
        val abandoned = abandonedDirectStream ?: return
        if (!DirectRouteMemoryPolicy.provenProxyOnly(observation.deliveryMode, status)) return
        abandonedDirectStream = null
        if (StaleChannelGuard.isCurrent(abandoned, observation.streamUrl)) {
            observation.receiverId?.let { remember(abandoned, it) }
        }
    }
}
