package com.uacastplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.player.SleepTimerDuration
import com.uacastplayer.ui.theme.Azure
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.LabelPrimary
import com.uacastplayer.ui.theme.LabelSecondary
import com.uacastplayer.ui.theme.RouteRed
import com.uacastplayer.ui.theme.Surface2
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
        containerColor = Surface2,
        titleContentColor = LabelPrimary,
        textContentColor = LabelSecondary,
        title = { Text(stringResource(R.string.player_sleep_timer)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            TextButton(onClick = onDismiss) {
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
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (emphasized) RouteRed else LabelPrimary,
            style = BodyText,
        )
    }
}
