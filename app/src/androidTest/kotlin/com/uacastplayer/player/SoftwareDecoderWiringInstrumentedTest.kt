package com.uacastplayer.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.ExperimentalFfmpegVideoRenderer
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class SoftwareDecoderWiringInstrumentedTest {
    @Test fun productionPlayerFactoryContainsSoftwareAudioAndVideoExactlyOnce() {
        assertTrue("Software libraries must load in the actual UA-Cast APK", FfmpegLibrary.isAvailable())
        // The relink job runs this same test against a repacked APK signed by a new recipient key.
        val relinkProbe = InstrumentationRegistry.getArguments().getString("uacast.relinkProbe") == "true"
        val expectedLicense = "LGPL version 2.1 or later" + if (relinkProbe) " (UA-Cast relink probe)" else ""
        assertEquals(expectedLicense, FfmpegLibrary.getNativeLicense())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val renderers = PlayerRenderersFactoryProvider.create(context).createRenderers(
            Handler(Looper.getMainLooper()),
            object : VideoRendererEventListener {},
            object : AudioRendererEventListener {},
            {},
            {},
        )
        assertEquals(1, renderers.count { it is ExperimentalFfmpegVideoRenderer })
        assertEquals(1, renderers.count { it is FfmpegAudioRenderer })
    }
}
