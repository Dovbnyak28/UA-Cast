package com.uacastplayer.cast

import androidx.core.net.toUri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.common.images.WebImage
import com.uacastplayer.core.cast.TsSourceKind
import java.util.UUID

/** Builds a direct-to-receiver load request for a channel's stream URL. */
object CastMediaLoader {

    fun forPlayback(url: String, title: String, channel: CastChannel?, kind: TsSourceKind?): MediaLoadRequestData {
        val video = channel?.localVideo
        return buildRequest(url, title, kind, channel?.logoUrl, video?.mimeType, isLive = video == null)
    }

    fun buildRequest(
        streamUrl: String,
        title: String,
        sourceKind: TsSourceKind? = null,
        logoUrl: String? = null,
        contentType: String? = null,
        isLive: Boolean = true,
    ): MediaLoadRequestData {
        val mimeType = contentType ?: CastContentType.of(streamUrl, sourceKind)
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            // Without an image the Default Media Receiver shows a bare title on a black screen, and
            // the sender's own cast controller dialog shows a large empty grey panel where the
            // artwork belongs. Only a logo selected from a user-added pack is passed here.
            // Blank is dropped rather than passed on as an empty Uri:
            // the receiver treats a broken image as an error to report, not as "no image".
            logoUrl?.takeIf { it.isNotBlank() }?.let { addImage(WebImage(it.toUri())) }
        }
        // contentId (the Builder's constructor arg) is only a logical identifier as far as the
        // Default Media Receiver is concerned - contentUrl is what it actually fetches. Without it
        // explicitly set, the receiver has no playable URL to resolve and rejects the load
        // outright ("Invalid Request"), before ever making an HTTP request our own proxy could see.
        // The proxy URL is intentionally stable. A separate identity rejects late receiver status
        // from a previous buffered load of that same file, without changing the URL it fetches.
        val contentId = if (isLive) streamUrl else "$streamUrl#ua-cast-load=${UUID.randomUUID()}"
        val mediaInfo = MediaInfo.Builder(contentId)
            .setContentUrl(streamUrl)
            .setStreamType(if (isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(mimeType)
            .setMetadata(metadata)
            .build()
        return MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build()
    }
}
