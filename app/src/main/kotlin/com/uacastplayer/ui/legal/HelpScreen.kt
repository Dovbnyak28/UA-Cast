package com.uacastplayer.ui.legal
import com.uacastplayer.ui.diagnostics.DiagnosticsPreviewDialog
import com.uacastplayer.ui.diagnostics.sendDiagnostics
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
import com.uacastplayer.ui.theme.raisedSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Static, "lite" Q&A-style help: what the app's main pieces are and how they relate, for a user who
 * has never seen it before. Deliberately not a first-run onboarding flow (nothing to dismiss/track
 * "seen" state for) - just a page reachable any time from Settings.
 */
@Composable
fun HelpScreen(
    onBackClick: () -> Unit,
    onBuildDiagnosticsReport: () -> String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var diagnosticsReport by remember { mutableStateOf<String?>(null) }
    // Hoisted out of the dialog block below, because building the report needs it too - see the
    // button's onClick. Bound to this composition, so leaving Help mid-build cancels the work.
    val diagnosticsScope = rememberCoroutineScope()

    // "How things work" first, in the order a new user meets them, then the three
    // troubleshooting entries, then where the data lives. The DLNA entry sits directly under the
    // Cast one because the two buttons sit side by side in the player and the question a user
    // actually has is "which of these two is mine?".
    val entries = listOf(
        R.string.help_q1_title to R.string.help_q1_body,
        R.string.help_q2_title to R.string.help_q2_body,
        R.string.help_q3_title to R.string.help_q3_body,
        R.string.help_dlna_title to R.string.help_dlna_body,
        R.string.help_q4_title to R.string.help_q4_body,
        R.string.help_q5_title to R.string.help_q5_body,
        R.string.help_q6_title to R.string.help_q6_body,
        R.string.help_q7_title to R.string.help_q7_body,
        R.string.help_playlist_errors_title to R.string.help_playlist_errors_body,
        R.string.help_channel_unavailable_title to R.string.help_channel_unavailable_body,
        R.string.help_cast_errors_title to R.string.help_cast_errors_body,
        R.string.help_q8_title to R.string.help_q8_body,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(UaTheme.palette.void)
            // safeDrawing, not statusBars alone - same reason as TermsScreen: a full-bleed screen
            // with nothing above it to pad the bottom, so under edge-to-edge its last list row and
            // the diagnostics button ran under the navigation bar.
            .safeDrawingPadding(),
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
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = ScreenHPadding),
            verticalArrangement = Arrangement.spacedBy(GapM),
        ) {
            items(entries, key = { it.first }) { (titleRes, bodyRes) ->
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
        }

        OutlinedButton(
            // Off the main thread: the report reads the crash file and the filesystem's free space,
            // and walks the whole guide to count its programmes. See SettingsScreen's copy of this.
            onClick = {
                diagnosticsScope.launch {
                    diagnosticsReport = withContext(Dispatchers.IO) { onBuildDiagnosticsReport() }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = GapM),
        ) {
            Text(stringResource(R.string.help_send_diagnostics_button))
        }
    }

    diagnosticsReport?.let { report ->
        // Read through stringResource, not context.getString: only the former is tied to the
        // composition, so a configuration change (the in-app language switch, most obviously)
        // re-reads it. Pulling it off LocalContext hands back whatever locale that Context was
        // created with, which is what Compose's own lint flags here.
        val chooserTitle = stringResource(R.string.diagnostics_share_chooser_title)
        DiagnosticsPreviewDialog(
            report = report,
            onCancel = { diagnosticsReport = null },
            onSend = {
                diagnosticsReport = null
                diagnosticsScope.launch { sendDiagnostics(context, report, chooserTitle) }
            },
        )
    }
}

