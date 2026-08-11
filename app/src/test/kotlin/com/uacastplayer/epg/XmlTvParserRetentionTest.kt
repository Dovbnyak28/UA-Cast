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

    private fun parse(xml: String, keepFrom: Long): XmlTvParseResult =
        XmlTvParser.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), keepFrom)

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
}
