package app.zorik.player.ui.components
import app.zorik.player.ui.theme.UaTheme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import app.zorik.player.ui.tv.tvFocus
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import app.zorik.player.R
import app.zorik.player.player.SleepTimerDuration
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.AppThemePreviewParameter
import app.zorik.player.ui.theme.BodyText
import app.zorik.player.ui.theme.ZorikTheme
import kotlin.time.Duration

@Composable
fun SleepTimerDialog(
    isTimerActive: Boolean,
    onSelect: (Duration) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = UaTheme.palette.surface2,
        titleContentColor = UaTheme.palette.labelPrimary,
        textContentColor = UaTheme.palette.labelSecondary,
        title = { Text(stringResource(R.string.player_sleep_timer)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SleepTimerDuration.entries.forEach { option ->
                    SleepTimerDialogRow(
                        label = stringResource(R.string.player_sleep_timer_minutes, option.minutes),
                        onClick = { onSelect(option.duration) },
                    )
                }
                if (isTimerActive) {
                    SleepTimerDialogRow(
                        label = stringResource(R.string.player_sleep_timer_cancel),
                        emphasized = true,
                        onClick = onCancelTimer,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.tvFocus()) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}

@Composable
private fun SleepTimerDialogRow(label: String, onClick: () -> Unit, emphasized: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .tvFocus()
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (emphasized) UaTheme.palette.routeRed else UaTheme.palette.labelPrimary,
            style = BodyText,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0B12L)
@Composable
private fun SleepTimerDialogPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    ZorikTheme(theme) {
        SleepTimerDialog(isTimerActive = true, onSelect = {}, onCancelTimer = {}, onDismiss = {})
    }
}
