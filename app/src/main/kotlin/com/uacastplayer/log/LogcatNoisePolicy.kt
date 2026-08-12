package com.uacastplayer.log

/**
 * Vendor chatter that crowds a device's log ring until nothing else fits in it.
 *
 * Measured, not guessed. A report from a Redmi Note 10 5G (MediaTek, Android 13) carried 2,416
 * lines, of which **1,893 - 78% - were two messages**:
 *
 * ```
 * 1304 x  E/gralloc4  Empty SMPTE 2094-40 data
 *  589 x  W/libc      Access denied finding property "ro.vendor.audio.5k"
 * ```
 *
 * Both are emitted per video frame or per audio buffer, so during playback they arrive at roughly
 * fifty lines a second. The consequence was not merely a long file: logcat's buffer is shared
 * system-wide and had rolled, so the log covered the last **23 seconds** of a session that had run
 * 4 minutes 19 seconds, and the app's own lines from the first four minutes were gone with it.
 *
 * This cannot un-roll the device's ring - nothing in an app can. What it can do is stop
 * [LogcatReader]'s own line budget being spent on the same two sentences, so that what does survive
 * is what a reader came for.
 *
 * A line must match a tag *and* a message fragment to be dropped, so the codec, media3 and OkHttp
 * lines that share these tags' neighbourhood are never caught by accident. Nothing here is dropped
 * silently either: [LogcatReader] states the count in the report.
 *
 * **This list is per-vendor and grows by measurement only.** The second device to send a report -
 * a Xiaomi on Android 16 - shared none of the two above and brought five of its own, together 669
 * of 4,000 lines (17%) in one report and 640 in another taken minutes later. Each entry below
 * carries the counts it was added on. Nothing goes in here on the grounds that it looks unhelpful;
 * a line that is merely verbose is still evidence, and this file's cost of being wrong is a
 * diagnostic that silently is not there.
 *
 * Two of that device's loudest were deliberately **not** added, for that reason:
 * `D/PipelineWatcher pipelineFull` (226 and 412 lines) is graphics-pipeline backpressure and may
 * be the answer to a jank report, and `I/MIUIInput [MotionEvent]` (138 and 179) is the only record
 * of when the user actually touched the screen.
 */
object LogcatNoisePolicy {

    /** Pairs of (tag as logcat prints it, fragment of the message). Both must appear. */
    private val NOISE = listOf(
        // Redmi Note 10 5G (MediaTek, Android 13) - 1304 and 589 lines of one 2,416-line report.
        "E/gralloc4" to "Empty SMPTE 2094-40 data",
        "W/libc" to "Access denied finding property",
        // Xiaomi (Android 16) - counts below are from two reports taken six minutes apart.
        // A window-insets calculation that reports doing nothing, once per layout pass. 235 / 247.
        "W/InsetsSource" to "Has no intersection",
        // MIUI's free-form window bookkeeping, saying there is no free-form window. 104 / 112.
        "D/VRI[MainActivity]" to "miuiFreeFormStackInfo",
        // The codec HAL being asked for vendor parameters it does not implement. 148 / 148, and
        // exactly 148 both times: it is a fixed sweep at codec configuration, not a per-frame log.
        "W/Codec2Client" to "param skipped",
        // 48 / 48, likewise once per codec. Says a message was kept, which is not an event.
        "D/MediaCodec" to "keep callback message for reclaim",
        // HWUI announcing that it has given up logging - the log line for having no log line.
        // 134 / 85.
        "W/HWUI" to "Image decoding logging dropped",
    )

    fun isNoise(line: String): Boolean = NOISE.any { (tag, message) ->
        line.contains(tag) && line.contains(message)
    }
}
