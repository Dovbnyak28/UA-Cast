package com.uacastplayer.ui.premium

import com.uacastplayer.premium.Feature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two things every gated offer point in the app relies on, asserted once here rather than
 * re-tested at each of the seven call sites.
 *
 * The interesting half is [guardedActionsDoNotRunWhenLocked]: a gate that opened the paywall *and*
 * still ran the action would be worse than no gate at all, because it would look correct in a
 * screenshot and be wrong in behaviour.
 */
class FeatureGateTest {

    private fun gate(unlocked: Boolean, refused: MutableList<Feature>) =
        FeatureGate(isUnlocked = { unlocked }, onPaywall = { refused += it })

    @Test
    fun guardedActionsRunWhenUnlocked() {
        val refused = mutableListOf<Feature>()
        var ran = 0

        gate(unlocked = true, refused = refused).guard(Feature.BACKUP) { ran++ }()

        assertEquals(1, ran)
        assertTrue("nothing should have been refused", refused.isEmpty())
    }

    @Test
    fun guardedActionsDoNotRunWhenLocked() {
        val refused = mutableListOf<Feature>()
        var ran = 0

        gate(unlocked = false, refused = refused).guard(Feature.BACKUP) { ran++ }()

        assertEquals("the guarded action must not run", 0, ran)
        assertEquals(listOf(Feature.BACKUP), refused)
    }

    /** Which feature was refused is what the dialog is about, so it has to survive the guard. */
    @Test
    fun theRefusedFeatureIsTheOneThatWasAskedFor() {
        val refused = mutableListOf<Feature>()
        val gate = gate(unlocked = false, refused = refused)

        gate.guard(Feature.DLNA) {}()
        gate.guard(Feature.XTREAM) {}()

        assertEquals(listOf(Feature.DLNA, Feature.XTREAM), refused)
    }

    @Test
    fun isLockedIsTheInverseOfUnlocked() {
        assertFalse(gate(unlocked = true, refused = mutableListOf()).isLocked(Feature.DLNA))
        assertTrue(gate(unlocked = false, refused = mutableListOf()).isLocked(Feature.DLNA))
    }

    /**
     * The default a composable gets outside any provider - a preview, a screenshot test, a screen
     * whose host forgot to provide one. It has to fail towards the user: an app that silently
     * paywalls everything because a `CompositionLocal` was missing is the worse of the two bugs.
     */
    @Test
    fun theDefaultGateLocksNothing() {
        var ran = 0

        for (feature in Feature.entries) {
            assertFalse(feature.name, FeatureGate.Open.isLocked(feature))
            FeatureGate.Open.guard(feature) { ran++ }()
        }

        assertEquals(Feature.entries.size, ran)
    }
}
