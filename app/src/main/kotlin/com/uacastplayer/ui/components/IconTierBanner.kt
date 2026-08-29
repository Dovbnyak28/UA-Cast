package com.uacastplayer.ui.components
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.AppThemePreviewParameter
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(listOf(UaTheme.palette.surface1, UaTheme.palette.surface2)),
                shape = shape,
            )
            .border(1.dp, UaTheme.palette.hairline, shape)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
        TextButton(
            onClick = onEnableIcons,
            colors = ButtonDefaults.textButtonColors(contentColor = UaTheme.palette.azure),
        ) {
            Text(stringResource(R.string.channels_icon_tier_banner_action))
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = AppIcons.Close,
                contentDescription = stringResource(R.string.download_banner_dismiss),
                tint = UaTheme.palette.labelSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0B12L)
@Composable
private fun IconTierBannerPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        IconTierBanner(onEnableIcons = {}, onDismiss = {})
    }
}
