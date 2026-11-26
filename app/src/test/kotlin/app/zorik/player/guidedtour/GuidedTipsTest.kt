package app.zorik.player.guidedtour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tips' and the intro's own integrity.
 *
 * Both are data, which is what makes them cheap to edit - and what makes a slip in one of them
 * silent. A misspelled target key does not fail to compile; it produces a tip that simply never
 * appears, and nobody notices because "not shown yet" is also what a tip looks like most of the time.
 */
class GuidedTipsTest {

    /** Ids are what [GuidedTourStorage.guidedTipsSeen] stores. Two tips sharing one would mean
     * dismissing either hides both. */
    @Test
    fun everyTipHasItsOwnId() {
        assertEquals(GuidedTip.entries.size, GuidedTip.entries.map { it.id }.distinct().size)
    }

    /** Stored, so renaming one would show an existing user a tip they already dismissed. */
    @Test
    fun storedIdsStayAsShipped() {
        assertEquals(listOf("search", "player", "cast"), GuidedTip.entries.map { it.id })
    }

    @Test
    fun everyTipPointsAtAKeySomethingCanActuallyRegister() {
        val unknown = GuidedTip.entries.map { it.targetKey }.filterNot { it in GuidedTourKeys.ALL }

        assertTrue("tips point at unregisterable keys: $unknown", unknown.isEmpty())
    }

    /** Short is the point of a tip; a fourth line is a paragraph in disguise. */
    @Test
    fun everyTipSaysTwoOrThreeThings() {
        GuidedTip.entries.forEach { tip ->
            assertTrue("tip '${tip.id}' has ${tip.points.size} lines", tip.points.size in 2..3)
        }
    }

    /** A zero here is a blank line, and `stringResource(0)` throws rather than degrading. */
    @Test
    fun everyTipAndPageCarriesRealStringResources() {
        GuidedTip.entries.forEach { tip ->
            assertTrue("tip '${tip.id}' has no title", tip.titleRes != 0)
            tip.points.forEach { assertTrue("tip '${tip.id}' has an empty line", it.textRes != 0) }
        }
        IntroPage.entries.forEach { page ->
            assertTrue("page $page has no title", page.titleRes != 0)
            assertTrue("page $page has no text", page.bodyRes != 0)
        }
    }

    /** The search field lives under the player; the player's controls do not exist without it. */
    @Test
    fun onlyThePlayerTipsBelongToThePlayer() {
        assertEquals(setOf(GuidedTip.PLAYER, GuidedTip.CAST), GuidedTip.entries.filter { it.inPlayer }.toSet())
    }

    /**
     * The intro and the stored version move together. A changed intro without a bump means existing
     * users never see the difference; a bump without one shows them the same slides again.
     */
    @Test
    fun theVersionIsBumpedWheneverTheIntroChanges() {
        assertEquals(
            "GuidedTourVersion.CURRENT must be bumped when IntroPage changes",
            2,
            GuidedTourVersion.CURRENT,
        )
        assertEquals(listOf(IntroPage.PLAYLIST, IntroPage.WATCH, IntroPage.CAST), IntroPage.entries)
    }
}
