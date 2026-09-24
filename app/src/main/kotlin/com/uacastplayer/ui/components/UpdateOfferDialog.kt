package com.uacastplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyRegular
import com.uacastplayer.ui.theme.TabLabel
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.update.GitHubRelease

/** An opt-in offer with a delayed reminder. Checking never downloads an APK on its own. */
@Composable
fun UpdateOfferDialog(
    release: GitHubRelease,
    onInstall: () -> Unit,
    onLater: () -> Unit,
    onOpenRelease: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        icon = {
            Icon(
                AppIcons.Refresh,
                contentDescription = null,
                tint = UaTheme.palette.azure,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                stringResource(R.string.update_offer_title),
                color = UaTheme.palette.labelPrimary,
            )
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.update_offer_message, release.tagName),
                    style = BodyRegular,
                    color = UaTheme.palette.labelSecondary,
                )
                Text(
                    stringResource(R.string.update_offer_whats_new),
                    style = TabLabel,
                    color = UaTheme.palette.labelPrimary,
                )
                Text(
                    release.releaseNotes ?: stringResource(R.string.update_offer_no_notes),
                    style = BodyRegular,
                    color = UaTheme.palette.labelSecondary,
                )
                TextButton(onClick = { onOpenRelease(release.releaseUrl) }) {
                    Text(stringResource(R.string.update_offer_full_notes))
                }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                PrimaryButton(
                    text = stringResource(R.string.update_offer_install),
                    onClick = onInstall,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = stringResource(R.string.update_offer_later),
                    onClick = onLater,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        containerColor = UaTheme.palette.surface1,
    )
}
