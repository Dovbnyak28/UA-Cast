package com.uacastplayer.ui.legal
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.CardPadding
import com.uacastplayer.ui.theme.CardTitle
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.LargeTitle
import com.uacastplayer.ui.theme.RadiusCard
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.raisedSurface

/**
 * Renders the Terms of Use text. Used two ways:
 *  - as a mandatory first-launch gate ([onAccept]/[onDecline] both non-null, no back navigation -
 *    see [com.uacastplayer.MainActivity]), or
 *  - as an ordinary reachable-any-time screen from Settings ([onBackClick] only).
 *
 * The full legal text also lives standalone at `legal/terms-of-use.html` (for hosting on a website
 * and linking from the Play Store listing); this composable is the in-app copy users actually agree
 * to, so the two need to be kept in sync by hand when either changes.
 */
@Composable
fun TermsScreen(
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    onAccept: (() -> Unit)? = null,
    onDecline: (() -> Unit)? = null,
) {
    val sections = listOf(
        R.string.terms_s1_title to R.string.terms_s1_body,
        R.string.terms_s2_title to R.string.terms_s2_body,
        R.string.terms_s3_title to R.string.terms_s3_body,
        R.string.terms_s4_title to R.string.terms_s4_body,
        R.string.terms_s5_title to R.string.terms_s5_body,
        R.string.terms_s6_title to R.string.terms_s6_body,
        R.string.terms_s7_title to R.string.terms_s7_body,
        R.string.terms_s8_title to R.string.terms_s8_body,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(UaTheme.palette.void)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        if (onBackClick != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        AppIcons.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = UaTheme.palette.labelPrimary,
                    )
                }
                Text(
                    text = stringResource(R.string.terms_title),
                    style = CardTitle,
                    color = UaTheme.palette.labelPrimary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        } else {
            // First-launch gate: no back target to navigate to, so no back button - accepting or
            // declining (below) are the only two ways forward.
            Text(
                text = stringResource(R.string.terms_title),
                style = LargeTitle,
                color = UaTheme.palette.labelPrimary,
                modifier = Modifier.padding(top = 32.dp, start = ScreenHPadding, end = ScreenHPadding),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = ScreenHPadding),
            verticalArrangement = Arrangement.spacedBy(GapM),
        ) {
            item(key = "terms-intro") {
                Text(
                    text = stringResource(R.string.terms_intro),
                    style = BodyText,
                    color = UaTheme.palette.labelSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(sections, key = { it.first }) { (titleRes, bodyRes) ->
                Column(
                    // Inside a LazyColumn item - shadow = false, see docs/DESIGN_SYSTEM.md "§D Depth".
                    modifier = Modifier
                        .fillMaxWidth()
                        .raisedSurface(
                            RoundedCornerShape(RadiusCard),
                            UaTheme.palette.surface1,
                            edgeColor = UaTheme.palette.hairline,
                            shadow = false,
                        )
                        .padding(CardPadding),
                ) {
                    Text(text = stringResource(titleRes), style = CardTitle, color = UaTheme.palette.labelPrimary)
                    Text(
                        text = stringResource(bodyRes),
                        style = BodyText,
                        color = UaTheme.palette.labelSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            item(key = "terms-bottom-spacer") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (onAccept != null && onDecline != null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.terms_gate_footer),
                    style = Caption,
                    color = UaTheme.palette.labelSecondary,
                )
                Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.terms_accept_button))
                }
                OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.terms_decline_button))
                }
            }
        }
    }
}
