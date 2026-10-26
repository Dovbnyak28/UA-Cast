package com.uacastplayer.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.RenderersFactory
import com.uacastplayer.log.AppLog
import androidx.media3.decoder.ffmpeg.FfmpegLibrary

private const val TAG = "PlayerRenderersFactory"

/**
 * Media3 discovers the Apache/LGPL software audio/video extensions from :ffmpegdecoder.
 * Hardware stays first to preserve power/CPU use; software covers codecs unavailable on-device.
 * Packaging fails closed on missing/stale native inputs. A device-specific native-load failure
 * must still allow supported platform formats, rather than crashing the entire player.
 */
@UnstableApi
object PlayerRenderersFactoryProvider {

    fun create(context: Context): RenderersFactory {
        val softwareAvailable = try {
            FfmpegLibrary.isAvailable()
        } catch (error: LinkageError) {
            AppLog.w(TAG) { "Software decoder native load failed: ${error.javaClass.simpleName}" }
            false
        }
        if (!softwareAvailable) AppLog.w(TAG) { "Software decoder unavailable on this runtime" }
        return DefaultRenderersFactory(context.applicationContext).apply {
            setExtensionRendererMode(
                if (softwareAvailable) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF,
            )
            setEnableDecoderFallback(true)
        }
    }
}
