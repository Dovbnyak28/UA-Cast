package com.uacastplayer.ui.components
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.AppThemePreviewParameter
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.ChannelLogoRadius
import com.uacastplayer.ui.theme.ChannelLogoSize
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.theme.raisedSurface
import java.io.File

private const val MAX_INITIALS = 2

/**
 * A channel's resolved logo, falling back to its initials in a plain box while resolving, if none
 * is available, or if Coil fails to decode the file [resolveIcon] returned - a file IconDiskCache
 * already accepted as *a* recognized format (see ImageFormatDetector) can still fail to decode
 * (e.g. a malformed SVG, or a format accepted there but unsupported by the installed decoders), and
 * an empty square must never be a possible rendered state.
 */
@Composable
fun ChannelIcon(
    channel: M3uChannel,
    resolveIcon: suspend (M3uChannel) -> File?,
    modifier: Modifier = Modifier,
    size: Dp = ChannelLogoSize,
    // Included in produceState's key so a caller can force a re-resolve once new information that
    // could change the result becomes available (EPG data arriving, an icon prefetch run
    // finishing) - resolveIcon's own result is otherwise only ever computed once per streamUrl.
    refreshKey: Any? = null,
) {
    val iconFile by produceState<File?>(initialValue = null, key1 = channel.streamUrl, key2 = refreshKey) {
        value = resolveIcon(channel)
    }
    var isDecodeError by remember(iconFile) { mutableStateOf(false) }

    if (iconFile != null && !isDecodeError) {
        AsyncImage(
            model = iconFile,
            contentDescription = null,
            onState = { state -> isDecodeError = state is AsyncImagePainter.State.Error },
            modifier = modifier.size(size).clip(RoundedCornerShape(ChannelLogoRadius)),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .raisedSurface(RoundedCornerShape(ChannelLogoRadius), UaTheme.palette.surface2, shadow = false),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = initialsFor(channel.displayName), style = Caption, color = UaTheme.palette.labelSecondary)
        }
    }
}

/**
 * The first letter of each of the first [MAX_INITIALS] words, uppercased.
 *
 * Scans [name] once instead of the `trim().split(regex).mapNotNull{}.take(2).joinToString()` this
 * replaced: that ran a regex matcher and built four intermediate lists per call, and it is called
 * on every recomposition of every channel row falling back to initials - no icon resolved yet, or a
 * decode error - including the swipe-preview rail that recomposes live during playback. Stopping at
 * [MAX_INITIALS] also means a long name is no longer split in full just to discard all but two
 * pieces of it.
 */
fun initialsFor(name: String): String {
    val initials = StringBuilder(MAX_INITIALS)
    var atWordStart = true
    var index = 0
    // Having enough initials is a loop condition rather than a break: a long name stops being
    // scanned the moment the answer is complete.
    while (index < name.length && initials.length < MAX_INITIALS) {
        val character = name[index]
        if (character.isWhitespace()) {
            atWordStart = true
        } else {
            if (atWordStart) initials.append(character.uppercaseChar())
            atWordStart = false
        }
        index++
    }
    return initials.toString()
}

/** No file to resolve here, so this always renders the initials-fallback path - the file-found/
 * AsyncImage path needs a real decodable file on disk, which a static preview doesn't have. */
@Preview(showBackground = true, backgroundColor = 0xFF0B0B12L)
@Composable
private fun ChannelIconPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        ChannelIcon(
            channel = M3uChannel(displayName = "BBC News", streamUrl = "https://example.com/stream.m3u8"),
            resolveIcon = { null },
        )
    }
}
