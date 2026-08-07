package com.uacastplayer.playlist

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uacastplayer.MainActivity
import com.uacastplayer.R
import com.uacastplayer.testsupport.skipOnboarding
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scenario 5 of the Session B lifecycle suite: a completely fresh start, with no playlist ever
 * loaded, must show the Channels tab's empty state rather than crashing. Uses
 * [createEmptyComposeRule] + a manually-launched [ActivityScenario] (instead of
 * [androidx.compose.ui.test.junit4.v2.createAndroidComposeRule]) specifically so persisted playlist
 * state can be wiped *before* the Activity - and the ViewModel that reads it - is constructed.
 */
@RunWith(AndroidJUnit4::class)
class EmptyPlaylistInstrumentedTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private lateinit var savedState: PersistedPlaylistState

    @Before
    fun backUpPersistedState() {
        savedState = PersistedPlaylistState.capture(context())
    }

    @After
    fun restorePersistedState() {
        savedState.restore(context())
    }

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
        val context = context()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.filesDir.listFiles { file -> file.name.contains("playlist") }?.forEach { it.delete() }
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    /**
     * A copy of everything [clearPersistedPlaylistState] destroys, so it can be put back.
     *
     * This suite runs against a real device, and on a developer's phone that is the same install
     * they actually watch TV on - so wiping the preferences and playlist files is not a private
     * fixture reset, it deletes their configured playlist, EPG source, favourites key and settings
     * for real. It happened twice during this suite's own repair before anyone noticed, and there
     * was nothing to put back afterwards because the state had never been captured. Now it is,
     * before the wipe, and restored in `@After` whether the test passes or fails.
     */
    private class PersistedPlaylistState(
        private val preferences: Map<String, Any?>,
        private val files: Map<String, ByteArray>,
    ) {
        fun restore(context: Context) {
            val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            editor.clear()
            for ((key, value) in preferences) {
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    // The only other type SharedPreferences can hold. Unchecked because the API
                    // erases it; nothing here writes a Set of anything but String.
                    is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
                    else -> Unit
                }
            }
            editor.commit()
            for ((name, bytes) in files) {
                File(context.filesDir, name).writeBytes(bytes)
            }
        }

        companion object {
            fun capture(context: Context): PersistedPlaylistState {
                val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all.toMap()
                // Deliberately the same predicate clearPersistedPlaylistState deletes by, so the two
                // can never drift into restoring less than was destroyed.
                val files = context.filesDir
                    .listFiles { file -> file.isFile && file.name.contains("playlist") }
                    .orEmpty()
                    .associate { it.name to it.readBytes() }
                return PersistedPlaylistState(preferences, files)
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "uacast_prefs"
    }
}
