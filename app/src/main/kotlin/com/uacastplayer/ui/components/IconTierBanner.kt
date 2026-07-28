package com.uacastplayer.ui.components
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.TabLabel
import com.uacastplayer.ui.theme.UaCastTheme

/**
 * One-time, dismissible strip on the Channels tab explaining why logos aren't showing when
 * [com.uacastplayer.performance.DeviceTierDefaults] silently switched to PLACEHOLDERS on a
 * low-end device - see [com.uacastplayer.settings.IconPlaceholdersBannerPolicy] for exactly when
 * this should be shown. Same visual language as [DownloadStatusBanner] (gradient card, hairline
 * border, dismiss X) but inline content rather than an overlay, so no status-bar padding here.
 */
@Composable
fun IconTierBanner(onEnableIcons: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(listOf(UaTheme.palette.surface1, UaTheme.palette.surface2)),
                shape = shape,
            )
            .border(1.dp, UaTheme.palette.hairline, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.channels_icon_tier_banner_title),
                color = UaTheme.palette.labelPrimary,
                style = TabLabel,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.download_banner_dismiss),
                    tint = UaTheme.palette.labelSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        TextButton(
            onClick = onEnableIcons,
            colors = ButtonDefaults.textButtonColors(contentColor = UaTheme.palette.azure),
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.channels_icon_tier_banner_action))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0B12L)
@Composable
private fun IconTierBannerPreview() {
    UaCastTheme(AppTheme.AZURE) {
        IconTierBanner(onEnableIcons = {}, onDismiss = {})
    }
}
