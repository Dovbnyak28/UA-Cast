package app.zorik.player.ui.premium

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.zorik.player.R
import app.zorik.player.premium.Feature
import app.zorik.player.ui.theme.BodyRegular
import app.zorik.player.ui.theme.Title
import app.zorik.player.ui.theme.UaTheme
import app.zorik.player.ui.tv.TvDialogInputRegistration
import app.zorik.player.ui.tv.tvFocus

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
            TvDialogInputRegistration()
            Text(
                text = stringResource(R.string.premium_unlock_message),
                style = BodyRegular,
                color = UaTheme.palette.labelSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onSeePremium, modifier = Modifier.tvFocus()) {
                Text(stringResource(R.string.premium_unlock_cta), color = UaTheme.palette.azure)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.tvFocus()) {
                Text(stringResource(R.string.premium_unlock_later), color = UaTheme.palette.labelSecondary)
            }
        },
    )
}
