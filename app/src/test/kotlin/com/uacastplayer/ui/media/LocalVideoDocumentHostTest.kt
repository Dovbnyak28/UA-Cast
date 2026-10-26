package com.uacastplayer.ui.media

import android.app.Application
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.CompositionLocalProvider
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
