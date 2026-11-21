package com.uacastplayer.player

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import java.io.File

/** Test-only, offline AVC/MP4 fixture. Encoding has an explicit deadline and releases native objects. */
internal object SyntheticVideoEncoder {
    fun encode(file: File, luma: Int = 16) {
        require(luma in 16..235)
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        try {
            val capabilities = codec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
            // Legacy constants remain needed for buffer-input encoders on supported older devices.
            @Suppress("DEPRECATION")
            val color = listOf(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                .first { it in capabilities.colorFormats }
            val videoCapabilities = checkNotNull(capabilities.videoCapabilities) {
                "AVC encoder does not expose video capabilities"
            }
            check(videoCapabilities.isSizeSupported(WIDTH, HEIGHT))
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, color)
                setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 10)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val frame = ByteArray(WIDTH * HEIGHT * 3 / 2) { index -> if (index < WIDTH * HEIGHT) luma.toByte() else 128.toByte() }
            val info = MediaCodec.BufferInfo()
            var frameIndex = 0
            var track = -1
            var done = false
            val deadline = SystemClock.elapsedRealtime() + 15_000
            while (!done && SystemClock.elapsedRealtime() < deadline) {
                if (frameIndex <= FRAME_COUNT) {
                    val input = codec.dequeueInputBuffer(10_000)
                    if (input >= 0) {
                        val eos = frameIndex == FRAME_COUNT
                        if (!eos) codec.getInputBuffer(input)!!.apply { clear(); put(frame) }
                        codec.queueInputBuffer(input, 0, if (eos) 0 else frame.size, frameIndex * 100_000L,
                            if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        frameIndex++
                    }
                }
                when (val output = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    else -> if (output >= 0) {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            check(muxerStarted)
                            muxer.writeSampleData(track, codec.getOutputBuffer(output)!!, info)
                        }
                        done = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(output, false)
                    }
                }
            }
            check(done && muxerStarted) { "Synthetic video encoding did not complete" }
        } finally {
            try { codec.release() } finally {
                try { if (muxerStarted) muxer.stop() } finally { muxer.release() }
            }
        }
    }

    private const val WIDTH = 320
    private const val HEIGHT = 240
    private const val FRAME_COUNT = 30
}
