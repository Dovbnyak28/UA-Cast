package com.uacastplayer.epg

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The parser side of [EpgRetentionPolicy]: a guide arrives with days of finished broadcasts in it,
 * and they used to be kept - counting against the cap, and so evicting the listings of every
 * channel that appears late in the file.
 */
class XmlTvParserRetentionTest {

    private val kyiv = ZoneId.of("Europe/Kyiv")

    /** `20260810200000 +0300` - the format every XMLTV feed writes. */
    private fun stamp(day: Int, hour: Int): String = "202608%02d%02d0000 +0300".format(day, hour)

    private fun millis(day: Int, hour: Int): Long =
        ZonedDateTime.of(2026, 8, day, hour, 0, 0, 0, kyiv).toInstant().toEpochMilli()

    private val feed = """
        <tv>
          <channel id="one"><display-name>One</display-name></channel>
          <programme channel="one" start="${stamp(10, 20)}" stop="${stamp(10, 21)}">
            <title>Last night</title>
          </programme>
          <programme channel="one" start="${stamp(11, 8)}" stop="${stamp(11, 9)}">
            <title>This morning</title>
          </programme>
          <programme channel="one" start="${stamp(11, 21)}" stop="${stamp(11, 22)}">
            <title>Tonight</title>
          </programme>
        </tv>
    """.trimIndent()

    private fun parse(xml: String, keepFrom: Long, keepUntil: Long = Long.MAX_VALUE): XmlTvParseResult =
        XmlTvParser.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), keepFrom, keepUntil)

    @Test
    fun yesterdayIsDroppedAndTodayIsKeptWhole() {
        val keepFrom = EpgRetentionPolicy.keepFrom(millis(day = 11, hour = 14), kyiv)
        val titles = parse(feed, keepFrom).programmes.map { it.title }
        assertEquals(listOf("This morning", "Tonight"), titles)
    }

    /**
     * The reason this is done in `startElement`: a title nobody will read must never become a
     * String. Proven by the one case where building it would show - a title long enough to be
     * truncated, on a programme that is dropped - which must not appear in the result at all.
     */
    @Test
    fun aDroppedProgrammeContributesNothingAtAll() {
        val keepFrom = EpgRetentionPolicy.keepFrom(millis(day = 11, hour = 14), kyiv)
        val result = parse(feed, keepFrom)
        assertTrue(result.programmes.none { it.title == "Last night" })
        assertEquals(1, result.channels.size)
    }

    /**
     * Dropping the past is not truncation. Raising the flag here would have put a permanent
     * "your guide is incomplete" warning in Settings for every user of every feed that carries
     * history - a warning about nothing, which they could neither act on nor dismiss.
     */
    @Test
    fun droppingThePastIsNotReportedAsAnIncompleteGuide() {
        val keepFrom = EpgRetentionPolicy.keepFrom(millis(day = 11, hour = 14), kyiv)
        val result = parse(feed, keepFrom)
        assertFalse(result.programmeLimitExceeded)
        assertFalse(result.channelLimitExceeded)
    }

    /** No clock, no cutoff: the whole feed, exactly as every existing caller still gets it. */
    @Test
    fun withoutACutoffTheWholeFeedSurvives() {
        assertEquals(3, parse(feed, keepFrom = 0L).programmes.size)
    }

    /** A feed with no stop time is judged on its start, which is what [EpgProgramme] uses for its
     * stop as well - so the two agree rather than one keeping what the other would discard. */
    @Test
    fun aProgrammeWithNoStopTimeIsJudgedOnItsStart() {
        val xml = """
            <tv>
              <channel id="one"><display-name>One</display-name></channel>
              <programme channel="one" start="${stamp(10, 20)}"><title>Last night</title></programme>
              <programme channel="one" start="${stamp(11, 21)}"><title>Tonight</title></programme>
            </tv>
        """.trimIndent()
        val keepFrom = EpgRetentionPolicy.keepFrom(millis(day = 11, hour = 14), kyiv)
        assertEquals(listOf("Tonight"), parse(xml, keepFrom).programmes.map { it.title })
    }

    /**
     * The far end of the window, and the report that produced it: a 311-channel playlist whose
     * guide carried 4052 channels and stopped dead on [XmlTvParser.MAX_PROGRAMMES], leaving the
     * channels at the end of the file with nothing. Dropping the past had not been enough, because
     * nothing dropped the far future - feeds carry about eight days, and every screen in this app
     * shows one.
     */
    @Test
    fun nextWeekIsDroppedTheSameWayLastNightIs() {
        val xml = """
            <tv>
              <channel id="one"><display-name>One</display-name></channel>
              <programme channel="one" start="${stamp(11, 21)}" stop="${stamp(11, 22)}">
                <title>Tonight</title>
              </programme>
              <programme channel="one" start="${stamp(13, 21)}" stop="${stamp(13, 22)}">
                <title>The day after tomorrow</title>
              </programme>
              <programme channel="one" start="${stamp(18, 21)}" stop="${stamp(18, 22)}">
                <title>Next week</title>
              </programme>
            </tv>
        """.trimIndent()
        val now = millis(day = 11, hour = 14)

        val result = parse(
            xml,
            keepFrom = EpgRetentionPolicy.keepFrom(now, kyiv),
            keepUntil = EpgRetentionPolicy.keepUntil(now, kyiv),
        )

        assertEquals(listOf("Tonight", "The day after tomorrow"), result.programmes.map { it.title })
    }

    /** Same reasoning as dropping the past: nothing a viewer could have reached was lost, so this
     * must not raise the flag that puts "your guide is incomplete" in front of them. */
    @Test
    fun droppingTheFarFutureIsNotReportedAsAnIncompleteGuideEither() {
        val now = millis(day = 11, hour = 14)

        val result = parse(
            feed,
            keepFrom = EpgRetentionPolicy.keepFrom(now, kyiv),
            keepUntil = EpgRetentionPolicy.keepUntil(now, kyiv),
        )

        assertFalse(result.programmeLimitExceeded)
    }
}
