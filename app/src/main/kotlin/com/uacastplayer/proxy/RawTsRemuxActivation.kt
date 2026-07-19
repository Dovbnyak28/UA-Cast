package com.uacastplayer.proxy

import com.uacastplayer.cast.CastCompatibilityVerdict

/**
 * Decides whether an origin response should be remuxed into live HLS instead of served as a raw
 * passthrough (see docs/PROXY_RULES.md "Raw TS remux"). All four conditions must hold:
 * - it isn't already an HLS playlist (nothing to remux, the existing rewrite path already works),
 * - its bytes actually look like MPEG-TS (an unrelated raw format shouldn't be force-fed to the
 *   TS segmenter),
 * - [com.uacastplayer.cast.CastCompatibilityPolicy] didn't find a confirmed-incompatible codec (an
 *   incompatible codec would still fail after remuxing - only the *container* problem is fixable
 *   here, see `cast/CastCompatibilityPolicy.kt`). Compatible AND Unknown both pass this: a raw TS
 *   stream is never playable as a bare passthrough URL regardless of codec verdict (the receiver
 *   needs HLS/DASH wrapping either way), so an Unknown verdict - PAT/PMT not found within the
 *   probe window, not a confirmed problem - should still get a chance via remux rather than a
 *   passthrough that's guaranteed useless,
 * - the feature hasn't been switched off (an escape hatch for if keyframe detection turns out
 *   unreliable on some real broadcast - see `AppPreferences.rawTsRemuxEnabled`).
 */
object RawTsRemuxActivation {

    fun shouldActivate(
        isHlsPlaylist: Boolean,
        looksLikeMpegTs: Boolean,
        verdict: CastCompatibilityVerdict,
        featureEnabled: Boolean,
    ): Boolean {
        val confirmedIncompatible = verdict is CastCompatibilityVerdict.IncompatibleAudio ||
            verdict is CastCompatibilityVerdict.IncompatibleVideo
        return featureEnabled && !isHlsPlaylist && looksLikeMpegTs && !confirmedIncompatible
    }
}
