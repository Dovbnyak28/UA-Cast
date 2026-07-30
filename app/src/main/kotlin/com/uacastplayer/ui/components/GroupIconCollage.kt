package com.uacastplayer.ui.components
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.uacastplayer.icons.GroupCollagePolicy
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.AppThemePreviewParameter
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.UaCastTheme
import java.io.File

/**
 * A group card's visual identity: up to 4 mini channel logos arranged in a collage instead of the
 * plain category glyph, when the group actually has cached icons to show. Only ever reads from
 * disk cache (see [cachedIconFile]/`IconRepository.cachedIconFile`) - a group overview can render
 * dozens of these at once, and speculatively fetching icons nobody asked for would multiply
 * traffic for something purely decorative. [fallback] (the glyph a caller already renders for
 * other groups) covers both "still resolving" and "fewer than 1 icon found in cache".
 */
@Composable
fun GroupIconCollage(
    channels: List<M3uChannel>,
    cachedIconFile: suspend (M3uChannel) -> File?,
    size: Dp,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    // Included in produceState's key so a caller can force a re-resolve once new information that
    // could change the result becomes available - see ChannelIcon's refreshKey for the same idea.
    refreshKey: Any? = null,
) {
    val candidates = remember(channels) { GroupCollagePolicy.candidateChannels(channels) }
    val files by produceState<List<File>?>(initialValue = null, key1 = candidates, key2 = refreshKey) {
        value = candidates.mapNotNull { cachedIconFile(it) }
    }
    val resolved = files

    Box(modifier = modifier.size(size).clip(RoundedCornerShape(RadiusItem))) {
        if (resolved.isNullOrEmpty()) {
            fallback()
        } else {
            CollageLayout(resolved)
        }
    }
}

/** 1 logo fills the whole tile; 2 stack vertically; 3 is 2 (left, stacked) + 1 (right, full height);
 * 4 is a plain 2x2 grid. [GroupCollagePolicy.MAX_TILES] caps [files] at 4, so there's no `else`
 * case beyond it. */
private const val THREE_TILE_COUNT = 3

@Composable
private fun CollageLayout(files: List<File>) {
    when (files.size) {
        1 -> CollageTile(files[0], Modifier.fillMaxSize())
        2 -> Column(modifier = Modifier.fillMaxSize()) {
            CollageTile(files[0], Modifier.weight(1f).fillMaxWidth())
            CollageDivider(Modifier.fillMaxWidth().height(1.dp))
            CollageTile(files[1], Modifier.weight(1f).fillMaxWidth())
        }
        THREE_TILE_COUNT -> Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                CollageTile(files[0], Modifier.weight(1f).fillMaxWidth())
                CollageDivider(Modifier.fillMaxWidth().height(1.dp))
                CollageTile(files[1], Modifier.weight(1f).fillMaxWidth())
            }
            CollageDivider(Modifier.fillMaxHeight().width(1.dp))
            CollageTile(files.last(), Modifier.weight(1f).fillMaxHeight())
        }
        else -> Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                CollageTile(files[0], Modifier.weight(1f).fillMaxHeight())
                CollageDivider(Modifier.fillMaxHeight().width(1.dp))
                CollageTile(files[1], Modifier.weight(1f).fillMaxHeight())
            }
            CollageDivider(Modifier.fillMaxWidth().height(1.dp))
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                CollageTile(files[2], Modifier.weight(1f).fillMaxHeight())
                CollageDivider(Modifier.fillMaxHeight().width(1.dp))
                CollageTile(files.last(), Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun CollageTile(file: File, modifier: Modifier) {
    AsyncImage(model = file, contentDescription = null, modifier = modifier)
}

@Composable
private fun CollageDivider(modifier: Modifier) {
    Box(modifier = modifier.background(UaTheme.palette.void))
}

/** No cached files to resolve here, so this always renders [fallback] - the collage layout itself
 * needs real decodable files on disk, which a static preview doesn't have. */
@Preview(showBackground = true, backgroundColor = 0xFF0B0B12L)
@Composable
private fun GroupIconCollagePreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        GroupIconCollage(
            channels = emptyList(),
            cachedIconFile = { null },
            size = 64.dp,
            fallback = {
                Box(
                    modifier = Modifier.fillMaxSize().background(UaTheme.palette.surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Tv, contentDescription = null, tint = UaTheme.palette.labelSecondary)
                }
            },
        )
    }
}
