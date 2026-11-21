package com.uacastplayer.ui.epg

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.uacastplayer.epg.DayScheduleBuilder
import com.uacastplayer.epg.EpgProgramme
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The day's lineup as it is actually drawn, against feeds that are not tidy.
 *
 * This is the one screen in the app that renders raw XMLTV rows in a list, and a `LazyColumn` is
 * strict about its keys in a way nothing else here is: two items with the same key is not a
 * duplicate row, it is an `IllegalArgumentException` thrown out of composition. Nothing between the
 * feed and this list ever promised the keys would differ - [com.uacastplayer.epg.XmlTvParser] keeps
 * every `<programme>` it is given, and `EpgRepository` only sorts them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w320dp-h480dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class ScheduleListTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone: ZoneId = ZoneOffset.UTC

    /** 2026-01-01T00:00:00Z, the same anchor `DayScheduleBuilderTest` uses. */
    private val dayStart = 1_767_225_600_000L
    private val hour = 60 * 60 * 1000L

    private fun programme(title: String, startMillis: Long, stopMillis: Long) =
        EpgProgramme(channelId = "ch", startMillis = startMillis, stopMillis = stopMillis, title = title)

    private fun render(programmes: List<EpgProgramme>, nowMillis: Long) {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                ScheduleList(
                    schedule = DayScheduleBuilder.build(programmes, nowMillis, zone),
                    nowMillis = nowMillis,
                    zoneId = zone,
                )
            }
        }
    }

    @Test
    fun theDayIsDrawnInOrder() {
        render(
            listOf(
                programme("Ранкові новини", dayStart + hour, dayStart + 2 * hour),
                programme("Фільм", dayStart + 2 * hour, dayStart + 4 * hour),
                programme("Вечірні новини", dayStart + 5 * hour, dayStart + 6 * hour),
            ),
            nowMillis = dayStart + 3 * hour,
        )

        composeRule.onNodeWithText("Ранкові новини").assertIsDisplayed()
        composeRule.onNodeWithText("Фільм").assertIsDisplayed()
        composeRule.onNodeWithText("Вечірні новини").assertIsDisplayed()
    }

    /**
     * Two `<programme>` entries with the same start on the same channel.
     *
     * Aggregated feeds - the kind that merge several providers into one file, which is what the
     * playlists this app is pointed at tend to advertise - carry these routinely, and nothing
     * upstream of the guide sheet removes them. The list keyed its rows on `startMillis` alone, so
     * the second one collided with the first and opening the guide for that channel threw.
     */
    @Test
    fun aChannelListedTwiceAtTheSameTimeStillOpens() {
        render(
            listOf(
                programme("Той самий фільм", dayStart + 5 * hour, dayStart + 6 * hour),
                programme("Той самий фільм", dayStart + 5 * hour, dayStart + 6 * hour),
            ),
            nowMillis = dayStart + hour,
        )

        assertEquals(2, composeRule.onAllNodesWithText("Той самий фільм").fetchSemanticsNodes().size)
    }

    /** The same collision one bucket over: a duplicated programme that has already finished. */
    @Test
    fun aDuplicatedProgrammeInThePastStillOpens() {
        render(
            listOf(
                programme("Повтор", dayStart, dayStart + hour),
                programme("Повтор", dayStart, dayStart + hour),
            ),
            nowMillis = dayStart + 3 * hour,
        )

        assertEquals(2, composeRule.onAllNodesWithText("Повтор").fetchSemanticsNodes().size)
    }

    /**
     * And across buckets: the programme on air and one already finished sharing a start.
     *
     * Reachable from a feed that gives a repeat no stop time - [EpgProgramme] then falls back to the
     * start, which puts a zero-length row in the past at the exact instant a real programme begins.
     */
    @Test
    fun aFinishedProgrammeSharingTheCurrentOnesStartStillOpens() {
        render(
            listOf(
                programme("Анонс", dayStart + hour, dayStart + hour),
                programme("Концерт", dayStart + hour, dayStart + 4 * hour),
            ),
            nowMillis = dayStart + 2 * hour,
        )

        composeRule.onNodeWithText("Анонс").assertIsDisplayed()
        composeRule.onNodeWithText("Концерт").assertIsDisplayed()
    }
}
