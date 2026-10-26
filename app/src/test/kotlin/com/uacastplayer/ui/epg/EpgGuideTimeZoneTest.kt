package com.uacastplayer.ui.epg

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.R
import com.uacastplayer.epg.EpgChannel
import com.uacastplayer.epg.EpgData
import com.uacastplayer.epg.EpgIndex
import com.uacastplayer.epg.EpgProgramme
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import java.time.Instant
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Change the device's zone without replacing the channel, feed or open guide. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class EpgGuideTimeZoneTest {
    @get:Rule val rule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val owner = GuideLifecycleOwner()
    private lateinit var previousZone: TimeZone
    private val visible = mutableStateOf(true)
    private val now = mutableStateOf(epoch("2026-01-01T22:30:00Z"))
    private val channel = M3uChannel("Channel", "https://unused.example.test", tvgId = "ch")
    private val data = EpgData(
        EpgIndex(listOf(EpgChannel("ch", listOf("Channel"), null))),
        mapOf("ch" to listOf(
            EpgProgramme("ch", epoch("2026-01-01T12:00:00Z"), epoch("2026-01-01T13:00:00Z"), "UTC day only"),
            EpgProgramme("ch", epoch("2026-01-01T23:00:00Z"), epoch("2026-01-02T00:00:00Z"), "Near midnight"),
        )),
    )

    @Before fun useUtc() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreDeviceZone() { TimeZone.setDefault(previousZone) }

    @Test fun timezoneBroadcastRefreshesTimesAndTheLocalDayInAnOpenGuide() {
        show()
        assertUtc()
        change {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"))
            context.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED).putExtra("time-zone", "Europe/Kyiv"))
        }
        assertKyiv()
    }

    @Test fun returningFromBackgroundRefreshesAZoneChangedWhileTheGuideWasStopped() {
        show()
        assertUtc()
        change { owner.registry.currentState = Lifecycle.State.CREATED }
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"))
        // No broadcast is delivered: restarting the visible owner must reread the device setting.
        change { owner.registry.currentState = Lifecycle.State.RESUMED }
        assertKyiv()
    }

    @Test fun aClockTickCannotKeepTheGuideInAnObsoleteZone() {
        show()
        assertUtc()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"))
        change {
            context.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED).putExtra("time-zone", "Europe/Kyiv"))
            now.value += 30_000
        }
        assertKyiv()
    }

    @Test fun closingAndReopeningUsesTheNewZoneWithoutChangingTheFeed() {
        show()
        assertUtc()
        change { visible.value = false }
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"))
        change { visible.value = true }
        assertKyiv()
    }

    @Test fun repeatedBackgroundingAndClosingDoNotRetainOrDuplicateTheReceiver() {
        val baseline = receiverCount()
        show()
        assertEquals(baseline + 1, receiverCount())
        repeat(10) {
            change { owner.registry.currentState = Lifecycle.State.CREATED }
            assertEquals("A stopped guide must not listen", baseline, receiverCount())
            change { owner.registry.currentState = Lifecycle.State.RESUMED }
            assertEquals("Only one receiver belongs to the visible guide", baseline + 1, receiverCount())
        }
        change { visible.value = false }
        assertEquals("Closing the guide releases its receiver", baseline, receiverCount())
    }

    @Test fun broadcastExtrasCannotOverrideTheActualDeviceZone() {
        show()
        assertUtc()
        change {
            context.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED).putExtra("time-zone", "invalid/zone"))
        }
        assertUtc()
    }

    private fun show() {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    if (visible.value) EpgGuideSheet(channel, data, now.value, onDismiss = {})
                }
            }
        }
    }

    private fun assertUtc() {
        rule.onNodeWithText(context.getString(R.string.epg_timezone, "UTC")).assertIsDisplayed()
        rule.onNodeWithText("UTC day only").assertIsDisplayed()
        rule.onNodeWithText("23:00").assertIsDisplayed()
    }

    private fun assertKyiv() {
        rule.onNodeWithText(context.getString(R.string.epg_timezone, "Europe/Kyiv")).assertIsDisplayed()
        rule.onNodeWithText("UTC day only").assertDoesNotExist()
        rule.onNodeWithText("Near midnight").assertIsDisplayed()
        rule.onNodeWithText("01:00").assertIsDisplayed()
    }

    private fun change(action: () -> Unit) {
        rule.runOnIdle {
            action()
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
        }
        rule.waitForIdle()
    }

    private fun epoch(value: String) = Instant.parse(value).toEpochMilli()

    private fun receiverCount() = shadowOf(context).registeredReceivers.count {
        it.intentFilter.hasAction(Intent.ACTION_TIMEZONE_CHANGED)
    }
}

private class GuideLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry
}
