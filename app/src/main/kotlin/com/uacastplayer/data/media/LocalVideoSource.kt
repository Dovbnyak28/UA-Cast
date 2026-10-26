package com.uacastplayer.data.media

import android.content.Context
import android.os.CancellationSignal
import androidx.core.net.toUri
import com.uacastplayer.media.LocalVideo
import java.io.Closeable
import java.io.FileNotFoundException
import java.io.InputStream

interface LocalVideoSource {
    fun open(video: LocalVideo, cancellation: CancellationSignal): LocalVideoHandle
}

interface LocalVideoHandle : Closeable {
    val length: Long
    val input: InputStream
    fun seek(position: Long)
}

/** One independently owned descriptor per response; no file copy, whole-file buffer or Activity. */
class AndroidLocalVideoSource(context: Context) : LocalVideoSource {
    private val resolver = context.applicationContext.contentResolver

    override fun open(video: LocalVideo, cancellation: CancellationSignal): LocalVideoHandle {
        val asset = resolver.openAssetFileDescriptor(video.uri.toUri(), "r", cancellation)
            ?: throw FileNotFoundException("Selected video is unavailable")
        var transferred = false
        try {
            val length = asset.declaredLength.takeIf { it >= 0 } ?: asset.parcelFileDescriptor.statSize
            check(length > 0) { "Video must have a known non-empty length for remote playback" }
            val input = asset.createInputStream()
            val handle = object : LocalVideoHandle {
                override val length = length
                override val input = input
                override fun seek(position: Long) { input.channel.position(asset.startOffset + position) }
                override fun close() {
                    try { input.close() } finally { asset.close() }
                }
            }
            transferred = true
            return handle
        } finally {
            if (!transferred) asset.close()
        }
    }
}
