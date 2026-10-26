package com.uacastplayer.ui.ads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.uacastplayer.R
import com.uacastplayer.ads.AdPlacement
import com.uacastplayer.ads.BannerAdPolicy
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.tv.LocalTvMode

/** Zero layout footprint unless a permitted renderer actually emits loaded content. */
@Composable
fun BannerAdSlot(placement: AdPlacement, modifier: Modifier = Modifier) {
    val integration = LocalBannerAdIntegration.current
    val audience = LocalBannerAdAudience.current
    val television = LocalTvMode.current
    if (!BannerAdPolicy.permitsRequests(placement, integration.configuration, audience,
            foreground = true, television = television)) return

    // Must receive ON_PAUSE/ON_STOP too: lifecycle-gated collection could leave the last RESUMED value.
    // This collector belongs to the composed slot and is disposed with it; disabled ads do not collect.
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    if (lifecycle != Lifecycle.State.RESUMED) return
    key(integration.renderer, placement) {
        integration.renderer.Render(placement, modifier)
    }
}

/** Only wrap a loaded ad. A label is not an ad, so never draw this for pending/no-fill/error states. */
@Composable
fun BannerAdFrame(placement: AdPlacement, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Keep the cap inside the host's full-width modifier, otherwise its tight constraints defeat widthIn.
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().testTag("ad_slot_${placement.name}"),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.advertisement_label), style = Caption, color = UaTheme.palette.labelSecondary)
            content()
        }
    }
}
