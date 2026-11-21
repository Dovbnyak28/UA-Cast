package com.uacastplayer

import androidx.compose.runtime.saveable.Saver
import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.media.LocalVideo
import com.uacastplayer.player.PlayerRequest

/** Small restoration marker, never a playlist, file body or retained Android descriptor. */
internal data class SavedPlayerRequest(val channelKey: String, val startIndex: Int, val localVideo: LocalVideo? = null)

internal fun PlayerRequest.toSavedRequest(): SavedPlayerRequest? = channels.getOrNull(startIndex)?.let { channel ->
    if (channel.localVideo?.canRestore == false) null
    else SavedPlayerRequest(FavoriteKey.of(channel), startIndex, channel.localVideo)
}

internal fun SavedPlayerRequest.preferredChannelKey(persistedKey: String?): String =
    if (localVideo != null) channelKey else persistedKey ?: channelKey

internal val SavedPlayerRequestSaver: Saver<SavedPlayerRequest?, List<Any>> = Saver(
    save = { request -> request?.let {
        val base = listOf(it.channelKey, it.startIndex)
        it.localVideo?.let { video -> base + listOf(video.uri, video.displayName, video.mimeType) } ?: base
    }.orEmpty() },
    restore = ::restorePlayerRequest,
)

private fun restorePlayerRequest(saved: List<Any>): SavedPlayerRequest? {
    val key = saved.getOrNull(0) as? String
    val index = saved.getOrNull(1) as? Int
    return if (key.isNullOrBlank() || index == null || index < 0) null else when (saved.size) {
        CHANNEL_MARKER_SIZE -> SavedPlayerRequest(key, index)
        VIDEO_MARKER_SIZE -> runCatchingNonFatal {
            val video = LocalVideo(saved[URI_INDEX] as String, saved[NAME_INDEX] as String,
                saved[MIME_INDEX] as String, true)
            SavedPlayerRequest(key, index, video)
        }.getOrNull()
        else -> null
    }
}

private const val CHANNEL_MARKER_SIZE = 2
private const val VIDEO_MARKER_SIZE = 5
private const val URI_INDEX = 2
private const val NAME_INDEX = 3
private const val MIME_INDEX = 4
