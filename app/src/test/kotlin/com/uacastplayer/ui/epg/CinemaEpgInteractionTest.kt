package com.uacastplayer.ui.epg

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import com.uacastplayer.epg.EpgData
import com.uacastplayer.epg.EpgIndex
import com.uacastplayer.epg.EpgChannel
import com.uacastplayer.playlist.M3uChannel
import org.junit.Assert.assertTrue
import com.uacastplayer.epg.DayScheduleBuilder
import com.uacastplayer.epg.EpgProgramme
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the real guide presentation without network or a separate media player. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w320dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class CinemaEpgInteractionTest {
    @get:Rule val rule = createComposeRule()

    private val dayStart = Instant.parse("2026-11-14T00:00:00Z").toEpochMilli()
    private val hour = 3_600_000L
    private val now = dayStart + 9 * hour + hour / 2

    @Test @Config(qualifiers = "en-w640dp-h320dp-land-xhdpi")
    fun shortLandscapeKeepsTheCurrentTitleFullyReadableAtTwoHundredPercent() {
        assertShortGuideReadable()
    }

    @Test @Config(qualifiers = "en-w640dp-h320dp-land-xhdpi")
    fun shortWindowRemainsReadableWhenLegacyConfigurationReportsMoreHeight() {
        assertShortGuideReadable(legacyHeight = 400)
    }

    private fun assertShortGuideReadable(legacyHeight: Int? = null) {
        val title = "A quiet journey through the mountains"
        val channel = M3uChannel("Cinema — a long channel name HD", "https://example.test/live", tvgId = "cinema")
        val data = EpgData(EpgIndex(listOf(EpgChannel("cinema", listOf(channel.displayName), null))),
            mapOf("cinema" to listOf(EpgProgramme("cinema", now - hour / 2, now + hour / 2, title))))
        rule.setContent {
            val configuration = Configuration(LocalConfiguration.current)
            legacyHeight?.let { configuration.screenHeightDp = it }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, 2f)) {
            UaCastTheme(AppTheme.CINEMA) { EpgGuideSheet(channel, data, now, {}) }
        } }
        val node = rule.onNode(hasText(title) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        val listBounds = rule.onNode(hasScrollAction()).getUnclippedBoundsInRoot()
        assertTrue("EPG chrome leaves insufficient reading space", listBounds.bottom - listBounds.top >= 120.dp)
        val bounds = node.getUnclippedBoundsInRoot()
        val height = (bounds.bottom - bounds.top).value * rule.density.density
        assertTrue("Current title is clipped by fixed guide chrome",
            node.fetchSemanticsNode().boundsInRoot.height + 1 >= height)
    }

    @Test fun nowReturnsToTheCurrentProgrammeAfterBrowsingEarlierProgrammes() {
        render()
        rule.onNodeWithText("Morning cinema").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToIndex(0)
        rule.onNodeWithText("Programme 0").assertIsDisplayed()

        rule.onNodeWithText("Now").performClick()

        rule.onNodeWithText("Morning cinema").assertIsDisplayed()
    }

    @Test fun currentProgrammeShowsBothStartAndEndTime() {
        render()

        rule.onNode(hasText("09:00", substring = true) and hasText("10:00", substring = true))
            .assertIsDisplayed()
    }

    @Test fun programmeProgressExposesItsValueWithoutASeekAction() {
        render()
        val halfFinished = SemanticsMatcher("programme progress is 50 percent") { node ->
            node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.current == 0.5f
        }
        val editableProgress = SemanticsMatcher("has an interactive seek action") { node ->
            node.config.contains(SemanticsActions.SetProgress)
        }

        rule.onNode(halfFinished).assertIsDisplayed()
        rule.onAllNodes(editableProgress).assertCountEquals(0)
    }

    private fun render() {
        val programmes = (0 until 24).map { index ->
            EpgProgramme(
                channelId = "cinema",
                startMillis = dayStart + index * hour,
                stopMillis = dayStart + (index + 1) * hour,
                title = if (index == 9) "Morning cinema" else "Programme $index",
            )
        }
        val schedule = DayScheduleBuilder.build(programmes, now, ZoneOffset.UTC)
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Box(Modifier.fillMaxWidth().height(320.dp)) {
                    ScheduleList(schedule, now, ZoneOffset.UTC)
                }
            }
        }
    }
}
