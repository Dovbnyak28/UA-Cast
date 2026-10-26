package com.uacastplayer.ads

import com.uacastplayer.premium.Entitlements

/** Explicit allowlist: no player, interstitial, app-open, remote-control or TV placements. */
enum class AdPlacement { HOME_BANNER, CHANNELS_BANNER }

/** Permission for requests, not permission for personalised targeting. Unknown always blocks. */
enum class AdRequestPermission { UNKNOWN, ALLOWED, DENIED }

/** Off until a real integration and its privacy flow are deliberately configured. */
data class BannerAdConfiguration(
    val enabledPlacements: Set<AdPlacement> = emptySet(),
    val requestPermission: AdRequestPermission = AdRequestPermission.UNKNOWN,
)

/** Derived from the existing entitlement snapshot; never persist a separate "ads removed" flag. */
data class BannerAdAudience(
    val entitlements: Entitlements? = null,
    val blocked: Boolean = false,
)

object BannerAdPolicy {
    fun permitsRequests(placement: AdPlacement, configuration: BannerAdConfiguration,
        audience: BannerAdAudience, foreground: Boolean, television: Boolean): Boolean =
        audience.entitlements != null &&
            !audience.entitlements.effectiveTier.isPaid &&
            !audience.blocked && foreground && !television &&
            configuration.requestPermission == AdRequestPermission.ALLOWED &&
            placement in configuration.enabledPlacements
}
