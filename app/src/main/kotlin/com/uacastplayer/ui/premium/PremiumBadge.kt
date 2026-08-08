package com.uacastplayer.ui.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.UaTheme

/**
 * A small lock next to something the current license does not include.
 *
 * It marks, it does not block: the control it sits beside stays visible and tappable, and tapping
 * opens [UnlockDialog]. Hiding a locked feature outright is the tempting alternative and the wrong
 * one - a control that vanishes reads as a broken app, while a lock with an explanation behind it
 * reads as an offer.
 *
 * The lock carries the description rather than being decorative, so the announcement a screen
 * reader makes for the row it sits in ends with "Premium feature" instead of silently matching the
 * unlocked row above it.
 */
@Composable
fun PremiumBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(UaTheme.palette.azure.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AppIcons.Lock,
            contentDescription = stringResource(R.string.premium_locked_badge),
            tint = UaTheme.palette.azure,
            modifier = Modifier.size(12.dp),
        )
    }
}
