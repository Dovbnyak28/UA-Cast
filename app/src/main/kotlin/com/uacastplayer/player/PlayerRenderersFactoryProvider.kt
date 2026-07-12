package com.uacastplayer.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.RenderersFactory
import com.uacastplayer.log.AppLog
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

private const val TAG = "PlayerRenderersFactory"

/**
 * NextRenderersFactory adds FFmpeg-backed software decoders (MP2/AC-3/DTS audio in particular,
 * which are common on IPTV feeds but not guaranteed on-device). If it fails to construct for any
 * reason, fall back to the plain DefaultRenderersFactory rather than crashing the player.
 */
@UnstableApi
object PlayerRenderersFactoryProvider {

    fun create(context: Context): RenderersFactory {
        return try {
            NextRenderersFactory(context).apply {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                setEnableDecoderFallback(true)
            }
        } catch (e: Exception) {
            AppLog.w(TAG) { "NextRenderersFactory unavailable, falling back to default: ${e.javaClass.simpleName}" }
            DefaultRenderersFactory(context).apply {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            }
        }
    }
}
