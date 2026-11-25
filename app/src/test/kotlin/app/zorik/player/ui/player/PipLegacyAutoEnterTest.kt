package app.zorik.player.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.graphics.Rect
import androidx.media3.common.VideoSize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Android 8-11 have picture-in-picture but no `setAutoEnterEnabled`, so Home during fullscreen
 * playback used to just pause the video. [PipController.enterOnUserLeave] is the stand-in the
 * activity calls from `onUserLeaveHint`; it must follow the same rule as API 31+ auto-enter.
 */
@RunWith(RobolectricTestRunner::class)
class PipLegacyAutoEnterTest {
    private val host = Robolectric.buildActivity(PipEnteringActivity::class.java).setup()
    private val other = Robolectric.buildActivity(PipEnteringActivity::class.java).setup()
    private val video = VideoSize(1920, 1080)

    @After fun tearDown() {
        PipController.disableAutoEnter(host.get(), legacyApi = true)
        PipController.disableAutoEnter(other.get(), legacyApi = true)
        host.pause().stop().destroy()
        other.pause().stop().destroy()
    }

    @Test
    fun homeDuringFullscreenPlaybackEntersPictureInPicture() {
        val activity = host.get()
        PipController.syncParams(activity, video, Rect(0, 0, 1920, 1080), autoEnter = true, legacyApi = true)

        assertTrue(PipController.enterOnUserLeave(activity, legacyApi = true))
        assertEquals(1, activity.entries)
    }

    @Test
    fun pausedOrErroredPlaybackDoesNotEnter() {
        val activity = host.get()
        PipController.syncParams(activity, video, null, autoEnter = true, legacyApi = true)
        PipController.syncParams(activity, video, null, autoEnter = false, legacyApi = true)

        assertFalse(PipController.enterOnUserLeave(activity, legacyApi = true))
        assertEquals(0, activity.entries)
    }

    @Test
    fun collapsingThePlayerRevokesTheRequest() {
        val activity = host.get()
        PipController.syncParams(activity, video, null, autoEnter = true, legacyApi = true)
        PipController.disableAutoEnter(activity, legacyApi = true)

        assertFalse(PipController.enterOnUserLeave(activity, legacyApi = true))
    }

    @Test
    fun aRequestRecordedForAnotherActivityIsIgnored() {
        PipController.syncParams(other.get(), video, null, autoEnter = true, legacyApi = true)

        assertFalse(PipController.enterOnUserLeave(host.get(), legacyApi = true))
        assertEquals(0, host.get().entries)
    }

    @Test
    fun fromApi31TheSystemAutoEnterOwnsIt() {
        val activity = host.get()
        PipController.syncParams(activity, video, null, autoEnter = true, legacyApi = true)

        assertFalse(PipController.enterOnUserLeave(activity, legacyApi = false))
        assertEquals(0, activity.entries)
    }
}

class PipEnteringActivity : Activity() {
    var entries = 0
        private set

    override fun enterPictureInPictureMode(params: PictureInPictureParams): Boolean {
        entries++
        return true
    }

    override fun setPictureInPictureParams(params: PictureInPictureParams) = Unit
}
