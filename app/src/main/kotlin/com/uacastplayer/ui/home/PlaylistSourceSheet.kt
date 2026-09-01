package com.uacastplayer.ui.home
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourceLabel
import com.uacastplayer.playlist.PlaylistSourceType
import com.uacastplayer.ui.components.PrimaryButton
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.Title

/**
 * Reached by tapping Home's active-playlist card - lists every saved source (see
 * [PlaylistSource]/`PlaylistSourceStore`) with a radio selection for the active one, a delete
 * action per row, and a way to add another.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSourceSheet(
    sources: List<PlaylistSource>,
    activeId: String?,
    onSelect: (PlaylistSource) -> Unit,
    onRemove: (PlaylistSource) -> Unit,
    onAddNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding).padding(bottom = GapM)) {
            Text(
                text = stringResource(R.string.home_playlist_sources_title),
                style = Title,
                color = UaTheme.palette.labelPrimary,
                modifier = Modifier.padding(bottom = GapM),
            )
            // Most recently added first, same ordering PlaylistSourcePolicy.remove's fallback uses.
            for (source in sources.sortedByDescending { it.addedAtEpochMillis }) {
                PlaylistSourceRow(
                    source = source,
                    isActive = source.id == activeId,
                    onSelect = { onSelect(source) },
                    onRemove = { onRemove(source) },
                )
            }
            PrimaryButton(
                text = stringResource(R.string.home_add_playlist_button),
                onClick = onAddNew,
                leadingIcon = AppIcons.Plus,
                modifier = Modifier.fillMaxWidth().padding(top = GapM),
            )
        }
    }
}

@Composable
private fun PlaylistSourceRow(source: PlaylistSource, isActive: Boolean, onSelect: () -> Unit, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(RadiusItem)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isActive) UaTheme.palette.accentGradientTop.copy(alpha = 0.10f)
                else UaTheme.palette.surface1,
            )
            .clickable(role = Role.RadioButton, onClickLabel = source.displayName, onClick = onSelect)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isActive,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = UaTheme.palette.azure,
                unselectedColor = UaTheme.palette.labelSecondary,
            ),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = source.displayName
                    ?: PlaylistSourceLabel.forLocation(source.type, source.location)
                    ?: stringResource(R.string.playlist_unnamed),
                style = BodyText,
                color = UaTheme.palette.labelPrimary,
                maxLines = 1,
            )
            Text(text = stringResource(source.type.labelRes()), style = Caption, color = UaTheme.palette.labelSecondary)
        }
        IconButton(onClick = onRemove) {
            Icon(
                AppIcons.Delete,
                contentDescription = stringResource(R.string.home_playlist_source_remove),
                tint = UaTheme.palette.routeRed,
            )
        }
    }
}

private fun PlaylistSourceType.labelRes(): Int = when (this) {
    PlaylistSourceType.URL -> R.string.add_playlist_type_url
    PlaylistSourceType.FILE -> R.string.add_playlist_type_file
    PlaylistSourceType.XTREAM -> R.string.add_playlist_type_xtream
}
