package com.uacastplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.ui.UiTestTags
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyRegular
import com.uacastplayer.ui.theme.TabLabel
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.update.GitHubRelease
import com.uacastplayer.update.ReleaseApk
import com.uacastplayer.update.UpdateInstallState

/**
 * Tells the user a newer release exists, in the same place and shape as [DownloadStatusBanner] -
 * part of the layout rather than an overlay, so it pushes the screen down instead of covering the
 * title.
 *
 * It is the whole of the "notify" half of the update feature. There is no system notification and
 * no background job: the check runs when the app is opened (see
 * [com.uacastplayer.app.UpdateController]), so the moment there is something to say the user is
 * already looking at the screen.
 *
 * **The button installs when there is an APK to install.** It used to hand the release page to a
 * browser unconditionally, which meant the one action the banner offered led to a GitHub page whose
 * most prominent downloads are the source archives - a zip and a tar.gz of the code, neither of
 * which is installable, and the first of which is exactly what a phone browser offers to save.
 * Opening the page stays the offer for a release this build will not take an APK from (see
 * [com.uacastplayer.update.ReleaseApkPolicy], which refuses to guess between several), because then
 * a human reading the page is the only way through.
 *
 * @param release the newer release, or null when there is none to announce - including when the
 *   user has closed this banner for that version.
 * @param installState so the banner reports its own download rather than appearing to do nothing -
 *   the same state the Settings row renders, from the same controller.
 */
@Composable
fun UpdateBanner(
    release: GitHubRelease?,
    installState: UpdateInstallState,
    onInstall: (ReleaseApk) -> Unit,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = release != null,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        modifier = modifier,
    ) {
        // Held across the exit animation: `release` is already null by the time the banner shrinks
        // away, and reading it directly would blank the text out for the length of that animation.
        val shown = rememberPreviousNonNull(release)
        val shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.UPDATE_BANNER)
                // A no-op inside the top bar, which consumes the status-bar inset for its whole
                // subtree - kept so the banner stays correct anywhere else it is hosted.
                .statusBarsPadding()
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(UaTheme.palette.surface1, UaTheme.palette.surface2),
                    ),
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
                Text(
                    text = stringResource(R.string.update_banner_title),
                    color = UaTheme.palette.labelPrimary,
                    style = TabLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = stringResource(R.string.download_banner_dismiss),
                        tint = UaTheme.palette.labelSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Text(
                text = stringResource(R.string.update_banner_message, shown?.tagName.orEmpty()),
                color = UaTheme.palette.labelSecondary,
                style = BodyRegular,
                // Two lines, not one: the message carries a version number at its end, and at a
                // large font scale on a narrow screen an ellipsis would eat exactly the part the
                // banner exists to communicate.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            UpdateBannerAction(
                release = shown,
                installState = installState,
                onInstall = onInstall,
                onOpen = onOpen,
            )
        }
    }
}

/**
 * The one button, and what it says while a download is running.
 *
 * Deliberately fewer states than the Settings row shows. This is a banner in the top bar, not the
 * screen a user goes to about updates: it reports progress, and every ending - installed, refused,
 * failed - sends them to Settings rather than growing a second copy of that vocabulary here.
 */
@Composable
private fun UpdateBannerAction(
    release: GitHubRelease?,
    installState: UpdateInstallState,
    onInstall: (ReleaseApk) -> Unit,
    onOpen: (String) -> Unit,
) {
    val apk = release?.apk
    when {
        installState is UpdateInstallState.Downloading -> Text(
            text = installState.percent()
                ?.let { stringResource(R.string.settings_update_downloading_percent, it) }
                ?: stringResource(R.string.settings_update_downloading),
            color = UaTheme.palette.labelSecondary,
            style = BodyRegular,
        )

        installState is UpdateInstallState.Launching -> Text(
            text = stringResource(R.string.settings_update_launching),
            color = UaTheme.palette.labelSecondary,
            style = BodyRegular,
        )

        apk != null -> SecondaryButton(
            text = stringResource(R.string.settings_update_install_button),
            onClick = { onInstall(apk) },
            modifier = Modifier.fillMaxWidth(),
        )

        else -> SecondaryButton(
            text = stringResource(R.string.update_banner_action),
            onClick = { release?.releaseUrl?.let(onOpen) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Whole percent, or null when the release declared no size - an indeterminate label beats a
 * percentage of nothing. */
private fun UpdateInstallState.Downloading.percent(): Int? =
    if (totalBytes > 0) (bytesSoFar * PERCENT / totalBytes).toInt().coerceIn(0, PERCENT.toInt()) else null

private const val PERCENT = 100L

/** Keeps the last non-null value so content stays readable while the container animates out. */
@Composable
private fun rememberPreviousNonNull(value: GitHubRelease?): GitHubRelease? {
    val holder = remember { mutableStateOf(value) }
    if (value != null) holder.value = value
    return holder.value
}
