package com.uacastplayer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uacastplayer.BuildConfig
import com.uacastplayer.R
import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.backup.BackupImportSummary
import com.uacastplayer.backup.BackupExportResult
import com.uacastplayer.diagnostics.RemuxEffectivenessCounts
import com.uacastplayer.diagnostics.RemuxEffectivenessPolicy
import com.uacastplayer.guidedtour.GuidedTourSectionState
import com.uacastplayer.performance.DeviceTier
import com.uacastplayer.premium.Feature
import com.uacastplayer.premium.PremiumAvailability
import com.uacastplayer.premium.PremiumSectionState
import com.uacastplayer.settings.SettingsUiState
import com.uacastplayer.ui.UiTestTags
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.diagnostics.DiagnosticsPreviewDialog
import com.uacastplayer.ui.diagnostics.sendDiagnostics
import com.uacastplayer.ui.premium.LocalFeatureGate
import com.uacastplayer.ui.premium.PremiumContent
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyRegular
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.CaptionSemibold
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.UaTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DataSettingsSection(
    onImportBackup: () -> Unit,
    onShowExportWarning: () -> Unit,
) {
    val gate = LocalFeatureGate.current
    SettingsSection(
        title = stringResource(R.string.settings_section_data),
        icon = AppIcons.Upload,
        locked = gate.isLocked(Feature.BACKUP),
    ) {
        Text(
            text = stringResource(R.string.settings_data_hint),
            style = Caption,
            color = UaTheme.palette.labelSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryButton(
                text = stringResource(R.string.settings_data_export),
                onClick = gate.guard(Feature.BACKUP, onShowExportWarning),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            SecondaryButton(
                text = stringResource(R.string.settings_data_import),
                onClick = gate.guard(Feature.BACKUP, onImportBackup),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
internal fun BackupExportWarningDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = UaTheme.palette.surface2,
        titleContentColor = UaTheme.palette.labelPrimary,
        textContentColor = UaTheme.palette.labelSecondary,
        title = { Text(stringResource(R.string.settings_data_export_warning_title)) },
        text = { Text(stringResource(R.string.settings_data_export_warning_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_data_export_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
internal fun BackupImportSummaryBanner(
    summary: BackupImportSummary,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackupNoticeBanner(
        text = stringResource(
            R.string.settings_data_import_summary,
            summary.importedSourceCount,
            summary.importedFavoriteCount,
        ),
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

@Composable
internal fun BackupExportResultBanner(
    result: BackupExportResult,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = when (result) {
        BackupExportResult.SUCCESS -> R.string.settings_data_export_success
        BackupExportResult.FAILURE -> R.string.settings_data_export_failure
    }
    BackupNoticeBanner(stringResource(message), onDismiss, modifier)
}

@Composable
private fun BackupNoticeBanner(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(RadiusItem)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(listOf(UaTheme.palette.surface1, UaTheme.palette.surface2)),
                shape = shape,
            )
            .border(1.dp, UaTheme.palette.hairline, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = BodyRegular,
            color = UaTheme.palette.labelPrimary,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = AppIcons.Close,
                contentDescription = stringResource(R.string.download_banner_dismiss),
                tint = UaTheme.palette.labelSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
internal fun PremiumSettingsSection(premiumSection: PremiumSectionState) {
    val hasDeveloperMenu = premiumSection.developerStates.isNotEmpty()
    if (!PremiumAvailability.STORE_IS_LIVE && !hasDeveloperMenu) return

    SettingsSection(title = stringResource(R.string.settings_section_premium), icon = AppIcons.Lock) {
        if (PremiumAvailability.STORE_IS_LIVE) {
            PremiumContent(section = premiumSection, nowMillis = System.currentTimeMillis())
        }
        if (hasDeveloperMenu) {
            LabeledRow(stringResource(R.string.settings_developer_license), AppIcons.Lock) {
                for (state in premiumSection.developerStates) {
                    SettingsChip(
                        label = state,
                        isSelected = false,
                        onClick = { premiumSection.onDeveloperStateSelected(state) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun TutorialSettingsSection(guidedTourSection: GuidedTourSectionState) {
    SettingsSection(title = stringResource(R.string.settings_section_tutorial), icon = AppIcons.HelpCircle) {
        Text(
            text = stringResource(
                if (guidedTourSection.hasSeenTour) {
                    R.string.settings_tutorial_hint_seen
                } else {
                    R.string.settings_tutorial_hint_unseen
                },
            ),
            style = BodyRegular,
            color = UaTheme.palette.labelSecondary,
        )
        LinkRow(
            label = stringResource(R.string.settings_tutorial_label),
            buttonLabel = stringResource(R.string.settings_tutorial_button),
            onClick = guidedTourSection.onStartTour,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
internal fun HelpSettingsSection(
    settingsState: SettingsUiState,
    onOpenHelp: () -> Unit,
    onOpenTerms: () -> Unit,
    onBuildDiagnosticsReport: () -> String,
    remuxEffectiveness: RemuxEffectivenessCounts,
    ioDispatcher: CoroutineDispatcher = AppDispatchers.io,
) {
    SettingsSection(title = stringResource(R.string.settings_help), icon = AppIcons.HelpCircle) {
        Text(
            text = stringResource(R.string.settings_device_tier_label) + ": " +
                stringResource(settingsState.deviceTier.labelRes()),
            style = BodyRegular,
            color = UaTheme.palette.labelSecondary,
        )
        Text(
            text = stringResource(R.string.settings_app_version) + ": " + BuildConfig.VERSION_NAME,
            style = BodyRegular,
            color = UaTheme.palette.labelSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.settings_help_body),
            style = BodyRegular,
            color = UaTheme.palette.labelPrimary,
            modifier = Modifier.padding(top = 12.dp),
        )
        LinkRow(
            label = stringResource(R.string.settings_open_help),
            buttonLabel = stringResource(R.string.settings_open_button),
            onClick = onOpenHelp,
            modifier = Modifier.padding(top = 16.dp),
            buttonTag = UiTestTags.SETTINGS_OPEN_HELP_BUTTON,
        )
        LinkRow(
            label = stringResource(R.string.settings_open_terms),
            buttonLabel = stringResource(R.string.settings_open_button),
            onClick = onOpenTerms,
            modifier = Modifier.padding(top = 8.dp),
        )
        SendDiagnosticsRow(
            onBuildReport = onBuildDiagnosticsReport,
            ioDispatcher = ioDispatcher,
            modifier = Modifier.padding(top = 8.dp),
        )
        RoutingEffectivenessBlock(remuxEffectiveness, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun SendDiagnosticsRow(
    onBuildReport: () -> String,
    ioDispatcher: CoroutineDispatcher,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<String?>(null) }
    val diagnosticsScope = rememberCoroutineScope()
    val chooserTitle = stringResource(R.string.diagnostics_share_chooser_title)

    LinkRow(
        label = stringResource(R.string.settings_send_diagnostics),
        buttonLabel = stringResource(R.string.settings_send_button),
        onClick = {
            diagnosticsScope.launch { report = withContext(ioDispatcher) { onBuildReport() } }
        },
        modifier = modifier,
    )

    report?.let { built ->
        DiagnosticsPreviewDialog(
            report = built,
            onCancel = { report = null },
            onSend = {
                report = null
                diagnosticsScope.launch { sendDiagnostics(context, built, chooserTitle) }
            },
        )
    }
}

private data class RouteLine(val labelRes: Int, val attempted: Int, val played: Int, val failed: Int)

@Composable
private fun RoutingEffectivenessBlock(counts: RemuxEffectivenessCounts, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_diagnostics_routing_label),
            style = CaptionSemibold,
            color = UaTheme.palette.labelSecondary,
        )
        if (RemuxEffectivenessPolicy.isUntouched(counts)) {
            Text(
                text = stringResource(R.string.settings_diagnostics_route_never_cast),
                style = Caption,
                color = UaTheme.palette.labelSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
            return@Column
        }
        for (line in counts.routeLines()) {
            Text(
                text = stringResource(
                    R.string.settings_diagnostics_route_line,
                    stringResource(line.labelRes),
                    line.attempted,
                    line.played,
                    line.failed,
                ),
                style = Caption,
                color = UaTheme.palette.labelSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun RemuxEffectivenessCounts.routeLines(): List<RouteLine> = listOf(
    RouteLine(R.string.settings_diagnostics_route_direct, directAttempted, directPlaying, directFailed),
    RouteLine(R.string.settings_diagnostics_route_remux, remuxAttempted, remuxPlaying, remuxFailed),
    RouteLine(
        R.string.settings_diagnostics_route_rewrite,
        proxyRewriteAttempted,
        proxyRewritePlaying,
        proxyRewriteFailed,
    ),
)

@Composable
private fun LinkRow(
    label: String,
    buttonLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonTag: String? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = BodyRegular,
            color = UaTheme.palette.labelPrimary,
            modifier = Modifier.weight(1f),
        )
        SecondaryButton(
            text = buttonLabel,
            onClick = onClick,
            modifier = if (buttonTag == null) Modifier else Modifier.testTag(buttonTag),
        )
    }
}

private fun DeviceTier.labelRes(): Int = when (this) {
    DeviceTier.LOW_END -> R.string.device_tier_low_end
    DeviceTier.MID_RANGE -> R.string.device_tier_mid_range
    DeviceTier.HIGH_END -> R.string.device_tier_high_end
}
