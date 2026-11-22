package com.uacastplayer.ui.nav
import com.uacastplayer.ui.theme.UaTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.ui.components.DownloadStatusBanner


/**
 * The screen title and the background-download banner.
 *
 * The banner belongs *here*, as part of the measured top bar, and not in an overlay Box stacked on
 * top of the whole scaffold, which is where it used to live. As an overlay it was simply drawn over
 * whatever was underneath: while a playlist was loading it cut the screen title in half ("Zorik
 * Player" on Home, the first section header in Settings), which is the exact moment the app most
 * needs to look like it is working rather than broken. As part of the top bar the Scaffold measures
 * it and hands the content the remaining height, so it pushes instead of covering.
 *
 * The status-bar inset is applied once, by the outer Column, and Compose *consumes* it for the
 * subtree - so [DownloadStatusBanner]'s own `statusBarsPadding()` and the title Row (which used to
 * carry the inset itself) both resolve to zero here rather than stacking into a double gap. The
 * background is applied before the inset padding, so it still fills the status bar area.
 *
 * Extracted from the `topBar` lambda so the no-overlap property can be asserted directly; composing
 * the whole of [RootScaffold] in a test would mean supplying sixty-odd parameters. Cast controls
 * intentionally live in the player overlay, where a video stream is active, rather than in this
 * global navigation chrome.
 */
private const val REMOTE_LABEL_MIN_WIDTH_DP = 360
private const val REMOTE_LABEL_MAX_FONT_SCALE = 1.5f

internal fun homeRemoteLabelVisible(home: Boolean, widthDp: Int, fontScale: Float): Boolean =
    home && widthDp >= REMOTE_LABEL_MIN_WIDTH_DP && fontScale < REMOTE_LABEL_MAX_FONT_SCALE

@Composable
internal fun RootTopBar(
    title: String,
    iconPrefetchState: IconPrefetchUiState,
    epgState: EpgUiState,
    showDownloadStatus: Boolean,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    labelRemote: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(UaTheme.palette.void)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        if (showDownloadStatus) {
            DownloadStatusBanner(iconPrefetchState = iconPrefetchState, epgState = epgState, compact = true)
        }
        if (showTitle) com.uacastplayer.ui.components.ScreenHeader(title, labelRemote = labelRemote)
    }
}
