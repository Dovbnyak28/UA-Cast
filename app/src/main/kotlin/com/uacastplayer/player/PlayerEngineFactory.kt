package com.uacastplayer.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.uacastplayer.core.settings.BufferSize

/** Builds the local Media3 engine without taking ownership of its playback lifecycle. */
@UnstableApi
internal object PlayerEngineFactory {

    fun create(
        context: Context,
        dataSourceFactory: PlayerDataSourceFactory,
        bufferSize: BufferSize,
    ): ExoPlayer {
        // IPTV origins commonly contain TS streams whose access-unit markers are rejected by the
        // strict defaults. These flags affect TS extraction only; HLS extraction remains separate.
        val extractorsFactory = DefaultExtractorsFactory().setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
        )

        return ExoPlayer.Builder(
            context,
            PlayerRenderersFactoryProvider.create(context),
        )
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory))
            .setLoadControl(buildLoadControl(PlayerBufferProfiles.forSize(bufferSize)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    private fun buildLoadControl(profile: PlayerBufferProfile): DefaultLoadControl =
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                profile.minBufferMs,
                profile.maxBufferMs,
                profile.bufferForPlaybackMs,
                profile.bufferForPlaybackAfterRebufferMs,
            )
            // A duration-derived byte target can become very large on high-bitrate streams. The
            // fixed cap is the primary safeguard against one player exhausting a small heap.
            .setTargetBufferBytes(profile.targetBufferBytes)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
}

/**
 * SMALL favors channel-switch latency, while LARGE trades startup time for unstable-link
 * resilience. Startup/rebuffer thresholds deliberately remain below each cruise buffer.
 */
@UnstableApi
internal object PlayerBufferProfiles {
    fun forSize(bufferSize: BufferSize): PlayerBufferProfile = when (bufferSize) {
        BufferSize.SMALL -> PlayerBufferProfile(
            minBufferMs = SMALL_MIN_BUFFER_MS,
            maxBufferMs = SMALL_MAX_BUFFER_MS,
            bufferForPlaybackMs = FAST_PLAYBACK_START_BUFFER_MS,
            bufferForPlaybackAfterRebufferMs = SMALL_REBUFFER_MS,
            targetBufferBytes = SMALL_TARGET_BYTES,
        )
        BufferSize.MEDIUM -> PlayerBufferProfile(
            minBufferMs = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            maxBufferMs = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
            bufferForPlaybackMs = FAST_PLAYBACK_START_BUFFER_MS,
            bufferForPlaybackAfterRebufferMs = MEDIUM_REBUFFER_MS,
            targetBufferBytes = MEDIUM_TARGET_BYTES,
        )
        BufferSize.LARGE -> PlayerBufferProfile(
            minBufferMs = LARGE_MIN_BUFFER_MS,
            maxBufferMs = LARGE_MAX_BUFFER_MS,
            bufferForPlaybackMs = STANDARD_PLAYBACK_START_BUFFER_MS,
            bufferForPlaybackAfterRebufferMs = LARGE_REBUFFER_MS,
            targetBufferBytes = LARGE_TARGET_BYTES,
        )
    }
}

internal data class PlayerBufferProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val targetBufferBytes: Int,
)

private const val BYTES_PER_MB = 1024 * 1024
private const val SMALL_TARGET_BYTES = 8 * BYTES_PER_MB
private const val MEDIUM_TARGET_BYTES = 16 * BYTES_PER_MB
private const val LARGE_TARGET_BYTES = 24 * BYTES_PER_MB
private const val SMALL_MIN_BUFFER_MS = 10_000
private const val SMALL_MAX_BUFFER_MS = 20_000
private const val FAST_PLAYBACK_START_BUFFER_MS = 1_000
private const val STANDARD_PLAYBACK_START_BUFFER_MS = 2_000
private const val SMALL_REBUFFER_MS = 2_000
private const val MEDIUM_REBUFFER_MS = 2_500
private const val LARGE_MIN_BUFFER_MS = 30_000
private const val LARGE_MAX_BUFFER_MS = 35_000
private const val LARGE_REBUFFER_MS = 5_000
