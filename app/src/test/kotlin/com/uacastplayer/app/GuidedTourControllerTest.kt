package com.uacastplayer.app

import com.uacastplayer.core.nav.BottomDestination
import com.uacastplayer.guidedtour.GuidedTourPhase
import com.uacastplayer.guidedtour.GuidedTourStep
import com.uacastplayer.guidedtour.GuidedTourStorage
import com.uacastplayer.guidedtour.GuidedTourTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory stand-in for the two SharedPreferences values. */
private class FakeTourStorage(
    override var guidedTourCompleted: Boolean = false,
    override var guidedTourVersion: Int = 0,
) : GuidedTourStorage

/**
 * The tour's whole decision surface: where Next and Back go, what Skip and Done record, and when the
 * tour offers itself unasked.
 *
 * Two of these are about not annoying someone. A tour that reappeared after being skipped, or after
 * being finished, is a feature that has stopped asking and started nagging - and the only thing
 * standing between the app and that is the pair of values written here.
 */
class GuidedTourControllerTest {

    private val steps = listOf(
        step("first", BottomDestination.HOME),
        step("second", BottomDestination.CHANNELS),
        step("third", BottomDestination.SETTINGS),
    )

    private fun step(id: String, destination: BottomDestination) = GuidedTourStep(
        id = id,
        titleRes = 1,
        descriptionRes = 2,
        target = GuidedTourTarget.Element("key_$id"),
        destination = destination,
    )

    private fun controller(storage: GuidedTourStorage, version: Int = 1) =
        GuidedTourController(storage = storage, steps = steps, version = version)

    @Test
    fun nothingIsShowingUntilSomethingAsksForIt() {
        val controller = controller(FakeTourStorage())

        assertEquals(GuidedTourPhase.IDLE, controller.state.value.phase)
        assertFalse(controller.state.value.isVisible)
        assertNull(controller.state.value.currentStep)
    }

    @Test
    fun aFirstLaunchOpensOnTheWelcomeCardRatherThanTheFirstStep() {
        val controller = controller(FakeTourStorage())

        controller.offerOnLaunch()

        assertEquals(GuidedTourPhase.WELCOME, controller.state.value.phase)
        // The welcome card has no step, so nothing is highlighted behind it.
        assertNull(controller.state.value.currentStep)
    }

    @Test
    fun nextWalksWelcomeThroughEveryStepAndStopsOnTheDoneCard() {
        val controller = controller(FakeTourStorage())
        controller.offerOnLaunch()

        controller.next()
        assertEquals(GuidedTourPhase.STEPS, controller.state.value.phase)
        assertEquals("first", controller.state.value.currentStep?.id)
        assertEquals(1, controller.state.value.stepNumber)

        controller.next()
        assertEquals("second", controller.state.value.currentStep?.id)
        controller.next()
        assertEquals("third", controller.state.value.currentStep?.id)

        controller.next()
        assertEquals(GuidedTourPhase.DONE, controller.state.value.phase)
    }

    /** The end of the list is where an off-by-one would show up as a crash on the user's screen. */
    @Test
    fun nextOnTheDoneCardDoesNothingRatherThanRunningOffTheEnd() {
        val controller = controller(FakeTourStorage())
        controller.offerOnLaunch()
        repeat(steps.size + 1) { controller.next() }
        assertEquals(GuidedTourPhase.DONE, controller.state.value.phase)

        controller.next()
        controller.next()

        assertEquals(GuidedTourPhase.DONE, controller.state.value.phase)
    }

    @Test
    fun backStepsBackwardsAndThenReturnsToTheWelcomeCard() {
        val controller = controller(FakeTourStorage())
        controller.offerOnLaunch()
        controller.next()
        controller.next()
        assertEquals("second", controller.state.value.currentStep?.id)

        controller.back()
        assertEquals("first", controller.state.value.currentStep?.id)

        // Back off the first step goes somewhere rather than being an inert visible control.
        controller.back()
        assertEquals(GuidedTourPhase.WELCOME, controller.state.value.phase)
    }

    @Test
    fun backFromTheDoneCardReturnsToTheLastStep() {
        val controller = controller(FakeTourStorage())
        controller.offerOnLaunch()
        repeat(steps.size + 1) { controller.next() }
        assertEquals(GuidedTourPhase.DONE, controller.state.value.phase)

        controller.back()

        assertEquals(GuidedTourPhase.STEPS, controller.state.value.phase)
        assertEquals("third", controller.state.value.currentStep?.id)
    }

    @Test
    fun skippingClosesTheTourAndIsRememberedTheSameWayFinishingIs() {
        val storage = FakeTourStorage()
        val controller = controller(storage)
        controller.offerOnLaunch()
        controller.next()

        controller.skip()

        assertEquals(GuidedTourPhase.IDLE, controller.state.value.phase)
        assertTrue(storage.guidedTourCompleted)
        assertEquals(1, storage.guidedTourVersion)
        assertTrue(controller.hasSeenTour.value)
    }

    @Test
    fun completingClosesTheTourAndRecordsIt() {
        val storage = FakeTourStorage()
        val controller = controller(storage)
        controller.offerOnLaunch()
        repeat(steps.size + 1) { controller.next() }

        controller.complete()

        assertEquals(GuidedTourPhase.IDLE, controller.state.value.phase)
        assertTrue(storage.guidedTourCompleted)
        assertEquals(1, storage.guidedTourVersion)
    }

    /** The whole point of recording it: the next launch must not open it again. */
    @Test
    fun aTourThatWasSkippedDoesNotOfferItselfOnTheNextLaunch() {
        val storage = FakeTourStorage()
        controller(storage).apply {
            offerOnLaunch()
            skip()
        }

        val nextLaunch = controller(storage)
        nextLaunch.offerOnLaunch()

        assertEquals(GuidedTourPhase.IDLE, nextLaunch.state.value.phase)
    }

    @Test
    fun settingsCanStartItAgainAfterItHasBeenSeen() {
        val storage = FakeTourStorage(guidedTourCompleted = true, guidedTourVersion = 1)
        val controller = controller(storage)

        controller.offerOnLaunch()
        assertEquals(GuidedTourPhase.IDLE, controller.state.value.phase)

        controller.startFromSettings()

        assertEquals(GuidedTourPhase.WELCOME, controller.state.value.phase)
        assertEquals(3, controller.state.value.stepCount)
    }

    /** A restart begins at the beginning, not wherever the last run was abandoned. */
    @Test
    fun restartingStartsFromTheTop() {
        val controller = controller(FakeTourStorage())
        controller.offerOnLaunch()
        controller.next()
        controller.next()
        controller.skip()

        controller.startFromSettings()

        assertEquals(GuidedTourPhase.WELCOME, controller.state.value.phase)
        assertEquals(0, controller.state.value.stepIndex)
    }

    /**
     * A build that ships a newer edition offers it once to someone who finished the old one - which
     * is the only reason the version is stored beside the flag at all.
     */
    @Test
    fun aNewerEditionIsOfferedAgainToSomeoneWhoFinishedTheOldOne() {
        val storage = FakeTourStorage(guidedTourCompleted = true, guidedTourVersion = 1)

        val controller = controller(storage, version = 2)
        controller.offerOnLaunch()

        assertEquals(GuidedTourPhase.WELCOME, controller.state.value.phase)
        assertFalse("the row must not claim the new edition was seen", controller.hasSeenTour.value)

        controller.skip()
        assertEquals(2, storage.guidedTourVersion)
    }

    /** Calling it twice - from a recomposed effect, say - must not restart a tour in progress. */
    @Test
    fun offeringItWhileItIsAlreadyOpenLeavesTheUserWhereTheyWere() {
        val controller = controller(FakeTourStorage())
        controller.offerOnLaunch()
        controller.next()
        controller.next()

        controller.offerOnLaunch()

        assertEquals("second", controller.state.value.currentStep?.id)
    }

    /** A tour with no steps at all - a catalogue emptied by a bad edit - must close cleanly rather
     * than showing a card with nothing on it. */
    @Test
    fun anEmptyStepListEndsAtTheDoneCardInsteadOfShowingABlankStep() {
        val controller = GuidedTourController(storage = FakeTourStorage(), steps = emptyList(), version = 1)
        controller.offerOnLaunch()

        controller.next()

        assertEquals(GuidedTourPhase.DONE, controller.state.value.phase)
        assertNull(controller.state.value.currentStep)
    }
}
