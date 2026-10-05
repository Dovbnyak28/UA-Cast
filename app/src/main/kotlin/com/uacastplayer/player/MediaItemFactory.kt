package com.uacastplayer.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.uacastplayer.core.media.StreamMimeClassifier
import com.uacastplayer.core.media.StreamType

object MediaItemFactory {

    fun forChannel(streamUrl: String, progressive: Boolean = false): MediaItem {
        val mimeType = if (progressive) null else when (StreamMimeClassifier.classify(streamUrl)) {
            StreamType.HLS -> MimeTypes.APPLICATION_M3U8
            StreamType.DASH -> MimeTypes.APPLICATION_MPD
            StreamType.MPEG_TS, StreamType.PROGRESSIVE -> null
        }
        return MediaItem.Builder()
            .setUri(streamUrl)
            .setMimeType(mimeType)
            .build()
    }

    /** One fallback for ambiguous provider URLs; the replacement has no HLS MIME, so this
     * cannot oscillate between parsers. Explicit adaptive manifests retain their real errors. */
    fun progressiveFallback(item: MediaItem?, errorCode: Int): MediaItem? {
        val config = item?.localConfiguration ?: return null
        val canFallback = errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED &&
            config.mimeType == MimeTypes.APPLICATION_M3U8 &&
            !config.uri.path.orEmpty().endsWith(".m3u8", ignoreCase = true)
        return if (canFallback) forChannel(config.uri.toString(), progressive = true) else null
    }
}
