package com.uacastplayer.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.data.prefs.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppThemePreferenceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = AppPreferences(context)

    @Before
    fun clearTheme() {
        preferences.appThemeId = null
    }

    @Test
    fun `missing persisted ID resolves to the design system default without writing one`() {
        assertEquals(AppTheme.DEFAULT, preferences.appTheme)
        assertNull(preferences.appThemeId)
    }

    @Test
    fun `selected UI theme round trips through its stable persisted ID`() {
        preferences.appTheme = AppTheme.MIDNIGHT

        assertEquals(AppTheme.MIDNIGHT.name, preferences.appThemeId)
        assertEquals(AppTheme.MIDNIGHT, AppPreferences(context).appTheme)
    }
}
