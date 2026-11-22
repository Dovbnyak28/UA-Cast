@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package app.zorik.player.ui.player

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import app.zorik.player.R
import app.zorik.player.player.AudioChannelLayout
import app.zorik.player.player.SelectableTrack
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w320dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(RequiresComposeTestManifest::class)
class TrackPickerSelectionTest {
    @get:Rule val rule = createComposeRule()
    @After fun restoreFontScale() { RuntimeEnvironment.setFontScale(1f) }
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun offAndTracksKeepOneSelectionAndOneCallbackPerTap() {
        val group = TrackGroup(Format.Builder().setId("subtitles").build())
        val tracks = mutableStateOf(listOf(SelectableTrack(group, 0, "Українська", false),
            SelectableTrack(group, 1, "Español", false)))
        var selections = 0
        var disables = 0
        val off = context.getString(R.string.player_subtitle_off)
        rule.setContent { ZorikTheme(AppTheme.CINEMA) {
            TrackPickerDialog(context.getString(R.string.player_subtitle_track), tracks.value,
                offLabel = off, onSelectOff = {
                    disables++
                    tracks.value = tracks.value.map { it.copy(isSelected = false) }
                },
                onSelect = { selected ->
                    selections++
                    assertTrue(selected.trackGroup === group)
                    tracks.value = tracks.value.map { it.copy(isSelected = it.indexInGroup == selected.indexInGroup) }
                }, onDismiss = {})
        } }
        rule.onNodeWithText(off).assertIsSelected()
        rule.onNodeWithText("Українська").assertIsNotSelected().performClick().assertIsSelected()
        rule.onNodeWithText(off).assertIsNotSelected().performClick().assertIsSelected()
        rule.onNodeWithText("Українська").assertIsNotSelected()
        assertEquals(1, selections)
        assertEquals(1, disables)
        rule.onRoot().captureRoboImage("src/test/screenshots/track_picker_off_selected_uk.png")
    }

    @Test fun longTrackDetailsStayReadableAtActualTwoHundredPercent() {
        RuntimeEnvironment.setFontScale(2f)
        val group = TrackGroup(Format.Builder().setId("audio").build())
        val label = "Українська — повна оригінальна звукова доріжка"
        val track = SelectableTrack(group, 0, label, true, codecLabel = "AAC",
            channelLayout = AudioChannelLayout.SURROUND_5_1, sampleRateHz = 48_000, bitrateBps = 192_000)
        rule.setContent { ZorikTheme(AppTheme.CINEMA) {
            TrackPickerDialog(context.getString(R.string.player_audio_track), listOf(track),
                onSelect = {}, onDismiss = {})
        } }
        val node = rule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(2f, layouts.single().layoutInput.density.fontScale, 0.01f)
        assertFalse(layouts.single().hasVisualOverflow)
        val bounds = node.getUnclippedBoundsInRoot()
        assertTrue("Selected row touch height", bounds.bottom - bounds.top >= 48.dp)
        rule.onNodeWithText(context.getString(R.string.common_close)).assertIsDisplayed()
        rule.onRoot().captureRoboImage("src/test/screenshots/track_picker_large_text_uk.png")
    }
}
