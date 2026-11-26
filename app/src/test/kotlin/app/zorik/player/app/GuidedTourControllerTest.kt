package app.zorik.player.app

import app.zorik.player.guidedtour.GuidedTip
import app.zorik.player.guidedtour.GuidedTourPhase
import app.zorik.player.guidedtour.GuidedTourStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory stand-in for the SharedPreferences values. */
private class FakeTourStorage(
    override var guidedTourCompleted: Boolean = false,
    override var guidedTourVersion: Int = 0,
    override var guidedTipsSeen: Set<String> = emptySet(),
) : GuidedTourStorage

/**
 * The intro's and the tips' whole decision surface: where Next and Back go, what closing records,
 * when the intro offers itself unasked, and which tips may still show.
 *
 * Most of these are about not annoying someone. An intro that reappeared after being skipped, or a
 * tip that came back after "Got it", is a feature that has stopped helping and started nagging - and
 * the only thing standing between the app and that is the values written here.
 */
class GuidedTourControllerTest {

    private fun controller(storage: GuidedTourStorage, version: Int = 2) =
        GuidedTourController(storage = storage, version = version)

    @Test
    fun nothingIsShowingUntilSomethingAsksForIt() {
        val controller = controller(FakeTourStorage())

        assertEquals(GuidedTourPhase.IDLE, controller.state.value.phase)
        assertFalse(controller.state.value.isVisible)
        assertNull(controller.state.value.tip)
    }

    @Test
    fun aFirstLaunchOpensTheIntroOnItsFirstPage() {
        val controller = controller(FakeTourStorage())

        controller.offerOnLaunch()

        assertEquals(GuidedTourPhase.INTRO, controller.state.value.phase)
        assertEquals(0, controller.state.value.introPage)
    }

    @Test
    fun nextWalksThePagesAndTheLastOneFinishesTheIntro() {
        val storage = FakeTourStorage()
        val controller = controller(storage)
        controller.offerOnLaunch()

        controller.introNext()
        assertEquals(1, controller.state.value.introPage)
        controller.introNext()
        assertEquals(2, controller.state.value.introPage)
        assertTrue(controller.state.value.isLastIntroPage)

        controller.introNext()

        assertEquals(GuidedTourPhase.IDLE, controller.state.value.phase)
        assertTrue(storage.guidedTourCompleted)
        assertEquals(2, storage.guidedTourVersion)
    }

    @Test
    fun backStepsAPageAndDoesNothingOnTheFirst() {
        val controller = controller(FakeTourStorage())
        controller.offerOnLaunch()
        controller.introNext()

        controller.introBack()
        assertEquals(0, controller.state.value.introPage)
        controller.introBack()

        assertEquals(0, controller.state.value.introPage)
        assertEquals(GuidedTourPhase.INTRO, controller.state.value.phase)
    }

    @Test
    fun aSwipeReportedPastTheEndIsCoercedToTheLastPage() {
        val controller = controller(FakeTourStorage())
        controller.offerOnLaunch()

        controller.goToIntroPage(9)

        assertEquals(2, controller.state.value.introPage)
    }

    @Test
    fun skippingIsRememberedExactlyLikeFinishing() {
        val storage = FakeTourStorage()
        controller(storage).apply {
            offerOnLaunch()
            finishIntro()
        }

        val nextLaunch = controller(storage)
        nextLaunch.offerOnLaunch()

        assertEquals(GuidedTourPhase.IDLE, nextLaunch.state.value.phase)
        assertTrue(nextLaunch.hasSeenTour.value)
    }

    @Test
    fun someoneWhoSawTheOldSevenStepTourIsShownTheNewIntroOnce() {
        val storage = FakeTourStorage(guidedTourCompleted = true, guidedTourVersion = 1)
        val controller = controller(storage)

        assertFalse(controller.hasSeenTour.value)
        controller.offerOnLaunch()

        assertEquals(GuidedTourPhase.INTRO, controller.state.value.phase)
    }

    @Test
    fun everyTipIsPendingOnAFreshInstallInPriorityOrder() {
        val controller = controller(FakeTourStorage())

        assertEquals(listOf(GuidedTip.SEARCH, GuidedTip.PLAYER, GuidedTip.CAST), controller.pendingTips.value)
    }

    @Test
    fun aTipWaitsForTheIntroToClose() {
        val controller = controller(FakeTourStorage())
        controller.offerOnLaunch()

        assertFalse(controller.showTip(GuidedTip.SEARCH))
        assertEquals(GuidedTourPhase.INTRO, controller.state.value.phase)

        controller.finishIntro()

        assertTrue(controller.showTip(GuidedTip.SEARCH))
        assertEquals(GuidedTip.SEARCH, controller.state.value.tip)
    }

    @Test
    fun onlyOneTipIsEverUp() {
        val controller = controller(FakeTourStorage(guidedTourCompleted = true, guidedTourVersion = 2))
        controller.showTip(GuidedTip.PLAYER)

        assertFalse(controller.showTip(GuidedTip.CAST))
        assertEquals(GuidedTip.PLAYER, controller.state.value.tip)
    }

    @Test
    fun aDismissedTipIsNeverOfferedAgain() {
        val storage = FakeTourStorage(guidedTourCompleted = true, guidedTourVersion = 2)
        val controller = controller(storage)
        controller.showTip(GuidedTip.PLAYER)

        controller.dismissTip()

        assertEquals(GuidedTourPhase.IDLE, controller.state.value.phase)
        assertEquals(setOf("player"), storage.guidedTipsSeen)
        assertEquals(listOf(GuidedTip.SEARCH, GuidedTip.CAST), controller.pendingTips.value)
        assertFalse(controller.showTip(GuidedTip.PLAYER))
        assertEquals(listOf(GuidedTip.SEARCH, GuidedTip.CAST), controller(storage).pendingTips.value)
    }

    @Test
    fun settingsBringsBackTheIntroAndEveryTip() {
        val storage = FakeTourStorage(
            guidedTourCompleted = true,
            guidedTourVersion = 2,
            guidedTipsSeen = setOf("search", "player", "cast"),
        )
        val controller = controller(storage)
        assertTrue(controller.pendingTips.value.isEmpty())

        controller.startFromSettings()

        assertEquals(GuidedTourPhase.INTRO, controller.state.value.phase)
        assertEquals(GuidedTip.entries, controller.pendingTips.value)
        assertTrue(storage.guidedTipsSeen.isEmpty())
    }

    @Test
    fun aTipInterruptedByAnOpenedFileComesBackLater() {
        val storage = FakeTourStorage(guidedTourCompleted = true, guidedTourVersion = 2)
        val controller = controller(storage)
        controller.showTip(GuidedTip.SEARCH)

        controller.interrupt()

        assertEquals(GuidedTourPhase.IDLE, controller.state.value.phase)
        assertTrue(storage.guidedTipsSeen.isEmpty())
        assertTrue(GuidedTip.SEARCH in controller.pendingTips.value)
    }

    @Test
    fun anIntroInterruptedByAnOpenedFileCountsAsSkipped() {
        val storage = FakeTourStorage()
        val controller = controller(storage)
        controller.offerOnLaunch()

        controller.interrupt()

        assertEquals(GuidedTourPhase.IDLE, controller.state.value.phase)
        assertTrue(storage.guidedTourCompleted)
    }

    @Test
    fun dismissAllLeavesNothingToShow() {
        val storage = FakeTourStorage()
        val controller = controller(storage)
        controller.offerOnLaunch()

        controller.dismissAll()

        assertEquals(GuidedTourPhase.IDLE, controller.state.value.phase)
        assertTrue(controller.pendingTips.value.isEmpty())
        assertTrue(storage.guidedTourCompleted)
        assertEquals(GuidedTip.entries.map(GuidedTip::id).toSet(), storage.guidedTipsSeen)
    }

    @Test
    fun dismissingWithNoTipUpRecordsNothing() {
        val storage = FakeTourStorage()
        val controller = controller(storage)

        controller.dismissTip()

        assertTrue(storage.guidedTipsSeen.isEmpty())
    }
}
