package com.uacastplayer.playlist

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uacastplayer.MainActivity
import com.uacastplayer.R
import com.uacastplayer.testsupport.skipOnboarding
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scenario 5 of the Session B lifecycle suite: a completely fresh start, with no playlist ever
 * loaded, must show the Channels tab's empty state rather than crashing. Uses
 * [createEmptyComposeRule] + a manually-launched [ActivityScenario] (instead of
 * [androidx.compose.ui.test.junit4.createAndroidComposeRule]) specifically so persisted playlist
 * state can be wiped *before* the Activity - and the ViewModel that reads it - is constructed.
 */
@RunWith(AndroidJUnit4::class)
class EmptyPlaylistInstrumentedTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Test
    fun freshStartWithNoPlaylist_showsEmptyStateWithoutCrashing() {
        clearPersistedPlaylistState()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var activity: MainActivity? = null
            scenario.onActivity { activity = it }
            val mainActivity = checkNotNull(activity) { "MainActivity was not available after launch" }

            skipOnboarding(mainActivity)
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText(mainActivity.getString(R.string.nav_channels)).performClick()
            composeTestRule.waitForIdle()

            // Throws if the empty-state message never appears - a plain existence check, same as
            // MainActivitySmokeTest's "did anything crash" philosophy.
            composeTestRule.onNodeWithText(mainActivity.getString(R.string.channels_empty_message))
        }
    }

    /** Deletes every persisted playlist artifact (snapshot, sources, and their AtomicFile backup
     * copies) so the app genuinely has nothing to load - onboarding prefs are cleared too, but
     * [skipOnboarding] bypasses those directly after launch regardless. */
    private fun clearPersistedPlaylistState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("uacast_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.filesDir.listFiles { file -> file.name.contains("playlist") }?.forEach { it.delete() }
    }
}
