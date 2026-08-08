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
import com.uacastplayer.premium.billing.BillingProduct
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
                text = stringResource(R.string.premium_no_store),
                style = Caption,
                color = UaTheme.palette.labelSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            for (product in section.products) {
                TierRow(product = product, onPurchase = { section.onPurchase(product) })
            }

            SecondaryButton(
                text = stringResource(R.string.premium_restore),
                onClick = section.onRestore,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
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
        Icon(
            imageVector = if (unlocked) AppIcons.Check else AppIcons.Lock,
            // Null, because the row's text already says which feature this is and the lock state is
            // carried by the text colour *and* by the icon - a screen reader announcing "lock,
            // Parental control" twice per row is noise, not information.
            contentDescription = null,
            tint = if (unlocked) UaTheme.palette.azure else UaTheme.palette.labelSecondary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(nameRes),
            style = BodyRegular,
            color = if (unlocked) UaTheme.palette.labelPrimary else UaTheme.palette.labelSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TierRow(product: BillingProduct, onPurchase: () -> Unit) {
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
        SecondaryButton(text = product.formattedPrice, onClick = onPurchase)
    }
}
