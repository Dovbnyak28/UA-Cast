package app.zorik.player.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.zorik.player.R
import app.zorik.player.playlist.GroupedChannels
import app.zorik.player.playlist.groupDisplayKey
import app.zorik.player.ui.channels.groupIcon
import app.zorik.player.ui.channels.groupLabel
import app.zorik.player.ui.channels.groupTone
import app.zorik.player.ui.components.UaCard
import app.zorik.player.ui.media.LocalOpenVideo
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.theme.BodyText
import app.zorik.player.ui.theme.Caption
import app.zorik.player.ui.theme.SectionLabel
import app.zorik.player.ui.theme.UaTheme

@Composable
internal fun HomeQuickActions(onOpenChannels: () -> Unit) {
    val openVideo = LocalOpenVideo.current
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val channels: @Composable (Modifier) -> Unit = { modifier ->
        HomeShortcut(stringResource(R.string.home_view_channels_button), AppIcons.Channels, onOpenChannels, modifier)
    }
    val video: @Composable (Modifier) -> Unit = { modifier ->
        if (openVideo != null) HomeShortcut(stringResource(R.string.local_video_open), AppIcons.Play, openVideo,
            modifier)
    }
    if (largeText) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        channels(Modifier.fillMaxWidth())
        video(Modifier.fillMaxWidth())
    } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        channels(Modifier.weight(1f))
        if (openVideo != null) video(Modifier.weight(1f))
    }
}

@Composable
private fun HomeShortcut(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    UaCard(modifier.heightIn(min = 92.dp), onClick = onClick) {
        Icon(icon, null, Modifier.size(24.dp), tint = UaTheme.colors.material.primary)
        Text(label, style = BodyText, color = UaTheme.palette.labelPrimary,
            modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
internal fun HomeCategoryShelf(
    groups: List<GroupedChannels>,
    hiddenKeys: Set<String>,
    onOpen: (String) -> Unit,
) {
    val visible = remember(groups,
        hiddenKeys) { groups.asSequence().filter { groupDisplayKey(it.group) !in hiddenKeys }.take(8).toList() }
    if (visible.isEmpty()) return
    Column {
        Text(stringResource(R.string.channels_groups_title), style = SectionLabel,
            color = UaTheme.palette.labelSecondary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
            items(visible, key = { groupDisplayKey(it.group) }, contentType = { "category" }) { group ->
                UaCard(Modifier.width(152.dp), onClick = { onOpen(groupDisplayKey(group.group)) }) {
                    Icon(groupIcon(group.group), null, tint = groupTone(group.group), modifier = Modifier.size(26.dp))
                    Text(groupLabel(group.group), style = BodyText, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp))
                    Text(pluralStringResource(R.plurals.channels_total_count, group.channels.size, group.channels.size),
                        style = Caption, color = UaTheme.palette.labelSecondary)
                }
            }
        }
    }
}
