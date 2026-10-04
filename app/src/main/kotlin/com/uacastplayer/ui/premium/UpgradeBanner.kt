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
import com.uacastplayer.premium.PremiumSectionState
import com.uacastplayer.ui.UiTestTags
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.theme.BodyRegular
import com.uacastplayer.ui.theme.UaTheme

/** Explains expired legacy paid access. Fresh Lite users and retired trials have no countdown
 * or automatic sales banner. One-time Premium never expires. */
@Composable
fun UpgradeBanner(
    section: PremiumSectionState,
    onSeePremium: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lapsed = section.entitlements.hasLapsed

    AnimatedVisibility(
        visible = lapsed,
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
                text = stringResource(R.string.premium_upgrade_lapsed),
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
