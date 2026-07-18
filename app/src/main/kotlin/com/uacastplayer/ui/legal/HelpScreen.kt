package com.uacastplayer.ui.legal
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.RadiusCard
import com.uacastplayer.ui.theme.ScreenHPadding

/**
 * Static, "lite" Q&A-style help: what the app's main pieces are and how they relate, for a user who
 * has never seen it before. Deliberately not a first-run onboarding flow (nothing to dismiss/track
 * "seen" state for) - just a page reachable any time from Settings.
 */
@Composable
fun HelpScreen(onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    val entries = listOf(
        R.string.help_q1_title to R.string.help_q1_body,
        R.string.help_q2_title to R.string.help_q2_body,
        R.string.help_q3_title to R.string.help_q3_body,
        R.string.help_q4_title to R.string.help_q4_body,
        R.string.help_q5_title to R.string.help_q5_body,
        R.string.help_q6_title to R.string.help_q6_body,
        R.string.help_q7_title to R.string.help_q7_body,
        R.string.help_q8_title to R.string.help_q8_body,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(UaTheme.palette.void)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
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
                text = stringResource(R.string.help_title),
                style = CardTitle,
                color = UaTheme.palette.labelPrimary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = ScreenHPadding),
            verticalArrangement = Arrangement.spacedBy(GapM),
        ) {
            items(entries, key = { it.first }) { (titleRes, bodyRes) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(RadiusCard))
                        .background(UaTheme.palette.surface1)
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
        }
    }
}
