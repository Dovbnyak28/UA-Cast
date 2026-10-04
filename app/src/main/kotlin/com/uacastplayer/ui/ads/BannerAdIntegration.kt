package com.uacastplayer.ui.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.uacastplayer.ads.AdPlacement
import com.uacastplayer.ads.BannerAdAudience
import com.uacastplayer.ads.BannerAdConfiguration

/**
 * Future SDK boundary, not a singleton SDK initializer. Called only while requests are permitted.
 * Own requests/views in composition; cancellation, removal and backgrounding must release them.
 * Draw nothing until an ad is ready, and on no-fill/error. Use BannerAdFrame only for loaded content.
 * Do not retain an Activity in a long-lived provider, preload outside this gate or retry forever.
 */
interface BannerAdRenderer {
    @Composable
    fun Render(placement: AdPlacement, modifier: Modifier)
}

data class BannerAdIntegration(
    val renderer: BannerAdRenderer,
    val configuration: BannerAdConfiguration = BannerAdConfiguration(),
) {
    companion object {
        val Disabled = BannerAdIntegration(DisabledBannerAdRenderer)
    }
}

private object DisabledBannerAdRenderer : BannerAdRenderer {
    @Composable override fun Render(placement: AdPlacement, modifier: Modifier) = Unit
}

/** No installed provider, no requests and no empty advertising space in shipping builds today. */
val LocalBannerAdIntegration = compositionLocalOf { BannerAdIntegration.Disabled }

/** Missing/unknown entitlement fails closed, unlike a feature paywall's permissive preview default. */
val LocalBannerAdAudience = compositionLocalOf { BannerAdAudience() }
