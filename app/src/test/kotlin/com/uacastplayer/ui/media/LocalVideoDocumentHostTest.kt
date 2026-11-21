package com.uacastplayer.ui.media

import android.app.Application
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.R
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.data.media.LocalVideoReader
import com.uacastplayer.media.LocalVideo
import com.uacastplayer.media.LocalVideoOpenViewModel
import com.uacastplayer.media.LocalVideoSelectionState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class LocalVideoDocumentHostTest {
    @get:Rule val rule = createComposeRule()
    private val store = ViewModelStore()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @After fun clear() { store.clear() }

    @Test fun formatsHintUsesTheReadablePaletteColorAcrossThemes() {
        val owner = LocalVideoOpenViewModel(app, LocalVideoReader { _, _ -> error("No document was selected") })
        store.put("video", owner)
        val theme = mutableStateOf(AppTheme.CINEMA)
        var expected = androidx.compose.ui.graphics.Color.Unspecified
        rule.setContent { UaCastTheme(theme.value) {
            expected = UaTheme.palette.labelSecondary
            LocalVideoDocumentHost({}, owner) {
                Column(Modifier.fillMaxWidth().background(UaTheme.palette.void).padding(16.dp)) {
                    OpenLocalVideoButton()
                }
            }
        } }
        AppTheme.entries.forEach { selected ->
            rule.runOnIdle { theme.value = selected }
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(app.getString(R.string.local_video_formats_hint))
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(selected.name, expected, layouts.single().layoutInput.style.color)
            if (selected == AppTheme.CINEMA || selected == AppTheme.DAYLIGHT) {
                rule.onRoot().captureRoboImage(
                    "src/test/screenshots/local_video_action_${selected.name.lowercase()}.png",
                )
            }
        }
    }

    @Test fun documentPickerCancellationDoesNotOpenAnythingOrChangeData() {
        val owner = LocalVideoOpenViewModel(app, LocalVideoReader { _, _ -> error("Cancelled picker must not read") })
        store.put("video", owner)
        var opened = 0
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
                options: ActivityOptionsCompat?) {
                assertEquals(listOf("*/*"), (input as Array<*>).toList())
                dispatchResult(requestCode, android.app.Activity.RESULT_CANCELED, null)
            }
        }
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                    LocalVideoDocumentHost({ opened++ }, owner) { OpenLocalVideoButton() }
                }
            }
        }
        rule.onNodeWithText("Open video file").performClick()
        rule.runOnIdle {
            assertEquals(0, opened)
            assertEquals(LocalVideoSelectionState(), owner.state.value)
        }
    }

    @Test fun pendingProviderReadHasAVisibleCancelActionAndNoLatePlayerOpen() {
        val owner = LocalVideoOpenViewModel(app, LocalVideoReader { _, _ -> awaitCancellation() })
        store.put("video", owner)
        var opened = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                LocalVideoDocumentHost({ opened++ }, owner) { OpenLocalVideoButton() }
            }
        }
        rule.runOnIdle { owner.open("content://media/video/1") }
        rule.onNodeWithText("Opening video…").assertExists()
        rule.onNodeWithText("Cancel").performClick()
        rule.runOnIdle {
            assertEquals(0, opened)
            assertNull(owner.state.value.video)
            assertEquals(LocalVideoSelectionState(), owner.state.value)
        }
    }

    @Test fun successfulSelectionIsConsumedOnceRatherThanReopeningDuringRecomposition() {
        val video = LocalVideo("content://media/video/1", "Movie.mp4", "video/mp4")
        val owner = LocalVideoOpenViewModel(app, LocalVideoReader { _, _ -> video })
        store.put("video", owner)
        var opened = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                LocalVideoDocumentHost({ opened++ }, owner) { OpenLocalVideoButton() }
            }
        }
        rule.runOnIdle { owner.open(video.uri) }
        rule.waitUntil(5000) { opened == 1 }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(1, opened); assertNull(owner.state.value.video) }
    }

    @Test fun twoExplicitOpensOfTheSameFileAreNotCollapsedIntoOneUiEvent() {
        val uri = "content://media/video/1"
        val owner = LocalVideoOpenViewModel(app, LocalVideoReader { _, _ ->
            LocalVideo(uri, "Movie.mp4", "video/mp4")
        })
        store.put("video", owner)
        var opened = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                LocalVideoDocumentHost({
                    opened++
                    // A second explicit request lands before the UI observes consumption of the first.
                    if (opened == 1) owner.open(uri)
                }, owner) { OpenLocalVideoButton() }
            }
        }
        rule.runOnIdle { owner.open(uri) }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(2, opened); assertNull(owner.state.value.video) }
    }
}
