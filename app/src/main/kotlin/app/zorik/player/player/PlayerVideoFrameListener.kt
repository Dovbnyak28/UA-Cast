@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package app.zorik.player.player

import android.os.Build
import android.os.Trace
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.analytics.AnalyticsListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** A rendered frame is different from READY/playWhenReady. Never acknowledge an abandoned item's frame. */
internal class PlayerVideoFrameListener(private val state: MutableStateFlow<PlayerUiState>) : AnalyticsListener {
    private var expectedUri: android.net.Uri? = null
    private var tracing = false
    private var closed = false
    private val traceCookie = System.identityHashCode(this)

    override fun onMediaItemTransition(eventTime: AnalyticsListener.EventTime, item: MediaItem?, reason: Int) {
        if (closed) return
        endTrace()
        expectedUri = item?.localConfiguration?.uri
        state.update { it.copy(hasRenderedVideoFrame = false) }
        if (expectedUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Trace.isEnabled()) {
            Trace.beginAsyncSection(FIRST_VIDEO_FRAME_TRACE, traceCookie)
            tracing = true
        }
    }

    override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
        if (closed || expectedUri == null || eventTime.windowIndex !in 0 until eventTime.timeline.windowCount) return
        val uri = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window())
            .mediaItem.localConfiguration?.uri
        if (uri != expectedUri) return
        state.update { it.copy(hasRenderedVideoFrame = true) }
        endTrace()
    }

    fun close() { closed = true; expectedUri = null; endTrace() }

    private fun endTrace() {
        if (tracing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Trace.endAsyncSection(FIRST_VIDEO_FRAME_TRACE, traceCookie)
        }
        tracing = false
    }
}

internal const val FIRST_VIDEO_FRAME_TRACE = "ZorikFirstVideoFrame"
