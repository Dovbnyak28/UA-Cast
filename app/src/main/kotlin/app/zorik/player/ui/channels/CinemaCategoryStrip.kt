package app.zorik.player.ui.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.zorik.player.playlist.ChannelGroup
import app.zorik.player.playlist.GroupOrderPolicy
import app.zorik.player.playlist.GroupedChannels
import app.zorik.player.playlist.groupDisplayKey
import app.zorik.player.ui.tv.tvFocus

/** Bounded lazy composition, even when a provider supplies thousands of groups. */
@Composable
internal fun CinemaCategoryStrip(
    groups: List<GroupedChannels>,
    selected: ChannelGroup,
    pinned: Set<String>,
    hidden: Set<String>,
    onSelect: (GroupedChannels) -> Unit,
) {
    val visible = remember(groups, pinned, hidden) { GroupOrderPolicy.order(groups, pinned, hidden) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(visible, key = { groupDisplayKey(it.group) }, contentType = { "category" }) { group ->
            FilterChip(selected = selected == group.group, onClick = { onSelect(group) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = app.zorik.player.ui.theme.UaTheme.colors.material.primary,
                    selectedLabelColor = app.zorik.player.ui.theme.UaTheme.colors.material.onPrimary),
                label = { Text(groupLabel(group.group), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.heightIn(min = 48.dp).widthIn(max = 224.dp).tvFocus())
        }
    }
}
