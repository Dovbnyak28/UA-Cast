package app.zorik.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.zorik.player.R
import app.zorik.player.data.playlist.withPlaylistCpuCancellable
import app.zorik.player.playlist.ChannelPickerMatches
import app.zorik.player.playlist.ChannelPickerSearch
import kotlinx.coroutines.delay
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.ui.UiTestTags
import app.zorik.player.ui.components.uaTextFieldColors
import app.zorik.player.ui.theme.BodyText
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.theme.ScreenHPadding
import app.zorik.player.ui.theme.Title
import app.zorik.player.ui.theme.UaTheme

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
            ChannelSearchField(query, onQuery = { query = it })
            if (positions.isNullOrEmpty()) {
                Text(
                    stringResource(
                        if (positions == null) {
                            R.string.player_channels_loading
                        } else {
                            R.string.player_channels_no_results
                        },
                    ),
                    color = UaTheme.palette.labelSecondary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false).testTag(UiTestTags.PLAYER_CHANNEL_LIST),
            ) {
                items(positions.orEmpty(), key = { it }) { index ->
                    val channel = channels[index]
                    ChannelPickerRow(channel.displayName, channel == currentChannel) {
                        onSelect(index)
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelSearchField(query: String, onQuery: (String) -> Unit) {
    // Resolve the dialog's input owners, rather than the Activity behind ModalBottomSheet.
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query, onValueChange = onQuery,
        label = { Text(stringResource(R.string.player_search_channels)) },
        leadingIcon = { Icon(AppIcons.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                Icon(AppIcons.Close, stringResource(R.string.channels_clear_search))
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); focus.clearFocus() }),
        singleLine = true, colors = uaTextFieldColors(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(UiTestTags.PLAYER_CHANNEL_SEARCH),
    )
}

@Composable
private fun ChannelPickerRow(name: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth()
        .background(if (selected) UaTheme.palette.azure.copy(alpha = 0.12f) else Color.Transparent, shape)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .heightIn(min = 48.dp).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (selected) Icon(AppIcons.Check, null, tint = UaTheme.palette.azure, modifier = Modifier.size(20.dp))
        else Spacer(Modifier.size(20.dp))
        Text(name, style = BodyText, color = UaTheme.palette.labelPrimary, modifier = Modifier.weight(1f))
    }
}

/** Channel-only navigation stays unavailable while a standalone video owns the player. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable
internal fun PlayerChannelPicker(
    channels: List<M3uChannel>, content: PlayerScreenContent,
    actions: PlayerScreenActions, transientState: PlayerScreenTransientState,
) {
    if (transientState.showChannelsSheet && content.uiState.currentChannel?.localVideo == null) {
        PlayerChannelSheet(channels, content.uiState.currentChannel, actions.viewModel.navigation::requestSwitch) {
            transientState.showChannelsSheet = false
        }
    }
}
