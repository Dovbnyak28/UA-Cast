package com.uacastplayer.ui.premium

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.premium.Feature
import com.uacastplayer.premium.PremiumSectionState
import androidx.annotation.StringRes
import com.uacastplayer.premium.PremiumAvailability
import com.uacastplayer.premium.StoreAbsence
import com.uacastplayer.premium.billing.BillingProduct
import com.uacastplayer.premium.billing.PurchaseResult
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyRegular
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.UaTheme

/**
 * What premium is, what it costs, and how to get back something already paid for.
 *
 * Written as a column rather than a screen with its own scaffold so the same content can be hosted
 * by the Settings section and by [PremiumBottomSheet] without being written twice.
 *
 * The feature list shows every sold feature with its current lock state, including the ones already
 * unlocked. A list that only showed what is missing would read as a demand; showing both is what
 * makes "you have this until the trial ends" legible.
 */
@Composable
fun PremiumContent(
    section: PremiumSectionState,
    nowMillis: Long,
    modifier: Modifier = Modifier,
    showIntro: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = statusLine(section, nowMillis),
            style = BodyRegular,
            color = UaTheme.palette.labelPrimary,
        )

        if (showIntro) {
            Text(
                text = stringResource(R.string.premium_screen_intro),
                style = Caption,
                color = UaTheme.palette.labelSecondary,
            )
        }

        for (feature in PremiumLabels.SOLD) {
            FeatureRow(feature = feature, unlocked = feature in section.entitlements.unlocked)
        }

        if (section.products.isEmpty()) {
            // The honest state until this app is published: there is no store to buy from. Saying
            // so beats an empty list under a heading that promises prices.
            //
            // Restore is hidden here rather than shown and disabled, and that is the point: with no
            // store to ask, tapping it could only ever do nothing, and a button that does nothing is
            // the defect - not the missing message explaining why it did nothing.
            Text(
                text = stringResource(noStoreMessage(section)),
                style = Caption,
                color = UaTheme.palette.labelSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            for (product in section.products) {
                TierRow(
                    product = product,
                    onPurchase = { section.onPurchase(product) },
                    enabled = !section.isPurchasing,
                )
            }

            SecondaryButton(
                text = stringResource(R.string.premium_restore),
                onClick = section.onRestore,
                enabled = !section.isPurchasing,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )

            outcomeLine(section.lastOutcome)?.let { message ->
                Text(
                    text = message,
                    style = Caption,
                    color = UaTheme.palette.labelSecondary,
                )
            }
        }
    }
}

/**
 * What to say about the last attempt, or null when there is nothing to say.
 *
 * The three cases that reach here need three different answers, and the common failure of a paid
 * flow is answering all of them with one. "Nothing to restore" is not a problem and must not send
 * the user off to check their connection; "the store cannot be reached" is a problem they can do
 * something about; "already owned" means the app should already be unlocked and is the one worth
 * repeating a restore for.
 */
@Composable
private fun outcomeLine(outcome: PurchaseResult?): String? = when (outcome) {
    null, PurchaseResult.Cancelled, is PurchaseResult.Success -> null
    PurchaseResult.NothingToRestore -> stringResource(R.string.premium_restore_nothing)
    PurchaseResult.AlreadyOwned -> stringResource(R.string.premium_already_owned)
    PurchaseResult.Unavailable -> stringResource(R.string.premium_store_unreachable)
    // Play's own debug message can name the account or the product, so it is not shown - it is
    // already in the log for a diagnostics report, and the user needs the next step, not the cause.
    is PurchaseResult.Failed -> stringResource(R.string.premium_purchase_failed)
}

/**
 * The reason there is nothing to buy, in the reader's terms.
 *
 * One sentence used to cover all of them, and it said the app was unpublished - which becomes a
 * falsehood the day it is published, told to the people least able to argue with it.
 */
@StringRes
private fun noStoreMessage(section: PremiumSectionState): Int =
    when (StoreAbsence.of(PremiumAvailability.STORE_IS_LIVE, section.connection, hasProducts = false)) {
        StoreAbsence.DEVICE_HAS_NO_STORE -> R.string.premium_no_play_on_device
        StoreAbsence.STORE_OFFERS_NOTHING -> R.string.premium_store_offers_nothing
        else -> R.string.premium_no_store
    }

@Composable
private fun statusLine(section: PremiumSectionState, nowMillis: Long): String {
    val days = section.daysRemaining(nowMillis)
    return when {
        section.entitlements.hasLapsed -> stringResource(R.string.premium_status_lapsed)
        section.entitlements.license.tier == com.uacastplayer.premium.LicenseTier.TRIAL && days != null ->
            stringResource(R.string.premium_status_trial, days)
        section.entitlements.license.tier == com.uacastplayer.premium.LicenseTier.FREE ->
            stringResource(R.string.premium_status_free)
        else -> stringResource(R.string.premium_status_active)
    }
}

@Composable
private fun FeatureRow(feature: Feature, unlocked: Boolean) {
    val nameRes = PremiumLabels.nameRes(feature) ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (unlocked) {
            Icon(
                imageVector = AppIcons.Check,
                // Null: the row's text already names the feature, and "unlocked" is carried by the
                // text colour too. A screen reader saying "tick, Parental control" on every row is
                // noise, not information.
                contentDescription = null,
                tint = UaTheme.palette.azure,
                modifier = Modifier.size(16.dp),
            )
        } else {
            // The same badge used to mark a locked control anywhere else in the app, rather than a
            // second lock icon drawn inline here - one lock, one look. This one *does* carry a
            // description, because "locked" is the thing a non-sighted user cannot otherwise tell.
            PremiumBadge()
        }
        Text(
            text = stringResource(nameRes),
            style = BodyRegular,
            color = if (unlocked) UaTheme.palette.labelPrimary else UaTheme.palette.labelSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TierRow(product: BillingProduct, onPurchase: () -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = product.title,
            style = BodyRegular,
            color = UaTheme.palette.labelPrimary,
            modifier = Modifier.weight(1f),
        )
        // The price comes from the store, never from a string resource: Play returns it in the
        // user's own currency with regional pricing and any running promotion already applied.
        SecondaryButton(text = product.formattedPrice, onClick = onPurchase, enabled = enabled)
    }
}
