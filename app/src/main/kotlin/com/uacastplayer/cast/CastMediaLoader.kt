package com.uacastplayer.cast

import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata

/** Builds a direct-to-receiver load request for a channel's stream URL. */
object CastMediaLoader {

    fun buildRequest(streamUrl: String, title: String, sourceKind: TsSourceKind? = null): MediaLoadRequestData {
        val mimeType = CastContentType.of(streamUrl, sourceKind)
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC).apply {
            putString(MediaMetadata.KEY_TITLE, title)
        }
        // contentId (the Builder's constructor arg) is only a logical identifier as far as the
        // Default Media Receiver is concerned - contentUrl is what it actually fetches. Without it
        // explicitly set, the receiver has no playable URL to resolve and rejects the load
        // outright ("Invalid Request"), before ever making an HTTP request our own proxy could see.
        val mediaInfo = MediaInfo.Builder(streamUrl)
            .setContentUrl(streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType(mimeType)
            .setMetadata(metadata)
            .build()
        return MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build()
    }
}
