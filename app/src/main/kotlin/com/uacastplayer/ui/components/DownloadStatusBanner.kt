package com.uacastplayer.ui.components
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.TabLabel
import com.uacastplayer.ui.theme.UaCastTheme

private const val MIN_PERCENT = 0
private const val MAX_PERCENT = 100

/**
 * A dismissible, non-modal strip explaining why the app might look sparse or feel slow right
 * after a playlist is first added: channel logos and the TV guide are still downloading in the
 * background. Deliberately not a dialog - the user can keep navigating while this is visible.
 *
 * Dismissing only hides it for the current download; it reappears the next time icon prefetch or
 * an EPG load starts (tracked via [IconPrefetchUiState.isRunning]/[EpgUiState.isLoading] flipping
 * false-\>true again).
 */
@Composable
fun DownloadStatusBanner(
    iconPrefetchState: IconPrefetchUiState,
    epgState: EpgUiState,
    modifier: Modifier = Modifier,
) {
    val isActive = iconPrefetchState.isRunning || epgState.isLoading
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(iconPrefetchState.isRunning, epgState.isLoading) {
        if (isActive) {
            dismissed = false
        }
    }

    AnimatedVisibility(
        visible = isActive && !dismissed,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(colors = listOf(UaTheme.palette.surface1, UaTheme.palette.surface2)),
                    shape = shape,
                )
                .border(1.dp, UaTheme.palette.hairline, shape)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Text(
                    text = stringResource(R.string.download_banner_title),
                    color = UaTheme.palette.labelPrimary,
                    style = TabLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { dismissed = true }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.download_banner_dismiss),
                        tint = UaTheme.palette.labelSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (iconPrefetchState.isRunning) {
                BannerProgressLine(
                    label = stringResource(R.string.download_banner_icons, iconPrefetchState.percent()),
                    fraction = iconPrefetchState.percent() / 100f,
                )
            }
            if (epgState.isLoading) {
                BannerProgressLine(
                    label = stringResource(R.string.download_banner_epg_processing),
                    fraction = null,
                )
            }
        }
    }
}

/** A label line with either a determinate ([fraction] non-null) or indeterminate progress bar. */
@Composable
private fun BannerProgressLine(label: String, fraction: Float?) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        androidx.compose.material3.Text(
            text = label,
            color = UaTheme.palette.labelSecondary,
            style = Caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val barModifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(999.dp))
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = barModifier,
                color = UaTheme.palette.azure,
                trackColor = UaTheme.palette.hairline,
            )
        } else {
            LinearProgressIndicator(
                modifier = barModifier,
                color = UaTheme.palette.azure,
                trackColor = UaTheme.palette.hairline,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0B12L)
@Composable
private fun DownloadStatusBannerPreview() {
    UaCastTheme(AppTheme.AZURE) {
        DownloadStatusBanner(
            iconPrefetchState = IconPrefetchUiState(isRunning = true, completed = 42, total = 120),
            epgState = EpgUiState(isLoading = true),
        )
    }
}

private fun IconPrefetchUiState.percent(): Int =
    if (total > 0) {
        ((completed * MAX_PERCENT.toLong()) / total).toInt().coerceIn(MIN_PERCENT, MAX_PERCENT)
    } else {
        MIN_PERCENT
    }
