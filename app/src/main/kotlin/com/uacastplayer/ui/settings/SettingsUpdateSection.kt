package com.uacastplayer.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.theme.BodyRegular
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.update.ReleaseApk
import com.uacastplayer.update.UpdateCheckOutcome
import com.uacastplayer.update.UpdateInstallState
import com.uacastplayer.update.UpdateSectionState
import androidx.compose.ui.res.stringResource

/** Update check and install UI, isolated from the general settings composition. */
@Composable
internal fun UpdateCheckRow(section: UpdateSectionState) {
    DisposableEffect(Unit) { onDispose { section.onOutcomeShown() } }

    Text(
        text = stringResource(R.string.settings_update_hint),
        style = Caption,
        color = UaTheme.palette.labelSecondary,
    )

    if (section.state.isChecking) {
        CheckingRow()
    } else {
        SecondaryButton(
            text = stringResource(R.string.settings_update_check_button),
            onClick = section.onCheckNow,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }

    val outcome = section.state.lastOutcome
    if (outcome != null && !section.state.isChecking) {
        OutcomeText(outcome, section.state.availableRelease?.tagName.orEmpty())
    }

    val release = section.state.availableRelease
    if (release != null && !section.state.isChecking) {
        release.apk?.let { apk -> UpdateInstallRow(section, apk) }
        SecondaryButton(
            text = stringResource(R.string.update_banner_action),
            onClick = { section.onOpenRelease(release.releaseUrl) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun CheckingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = UaTheme.palette.azure,
            strokeWidth = 2.dp,
        )
        Text(
            text = stringResource(R.string.settings_update_checking),
            style = BodyRegular,
            color = UaTheme.palette.labelSecondary,
        )
    }
}

@Composable
private fun OutcomeText(outcome: UpdateCheckOutcome, version: String) {
    Text(
        text = when (outcome) {
            UpdateCheckOutcome.UP_TO_DATE -> stringResource(R.string.settings_update_up_to_date)
            UpdateCheckOutcome.UPDATE_AVAILABLE -> stringResource(R.string.settings_update_available, version)
            UpdateCheckOutcome.FAILED -> stringResource(R.string.settings_update_failed)
        },
        style = BodyRegular,
        color = if (outcome == UpdateCheckOutcome.FAILED) {
            UaTheme.palette.labelSecondary
        } else {
            UaTheme.palette.labelPrimary
        },
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun UpdateInstallRow(section: UpdateSectionState, apk: ReleaseApk) {
    when (val install = section.installState) {
        is UpdateInstallState.Downloading -> DownloadingRow(install)
        UpdateInstallState.Launching -> UpdateInstallNote(R.string.settings_update_launching)
        UpdateInstallState.NeedsPermission -> {
            UpdateInstallNote(R.string.settings_update_needs_permission)
            SecondaryButton(
                text = stringResource(R.string.settings_update_grant_permission_button),
                onClick = section.onGrantInstallPermission,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        UpdateInstallState.Untrusted -> UpdateInstallNote(R.string.settings_update_untrusted)
        UpdateInstallState.Corrupt -> RetryableInstallFailure(
            R.string.settings_update_corrupt,
            section,
            apk,
        )
        UpdateInstallState.Failed -> RetryableInstallFailure(
            R.string.settings_update_install_failed,
            section,
            apk,
        )
        UpdateInstallState.Idle -> InstallButton(section, apk)
    }
}

@Composable
private fun DownloadingRow(install: UpdateInstallState.Downloading) {
    val percent = if (install.totalBytes > 0) {
        (install.bytesSoFar * PERCENT / install.totalBytes).toInt().coerceIn(0, PERCENT.toInt())
    } else {
        null
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = UaTheme.palette.azure,
            strokeWidth = 2.dp,
        )
        Text(
            text = percent?.let { stringResource(R.string.settings_update_downloading_percent, it) }
                ?: stringResource(R.string.settings_update_downloading),
            style = BodyRegular,
            color = UaTheme.palette.labelSecondary,
        )
    }
}

@Composable
private fun RetryableInstallFailure(
    @StringRes note: Int,
    section: UpdateSectionState,
    apk: ReleaseApk,
) {
    UpdateInstallNote(note)
    InstallButton(section, apk)
}

@Composable
private fun InstallButton(section: UpdateSectionState, apk: ReleaseApk) {
    SecondaryButton(
        text = stringResource(R.string.settings_update_install_button),
        onClick = { section.onDownloadAndInstall(apk) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun UpdateInstallNote(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = BodyRegular,
        color = UaTheme.palette.labelSecondary,
        modifier = Modifier.padding(top = 10.dp),
    )
}

private const val PERCENT = 100L
