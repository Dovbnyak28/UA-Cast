package com.uacastplayer.proxy

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** One window of ordinary proxy traffic. [windowMillis] is 0 for the very first serve of a
 * session, which is reported on its own rather than waited for - see [ProxyServeRollup]. */
data class ProxyServeSummary(
    val playlists: Int,
    val segments: Int,
    val bytes: Long,
    val windowMillis: Long,
) {
    /** Deliberately not a resource id or a url: this stands in for hundreds of individual serves,
     * and the only thing they had in common is that they worked. */
    fun sentence(): String {
        val counts = "$playlists playlist(s), $segments segment(s), ${bytes / BYTES_PER_MB}MB"
        return if (windowMillis <= 0) {
            "Proxy serving: $counts (first serve of this session)"
        } else {
            "Proxy served: $counts in ${windowMillis / MILLIS_PER_SECOND}s"
        }
    }

    private companion object {
        const val BYTES_PER_MB = 1024 * 1024
        const val MILLIS_PER_SECOND = 1000
    }
}

/**
 * Turns one log line per served request into one per minute, without losing what the lines were
 * added for.
 *
 * Two field reports from a Mi A2 made the case beyond argument. The app's own log ring holds 500
 * entries ([com.uacastplayer.log.LogBuffer]) and is the record that survives the device's logcat
 * rolling over. In a report taken after a 3-hour session, **499 of those 500 entries were two
 * sentences** - `Rewritten playlist served` and `Passthrough served` - and the ring reached back
 * exactly 24 minutes. The other 2 hours 37 minutes, including the playlist load, the guide load and
 * anything at all that went wrong, had been pushed out by a proxy reporting every four seconds that
 * it was still fine. The second report, over 40 minutes, was 394 of 500 the same way.
 *
 * Neither line can simply be deleted: both were added because a field capture of a failing cast was
 * unreadable without them, and the question they answer - did the receiver ever fetch anything, and
 * is it still fetching - is the first one anybody asks. What they do not need is to answer it once
 * per request. A count and a byte total per window answers it just as well, in one line, and adds a
 * rate: 15 playlists in 60 seconds is a receiver polling normally, and 1 is a receiver that has
 * nearly stopped.
 *
 * What is **not** rolled up is anything abnormal - a playlist that came back with nothing to
 * rewrite, a transfer cut short, a segment that delivered no bytes. Those keep their own line, with
 * their own resource id, because there the individual case is the whole point. See the call sites in
 * [com.uacastplayer.data.cast.ProxyServer].
 *
 * The first serve of a session is reported immediately rather than waited for, so the report shows
 * the moment the receiver first fetched even when the session is over in seconds. Thereafter the
 * pending window is emitted by the next serve that finds it expired, or by [flush] when the server
 * stops. A proxy that stalls and is never stopped therefore leaves its last partial window
 * unrecorded - which costs at most [windowMillis] of counts, and the gap between the last line and
 * the report's own timestamp is itself the evidence of the stall.
 *
 * Locked rather than left racy, unlike [com.uacastplayer.log.RepeatedNoteFilter]: serves arrive on
 * several of [com.uacastplayer.data.cast.ProxyHttpServer]'s pool threads at once, and a lost count
 * is a summary that quietly understates what happened. At a few serves a second the lock costs
 * nothing.
 */
class ProxyServeRollup(private val windowMillis: Long = WINDOW_MILLIS) {

    private val lock = ReentrantLock()
    private var windowStartMillis: Long? = null
    private var playlists = 0
    private var segments = 0
    private var bytes = 0L

    fun playlistServed(nowMillis: Long): ProxyServeSummary? = record(nowMillis) { playlists++ }

    fun segmentServed(byteCount: Long, nowMillis: Long): ProxyServeSummary? = record(nowMillis) {
        segments++
        bytes += byteCount
    }

    /** Whatever the open window holds, or null if nothing has been served since the last one. Call
     * when the server stops, so the final window is not lost with it. */
    fun flush(nowMillis: Long): ProxyServeSummary? = lock.withLock {
        if (playlists == 0 && segments == 0) null else take(nowMillis)
    }

    private fun record(nowMillis: Long, count: () -> Unit): ProxyServeSummary? = lock.withLock {
        val start = windowStartMillis
        count()
        when {
            start == null -> take(nowMillis)
            nowMillis - start >= windowMillis -> take(nowMillis)
            else -> null
        }
    }

    /** Empties the window and starts the next one here, so windows abut rather than drift. */
    private fun take(nowMillis: Long): ProxyServeSummary {
        val summary = ProxyServeSummary(
            playlists = playlists,
            segments = segments,
            bytes = bytes,
            windowMillis = windowStartMillis?.let { nowMillis - it } ?: 0L,
        )
        playlists = 0
        segments = 0
        bytes = 0L
        windowStartMillis = nowMillis
        return summary
    }

    companion object {
        /**
         * One minute, chosen against the measured cadence rather than picked round. A receiver
         * polls the playlist every four seconds and pulls a segment every ten, so a minute holds
         * roughly 15 and 6 of them - enough that the rate is readable in a single line, and short
         * enough that a stall shows up as a gap of a minute rather than of ten. At this rate the
         * 500-entry ring covers about eight hours of casting instead of twenty-four minutes.
         */
        const val WINDOW_MILLIS = 60_000L
    }
}
