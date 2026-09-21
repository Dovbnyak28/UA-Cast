package com.uacastplayer.epg

import com.uacastplayer.playlist.M3uChannel
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How much guide a viewer actually has, as opposed to how big the feed is.
 *
 * The two are not close. A diagnostics report from the field carried a 311-channel playlist against
 * a 4052-channel guide, and it was the guide's total that decided that phone's performance tier -
 * so 92% of the number turning its channel logos off was channels its owner did not have.
 */
class EpgWorkloadPolicyTest {

    private fun channel(name: String, tvgId: String? = null) =
        M3uChannel(displayName = name, streamUrl = "http://example.test/$name", tvgId = tvgId)

    private fun epgChannel(id: String, name: String = id) =
        EpgChannel(id = id, displayNames = listOf(name), iconUrl = null)

    private fun programmes(channelId: String, count: Int): List<EpgProgramme> = (1..count).map {
        EpgProgramme(
            channelId = channelId,
            startMillis = it * 1000L,
            stopMillis = it * 1000L + 500,
            title = "P$it",
        )
    }

    private fun data(epgChannels: List<EpgChannel>, perChannel: Int): EpgData = EpgData(
        index = EpgIndex(epgChannels),
        programmesByChannelId = epgChannels.associate { it.id to programmes(it.id, perChannel) },
        truncation = EpgTruncation(channelsDropped = false, programmesDropped = false),
    )

    @Test
    fun `no guide at all counts as nothing`() {
        assertEquals(0, EpgWorkloadPolicy.programmesFor(null, listOf(channel("One"))))
    }

    @Test
    fun `an empty playlist counts as nothing, however large the feed`() {
        val feed = data((1..4000).map { epgChannel("ch$it") }, perChannel = 50)

        assertEquals(0, EpgWorkloadPolicy.programmesFor(feed, emptyList()))
    }

    /**
     * The reported shape, in miniature: a small playlist inside a huge feed. The old measurement
     * returned the feed's total and put the device two tiers down for it.
     */
    @Test
    fun `only the playlist's own channels are counted`() {
        val feed = data((1..1000).map { epgChannel("ch$it") }, perChannel = 60)
        val playlist = (1..10).map { channel("Channel $it", tvgId = "ch$it") }

        assertEquals(10 * 60, EpgWorkloadPolicy.programmesFor(feed, playlist))
    }

    /** Providers list the same channel three times at three qualities on one `tvg-id`. Counting per
     * playlist row would triple a small playlist's guide and push it over a threshold on nothing
     * but the provider's naming. */
    @Test
    fun `quality variants sharing one tvg-id are counted once`() {
        val feed = data(listOf(epgChannel("one")), perChannel = 60)
        val playlist = listOf(
            channel("Channel HD", tvgId = "one"),
            channel("Channel FHD", tvgId = "one"),
            channel("Channel SD", tvgId = "one"),
        )

        assertEquals(60, EpgWorkloadPolicy.programmesFor(feed, playlist))
    }

    /** A playlist whose channels the guide does not carry has no guide, whatever the feed holds -
     * which is the honest answer, and the one that leaves such a device on its hardware tier. */
    @Test
    fun `channels the guide does not know contribute nothing`() {
        val feed = data((1..1000).map { epgChannel("ch$it") }, perChannel = 60)
        val playlist = listOf(channel("Nothing like it", tvgId = "absent"))

        assertEquals(0, EpgWorkloadPolicy.programmesFor(feed, playlist))
    }

    /** Matching is [EpgIndex]'s, so a channel with no `tvg-id` still counts when its name resolves -
     * exactly when the user can see a guide for it. */
    @Test
    fun `a channel matched by name counts too`() {
        val feed = data(listOf(epgChannel("id-1", name = "Перший")), perChannel = 42)

        assertEquals(42, EpgWorkloadPolicy.programmesFor(feed, listOf(channel("Перший"))))
    }

    /**
     * This runs on the frame that applies a load, once per channel in the playlist, so it needs a
     * measured bound rather than a hope.
     *
     * Both paths through [EpgIndex.match] are measured, because they differ by a lot. Ten thousand
     * channels against a five-thousand-channel feed, on this JVM:
     *
     * - **2ms** when `tvg-id` resolves, which is `match`'s first attempt - a hash lookup that never
     *   reaches the three normalising attempts behind it.
     * - **12ms** when nothing resolves at all, which walks all four and pays NFKC plus a regex
     *   twice per channel. That is also the case where the answer is zero and no downgrade happens,
     *   so the worst cost and the least consequence coincide.
     *
     * Neither is the common case, and it is worth saying which is: measured against the real
     * playlists on the test device, **not one channel of 2863 carried a `tvg-id`**, so the answer
     * came from the normalised-name map on the third attempt - 4ms for that playlist, 2ms and 1ms
     * for the two smaller ones. Matching by name is what real playlists do here.
     *
     * The budget is an order of magnitude above the slowest of these on purpose: it is here to
     * catch a change that costs a multiple, not a few percent, and to stay quiet on a loaded
     * machine.
     */
    @Test
    fun `a large playlist against a large feed stays well inside a load frame`() {
        val feed = data((1..5000).map { epgChannel("ch$it") }, perChannel = 60)
        val byTvgId = (1..10_000).map { channel("Channel $it", tvgId = "ch${it % 5000 + 1}") }
        val unmatchable = (1..10_000).map { channel("Nothing like channel $it") }

        // Warm the JIT and the index's maps so what is measured is the work, not the first touch.
        EpgWorkloadPolicy.programmesFor(feed, byTvgId)
        EpgWorkloadPolicy.programmesFor(feed, unmatchable)

        val fast = measureTimeMillis { EpgWorkloadPolicy.programmesFor(feed, byTvgId) }
        val worst = measureTimeMillis { EpgWorkloadPolicy.programmesFor(feed, unmatchable) }

        assertTrue("tvg-id path took ${fast}ms for 10,000 channels", fast < BUDGET_MILLIS)
        assertTrue("no-match path took ${worst}ms for 10,000 channels", worst < BUDGET_MILLIS)
    }

    private companion object {
        /** Measured at 2ms and 12ms; see the test above for why this sits an order of magnitude up. */
        const val BUDGET_MILLIS = 250L
    }
}
