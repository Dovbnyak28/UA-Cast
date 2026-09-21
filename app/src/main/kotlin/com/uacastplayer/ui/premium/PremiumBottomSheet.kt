package com.uacastplayer.ui.premium

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.premium.PremiumSectionState
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.GapL
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.UaTheme

/**
 * The short path to premium: what it costs and how to restore it, without leaving the screen the
 * user was on.
 *
 * Reached from [UnlockDialog] - somebody who tapped a locked control and then asked to see premium
 * has already read what it is, so this drops the explanation and keeps the part they came for. It
 * is the same [PremiumContent] as the Settings section with the intro suppressed, rather than a
 * second implementation that would drift out of step with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumBottomSheet(
    section: PremiumSectionState,
    nowMillis: Long,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = UaTheme.palette.surface1,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenHPadding)
                .padding(bottom = GapL),
        ) {
            Text(
                text = stringResource(R.string.premium_open),
                style = Title,
                color = UaTheme.palette.labelPrimary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            PremiumContent(section = section, nowMillis = nowMillis, showIntro = false)
        }
    }
}
