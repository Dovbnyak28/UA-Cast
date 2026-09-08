package com.uacastplayer.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.data.playlist.withPlaylistCpuCancellable
import com.uacastplayer.playlist.ChannelPickerMatches
import com.uacastplayer.playlist.ChannelPickerSearch
import kotlinx.coroutines.delay
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.UiTestTags
import com.uacastplayer.ui.components.uaTextFieldColors
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.UaTheme

private const val SEARCH_DEBOUNCE_MILLIS = 180L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerChannelSheet(
    channels: List<M3uChannel>,
    currentChannel: M3uChannel?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches by key(channels, query, currentChannel) {
        produceState<ChannelPickerMatches?>(null) {
            if (query.isNotBlank()) delay(SEARCH_DEBOUNCE_MILLIS)
            value = withPlaylistCpuCancellable { checkCancellation ->
                ChannelPickerSearch.search(channels, query, currentChannel, checkCancellation)
            }
        }
    }
    val positions = matches?.positions
    val listState = rememberLazyListState()
    LaunchedEffect(matches) {
        matches?.let { listState.scrollToItem(it.scrollPosition) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = UaTheme.palette.surface2,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding)) {
            Text(stringResource(R.string.nav_channels), style = Title, color = UaTheme.palette.labelPrimary)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.player_search_channels)) },
                singleLine = true,
                colors = uaTextFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            if (positions.isNullOrEmpty()) {
                Text(
                    stringResource(
                        if (positions == null) {
                            R.string.player_channels_loading
                        } else {
                            R.string.player_channels_no_results
                        },
                    ),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false).testTag(UiTestTags.PLAYER_CHANNEL_LIST),
            ) {
                items(positions.orEmpty(), key = { it }) { index ->
                    val channel = channels[index]
                    Text(
                        text = channel.displayName,
                        style = BodyText,
                        color = if (channel == currentChannel) {
                            UaTheme.palette.accentText
                        } else {
                            UaTheme.palette.labelPrimary
                        },
                        modifier = Modifier.fillMaxWidth().selectable(
                            selected = channel == currentChannel,
                            role = Role.RadioButton,
                            onClick = { onSelect(index); onDismiss() },
                        ).heightIn(min = 48.dp).padding(12.dp),
                    )
                }
            }
        }
    }
}
