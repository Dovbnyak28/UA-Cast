package com.uacastplayer.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.core.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalizedPreferencesContextTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = AppPreferences(context)

    @Before
    fun selectUkrainian() {
        preferences.language = AppLanguage.UKRAINIAN
    }

    @Test
    fun `explicit preference is the current application language`() {
        assertEquals(AppLanguage.UKRAINIAN, context.currentAppLanguage())
    }

    @Test
    fun `localized context uses the selected preference`() {
        val locale = context.withAppLocale().resources.configuration.locales[0]

        assertEquals(AppLanguage.UKRAINIAN.code, locale.language)
    }
}
