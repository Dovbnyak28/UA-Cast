package app.zorik.player.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.LocalReducedEffects
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w320dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class ControlPressAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    @Test fun reducedEffectsNeverShrinksTheActionDuringAHeldPress() = assertStableTarget(reduced = true)
    @Test fun regularPressFeedbackDoesNotMoveOrShrinkTheClickableTarget() = assertStableTarget(reduced = false)

    private fun assertStableTarget(reduced: Boolean) {
        rule.setContent {
            CompositionLocalProvider(LocalReducedEffects provides reduced) {
                ZorikTheme(AppTheme.CINEMA) { Column { PrimaryButton("Відтворити", {}) } }
            }
        }
        val node = rule.onNodeWithText("Відтворити")
        val before = node.getUnclippedBoundsInRoot()
        node.performTouchInput { down(center) }
        try {
            rule.mainClock.advanceTimeBy(350)
            assertEquals("Motion must not change the action target", before, node.getUnclippedBoundsInRoot())
        } finally {
            node.performTouchInput { up() }
        }
    }
}
