package com.uacastplayer.ui.premium

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.premium.LicenseTier
import com.uacastplayer.premium.PremiumSectionState
import com.uacastplayer.ui.UiTestTags
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.theme.BodyRegular
import com.uacastplayer.ui.theme.UaTheme

/** A trial with more than this many days left says nothing: a countdown that starts on day one is
 * nagging, not information. */
private const val TRIAL_NOTICE_DAYS = 3

/**
 * The only part of the premium UI that can appear without being asked for, which is why it is the
 * one with the strictest rules about when it may.
 *
 * It shows in exactly two situations, both of which are news rather than salesmanship:
 *
 * - the trial is about to run out (within [TRIAL_NOTICE_DAYS]), so the change is not a surprise;
 * - it already has, and paid features are locked - saying so beats letting the user discover it by
 *   tapping something that used to work.
 *
 * It never shows to someone on the free tier who never had premium: they are not losing anything,
 * and an app that asks for money on its home screen from day one is the app people uninstall.
 */
@Composable
fun UpgradeBanner(
    section: PremiumSectionState,
    nowMillis: Long,
    onSeePremium: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = section.daysRemaining(nowMillis)
    val lapsed = section.entitlements.hasLapsed
    val endingTrial = section.entitlements.license.tier == LicenseTier.TRIAL &&
        days != null && days <= TRIAL_NOTICE_DAYS

    AnimatedVisibility(
        visible = lapsed || endingTrial,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(16.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.UPGRADE_BANNER)
                .clip(shape)
                .background(UaTheme.palette.surface1, shape)
                .border(1.dp, UaTheme.palette.hairline, shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (lapsed) {
                    stringResource(R.string.premium_upgrade_lapsed)
                } else {
                    stringResource(R.string.premium_upgrade_trial, days ?: 0)
                },
                style = BodyRegular,
                color = UaTheme.palette.labelPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            SecondaryButton(
                text = stringResource(R.string.premium_open),
                onClick = onSeePremium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
