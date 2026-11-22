package app.zorik.player.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ControlFeedbackPolicyTest {
    @Test fun reducedMotionKeepsTheVisualScaleStableEvenDuringAPress() {
        assertEquals(1f, controlScaleTarget(true, true, false, 0.97f), 0f)
    }
    @Test fun disabledActionsCannotAcquirePressMotion() {
        assertEquals(1f, controlScaleTarget(true, false, true, 0.97f), 0f)
    }
    @Test fun onlyAnEnabledHeldPressUsesTheFeedbackScale() {
        assertEquals(0.97f, controlScaleTarget(true, true, true, 0.97f), 0f)
        assertEquals(1f, controlScaleTarget(false, true, true, 0.97f), 0f)
    }
}
