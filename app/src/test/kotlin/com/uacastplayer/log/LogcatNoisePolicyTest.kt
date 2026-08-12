package com.uacastplayer.log

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lines taken verbatim from the field report that prompted this filter - a Redmi Note 10 5G whose
 * log was 78% two sentences, and which therefore covered 23 seconds of a 4-minute session.
 */
class LogcatNoisePolicyTest {

    @Test
    fun theGrallocFrameSpamIsNoise() {
        assertTrue(
            LogcatNoisePolicy.isNoise("08-11 21:53:48.015 E/gralloc4(12972): Empty SMPTE 2094-40 data"),
        )
    }

    @Test
    fun theVendorPropertyProbeIsNoise() {
        assertTrue(
            LogcatNoisePolicy.isNoise(
                """08-11 21:53:48.019 W/libc    (12972): Access denied finding property "ro.vendor.audio.5k"""",
            ),
        )
    }

    /** The app's own lines are the entire reason the filter exists; catching one would be worse
     * than not filtering at all. */
    @Test
    fun theAppsOwnLinesAreKept() {
        assertFalse(
            LogcatNoisePolicy.isNoise(
                "08-11 21:53:48.785 D/IconRepository(12972): cast artwork: none, candidates=0 (fetchable=0)",
            ),
        )
    }

    /**
     * Codec and player plumbing shares this neighbourhood and is exactly what a playback complaint
     * is diagnosed from. Matching on the tag alone would have swept it away.
     */
    @Test
    fun codecAndPlayerPlumbingIsKept() {
        val kept = listOf(
            "08-11 21:53:47.869 D/AudioTrack(12972): start(9105): prior state:STATE_STOPPED",
            "08-11 21:53:48.100 D/CCodecBufferChannel(12972): [c2.mtk.avc.decoder] queue buffer",
            "08-11 21:53:49.000 I/MediaCodec(12972): setting surface generation to 1",
        )
        kept.forEach { assertFalse(it, LogcatNoisePolicy.isNoise(it)) }
    }

    /**
     * A tag with the right name but a different message is a real message from that component -
     * `libc` says other things, and a fatal one would be among them.
     */
    @Test
    fun aDifferentMessageUnderTheSameTagSurvives() {
        assertFalse(
            LogcatNoisePolicy.isNoise("08-11 21:53:48.019 W/libc    (12972): malloc: heap corruption detected"),
        )
    }

    /**
     * Verbatim from the second device to send a report - a Xiaomi on Android 16, which shared not
     * one of the two lines above and brought five of its own. Together 669 of that report's 4,000
     * lines. The list is per-vendor and the next device will bring its own; that is the shape of
     * the problem, not a shortcoming of the fix.
     */
    @Test
    fun theSecondDevicesVendorSpamIsNoise() {
        val noise = listOf(
            "08-12 17:04:58.371 W/InsetsSource(10258): Has no intersection or mTmpFrame.height(), " +
                "return Insets.NONE mTmpFrame.height() =0 hasIntersection =false",
            "08-12 17:05:04.470 D/VRI[MainActivity](10258): <token:1df300> mTmpFrames.miuiFreeFormStackInfo: null",
            "08-12 17:05:14.826 W/Codec2Client(10258): query -- param skipped: index = 1342179345.",
            "08-12 17:05:14.824 D/MediaCodec(10258): keep callback message for reclaim",
            "08-12 17:05:08.692 W/HWUI    (10258): Image decoding logging dropped!",
        )
        noise.forEach { assertTrue(it, LogcatNoisePolicy.isNoise(it)) }
    }

    /**
     * The two loudest lines on that same device that are deliberately kept. `pipelineFull` is
     * graphics-pipeline backpressure and may be the answer to a jank report; `MotionEvent` is the
     * only record of when the user actually touched the screen. Volume alone is not grounds for
     * deleting evidence, and this test is here so a later pass at "the log is still long" does not
     * quietly decide otherwise.
     */
    @Test
    fun loudButMeaningfulLinesAreDeliberatelyKept() {
        val kept = listOf(
            "08-12 17:04:58.456 D/PipelineWatcher(10258): [0xb400] pipelineFull: too many frames in pipeline (6)",
            "08-12 16:58:21.707 I/MIUIInput(1692): [MotionEvent] ViewRootImpl windowName " +
                "'com.uacastplayer/com.uacastplayer.MainActivity', { action=ACTION_DOWN }",
        )
        kept.forEach { assertFalse(it, LogcatNoisePolicy.isNoise(it)) }
    }

    /**
     * The same device also logs `D/HWUI` and `I/MediaCodec` lines that carry real information, and
     * both entries added for it name a level as well as a tag. Dropping on the tag alone would
     * have taken these with them.
     */
    @Test
    fun theSameTagAtADifferentLevelSurvives() {
        val kept = listOf(
            "08-12 17:05:08.700 D/HWUI    (10258): <token:94d515>: width = 143, height = 143, encodedFormat = 4",
            "08-12 17:05:14.900 I/MediaCodec(10258): [mId: 2] [video-debug-dec] queueInputBuffer: index = 3",
        )
        kept.forEach { assertFalse(it, LogcatNoisePolicy.isNoise(it)) }
    }
}
