package com.uacastplayer.ui.player

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.R
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.player.AudioChannelLayout
import com.uacastplayer.player.PlaybackBadgesState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "en-w320dp-h480dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class QualityDetailsLayoutTest(private val locale: String) {
    @get:Rule val rule = createComposeRule()
    @After fun resetFontScale() { RuntimeEnvironment.setFontScale(1f) }

    @Test fun audioMetadataIsReadableOnACompactPhoneWithLargeText() {
        checkAudioMetadata("w320dp-h480dp", "compact")
    }

    @Test fun audioMetadataIsReadableInLandscapeWithLargeText() {
        checkAudioMetadata("w640dp-h360dp-land", "landscape")
    }

    private fun checkAudioMetadata(size: String, screenshot: String) {
        RuntimeEnvironment.setQualifiers("$locale-$size-xhdpi")
        RuntimeEnvironment.setFontScale(2f)
        val context = ApplicationProvider.getApplicationContext<Context>()
        var dismissals = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                QualityDetailsDialog(PlaybackBadgesState(
                    videoWidth = 1920, videoHeight = 1080, videoCodecLabel = "H.264",
                    frameRate = 60f, videoBitrateBps = 8_000_000,
                    audioCodecLabel = "AAC", channelLayout = AudioChannelLayout.STEREO,
                    audioSampleRateHz = 48_000, audioBitrateBps = 192_000,
                ), onDismiss = { dismissals++ })
            }
        }
        val audio = rule.onNodeWithText("AAC", substring = true)
        if (!audio.isDisplayed()) audio.performScrollTo()
        if (locale == "uk") {
            rule.onNode(isDialog()).captureRoboImage("src/test/screenshots/quality_${screenshot}_large_uk.png")
        }
        val layouts = mutableListOf<TextLayoutResult>()
        audio.assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals(2f, layout.layoutInput.density.fontScale, 0.01f)
        assertFalse("Audio metadata is clipped", layout.didOverflowHeight)
        rule.onNodeWithText(context.getString(R.string.common_close)).assertIsDisplayed().performClick()
        assertEquals(1, dismissals)
    }

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "locale={0}")
        fun locales() = listOf(arrayOf("en"), arrayOf("uk"), arrayOf("ru"), arrayOf("es"))
    }
}
