package app.zorik.player.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.zorik.player.R
import app.zorik.player.epg.EpgProgramme
import app.zorik.player.epg.EpgUiState
import app.zorik.player.epg.EpgLookup
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.epg.ProgrammeProgress
import app.zorik.player.ui.components.TrackProgress
import app.zorik.player.ui.epg.rememberEpgTimeZone
import app.zorik.player.ui.theme.Caption
import app.zorik.player.ui.theme.CaptionSemibold
import app.zorik.player.ui.theme.UaTheme
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
internal fun rememberPlayerProgramme(channel: M3uChannel?, state: EpgUiState?): EpgProgramme? =
    remember(channel, state?.data, state?.nowMillis) {
        val data = state?.data ?: return@remember null
        if (channel == null || channel.localVideo != null) return@remember null
        val result = EpgLookup.currentAndNext(data, channel, state.nowMillis) ?: return@remember null
        result.current?.let { it.copy(stopMillis = result.effectiveStopMillis ?: it.stopMillis) }
    }

/** Broadcast progress has no seek action, thumb or player listener. */
@Composable
internal fun PlayerProgrammePanel(programme: EpgProgramme, nowMillis: Long) {
    val zone = rememberEpgTimeZone()
    val times = remember(programme.startMillis, programme.stopMillis, zone) {
        val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
        formatter.format(Instant.ofEpochMilli(programme.startMillis)) to
            formatter.format(Instant.ofEpochMilli(programme.stopMillis))
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.epg_now), style = Caption, color = UaTheme.palette.accentText)
        Text(programme.title, style = CaptionSemibold, color = UaTheme.palette.labelPrimary,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (programme.stopMillis > programme.startMillis) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(times.first, style = Caption, color = UaTheme.palette.labelSecondary)
                Text(times.second, style = Caption, color = UaTheme.palette.labelSecondary)
            }
            val progress = ProgrammeProgress.progress(programme.startMillis, programme.stopMillis, nowMillis)
            TrackProgress(progress, Modifier.progressSemantics(progress))
        }
    }
}
