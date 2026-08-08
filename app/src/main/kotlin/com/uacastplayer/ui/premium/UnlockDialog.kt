package com.uacastplayer.ui.premium

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.uacastplayer.R
import com.uacastplayer.premium.Feature
import com.uacastplayer.ui.theme.BodyRegular
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.UaTheme

/**
 * Shown when a locked control is tapped - and only then.
 *
 * It never appears on its own. A paywall that interrupts is the one users learn to resent; this one
 * is an answer to a tap the user made, which is why it names the feature they just reached for
 * rather than opening with a price.
 *
 * @param feature what was tapped. A feature with no user-facing name (one of the reserved entries
 *   in [Feature] that nothing implements yet) renders nothing at all rather than an empty title.
 */
@Composable
fun UnlockDialog(
    feature: Feature,
    onSeePremium: () -> Unit,
    onDismiss: () -> Unit,
) {
    val nameRes = PremiumLabels.nameRes(feature) ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = UaTheme.palette.surface1,
        title = {
            Text(
                text = stringResource(R.string.premium_unlock_title, stringResource(nameRes)),
                style = Title,
                color = UaTheme.palette.labelPrimary,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.premium_unlock_message),
                style = BodyRegular,
                color = UaTheme.palette.labelSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onSeePremium) {
                Text(stringResource(R.string.premium_unlock_cta), color = UaTheme.palette.azure)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.premium_unlock_later), color = UaTheme.palette.labelSecondary)
            }
        },
    )
}
