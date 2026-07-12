package com.uacastplayer.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes

object MediaItemFactory {

    fun forChannel(streamUrl: String): MediaItem {
        val mimeType = when (StreamMimeClassifier.classify(streamUrl)) {
            StreamType.HLS -> MimeTypes.APPLICATION_M3U8
            StreamType.DASH -> MimeTypes.APPLICATION_MPD
        }
        return MediaItem.Builder()
            .setUri(streamUrl)
            .setMimeType(mimeType)
            .build()
    }
}
