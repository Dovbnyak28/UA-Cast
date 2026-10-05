package com.uacastplayer.ui.epg

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uacastplayer.R
import com.uacastplayer.epg.EpgChannel
import com.uacastplayer.epg.EpgData
import com.uacastplayer.epg.EpgIndex
import com.uacastplayer.epg.EpgProgramme
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import java.time.Instant
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Changes only this test process's default zone, never the device setting or user EPG files. */
@RunWith(AndroidJUnit4::class)
class EpgTimeZoneInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val owner = InstrumentedGuideOwner()
    private val visible = mutableStateOf(true)
    private lateinit var previousZone: TimeZone
    private val now = Instant.parse("2026-01-01T22:30:00Z").toEpochMilli()
    private val hour = 3_600_000L
    private val midnight = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
    private val data = EpgData(
        EpgIndex(listOf(EpgChannel("ch", listOf("Channel"), null))),
        mapOf("ch" to listOf(
            EpgProgramme("ch", midnight + 12 * hour, midnight + 13 * hour, "UTC day only"),
            EpgProgramme("ch", midnight + 23 * hour, midnight + 24 * hour, "Near midnight"),
        )),
    )

    @Before fun useUtc() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreProcessZone() { TimeZone.setDefault(previousZone) }

    @Test fun backgroundResumeRefreshesTheSameOpenGuide() {
        show()
        assertZone("UTC", "23:00")
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        rule.runOnIdle {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Athens"))
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        assertZone("Europe/Athens", "01:00")
        rule.onNodeWithText("UTC day only").assertDoesNotExist()
        rule.onNodeWithText("Near midnight").assertIsDisplayed()
    }

    @Test fun tenStopResumeCyclesAndReopeningRemainUsable() {
        show()
        assertZone("UTC", "23:00")
        repeat(10) { index ->
            rule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
            val zone = if (index % 2 == 0) "Europe/Athens" else "UTC"
            rule.runOnIdle {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                owner.registry.currentState = Lifecycle.State.RESUMED
            }
            assertZone(zone, if (zone == "UTC") "23:00" else "01:00")
        }
        rule.runOnIdle { visible.value = false }
        rule.waitForIdle()
        rule.runOnIdle {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Athens"))
            visible.value = true
        }
        assertZone("Europe/Athens", "01:00")
    }

    private fun show() {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    if (visible.value) EpgGuideSheet(
                        M3uChannel("Channel", "https://unused.example.test", tvgId = "ch"), data, now, {},
                    )
                }
            }
        }
    }

    private fun assertZone(zone: String, time: String) {
        rule.onNodeWithText(rule.activity.getString(R.string.epg_timezone, zone)).assertIsDisplayed()
        rule.onNodeWithText(time).assertIsDisplayed()
    }
}

private class InstrumentedGuideOwner : LifecycleOwner {
    val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry
}
