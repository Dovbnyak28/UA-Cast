package com.uacastplayer.guidedtour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue's own integrity.
 *
 * Steps are data, which is what makes them cheap to edit - and what makes a typo in one of them
 * silent. A misspelled target key does not fail to compile; it produces a step that quietly renders
 * as plain text forever, and nobody notices because that is also what a legitimately absent element
 * looks like.
 */
class GuidedTourStepsTest {

    private val steps = GuidedTourSteps.DEFAULT

    @Test
    fun theTourIsTheSevenStepsItAdvertises() {
        assertEquals(7, steps.size)
    }

    /** Ids reach the log and are what a "target not found" line names. Two steps sharing one would
     * make that line ambiguous. */
    @Test
    fun everyStepHasItsOwnId() {
        assertEquals(steps.size, steps.map { it.id }.distinct().size)
    }

    @Test
    fun everyStepPointsAtAKeySomethingCanActuallyRegister() {
        val unknown = steps
            .mapNotNull { (it.target as? GuidedTourTarget.Element)?.key }
            .filterNot { it in GuidedTourKeys.ALL }

        assertTrue("steps point at unregisterable keys: $unknown", unknown.isEmpty())
    }

    /** Not a formality: a zero here is a step that shows a blank card, and `stringResource(0)`
     * throws rather than degrading. */
    @Test
    fun everyStepCarriesRealStringResources() {
        steps.forEach { step ->
            assertTrue("step '${step.id}' has no title", step.titleRes != 0)
            assertTrue("step '${step.id}' has no description", step.descriptionRes != 0)
        }
    }

    /**
     * The catalogue and the stored version move together. A step added without bumping this means
     * every existing user's tour silently stops matching what they were shown - and never offers
     * them the difference.
     */
    @Test
    fun theVersionIsBumpedWheneverTheStepListChanges() {
        assertEquals(
            "GuidedTourVersion.CURRENT must be bumped when GuidedTourSteps.DEFAULT changes",
            1,
            GuidedTourVersion.CURRENT,
        )
        assertEquals(7, steps.size)
    }
}
