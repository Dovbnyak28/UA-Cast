package com.uacastplayer.cast

import com.uacastplayer.core.cast.AudioCodec
import com.uacastplayer.core.cast.VideoCodec

/** What the player should tell the user about a cast that is not playing, if anything. */
sealed interface CastStatusMessage {
    data class IncompatibleVideo(val codec: VideoCodec) : CastStatusMessage
    data object ProxyUnavailableIpv4Only : CastStatusMessage
    data object Recovering : CastStatusMessage
    data class LikelyIncompatibleVideo(val codec: VideoCodec) : CastStatusMessage
    data class LikelyIncompatibleAudio(val codec: AudioCodec) : CastStatusMessage
    data object ReceiverLoadFailed : CastStatusMessage
}

/**
 * Which explanation wins when several could apply, and when a still-retrying cast stops being
 * described as "recovering".
 *
 * This is a policy rather than a `when` in the composable because the defect it exists to fix was a
 * *precedence* defect, and precedence is exactly what a UI-only branch chain cannot be tested for.
 * The recovery branch sat above the codec hints on the reasoning that a hint "only ever supplies a
 * likely cause for a failure that already happened (recovery gave up)". Recovery never gives up:
 * [CastRecoveryPolicy] returns GiveUp only for a confirmed MPEG-2 verdict and otherwise reloads
 * forever at a steady backoff. So for every other failure the recovery branch matched permanently
 * and the two hint branches below it were unreachable by construction - the app held an exact
 * diagnosis (audioHint=Ac3), printed the very same codec a few dp lower as part of the channel's
 * own details, and showed "Restoring cast..." indefinitely instead of connecting the two.
 */
object CastStatusMessagePolicy {

    /**
     * Whether a cast that is still being retried should be explained rather than described as
     * recovering.
     *
     * The distinction the recovery policy does not draw: a receiver that *played and then dropped*
     * is a hiccup worth retrying quietly, while one that has **never rendered a single millisecond**
     * is not recovering from anything - there is nothing to return to. Both produce identical
     * IDLE/ERROR statuses, and only [everReachedPlaying] separates them.
     *
     * Narrowed by two more conditions so a genuinely transient failure is never mislabelled:
     * - **[CastDeliveryMode.Proxy] only.** On Direct the fallback has not been tried yet, so
     *   "recovering" is still honest - the app has a route left that may well work.
     * - **[CastRecoveryPolicy.MAX_ATTEMPTS] reached**, i.e. the fast 2s/4s/8s attempts are spent and
     *   reloads have settled into steady-state backoff. That is the recovery policy's own line
     *   between "trying hard" and "waiting indefinitely", so it is the honest moment to stop calling
     *   it recovery. Retrying continues either way; only the wording changes.
     */
    fun isRecoveringWithoutPlayback(
        everReachedPlaying: Boolean,
        deliveryMode: CastDeliveryMode,
        attempt: Int,
    ): Boolean = !everReachedPlaying &&
        deliveryMode == CastDeliveryMode.Proxy &&
        attempt >= CastRecoveryPolicy.MAX_ATTEMPTS

    /**
     * Ordered most specific first. A confirmed incompatibility beats everything (it is the one
     * verdict that stops the cast outright); an IPv4-only proxy failure names a cause nothing can
     * retry past; and the codec hints only ever qualify a failure that has actually happened -
     * either the receiver refused the load, or [isRecoveringWithoutPlayback] has concluded the
     * retries are not going anywhere.
     */
    @Suppress("ReturnCount")
    fun messageFor(state: CastPlaybackState): CastStatusMessage? {
        val incompatibility = state.codecIncompatibility
        if (incompatibility is CodecIncompatibility.Video) {
            return CastStatusMessage.IncompatibleVideo(incompatibility.codec)
        }
        if (state.isRecovering && !state.recoveringWithoutPlayback) return CastStatusMessage.Recovering
        if (state.proxyUnavailableIpv4Only) return CastStatusMessage.ProxyUnavailableIpv4Only

        if (!state.receiverLoadFailed && !state.recoveringWithoutPlayback) return null
        val hint = state.likelyCompatibilityHint
        hint?.videoHint?.let { return CastStatusMessage.LikelyIncompatibleVideo(it) }
        hint?.audioHint?.let { return CastStatusMessage.LikelyIncompatibleAudio(it) }
        return CastStatusMessage.ReceiverLoadFailed
    }
}
