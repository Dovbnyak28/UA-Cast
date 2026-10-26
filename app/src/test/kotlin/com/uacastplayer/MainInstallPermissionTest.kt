package com.uacastplayer

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainInstallPermissionTest {
    @Test
    fun `denied settings activity does not crash the update permission flow`() {
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun startActivity(intent: Intent) {
                assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
                throw SecurityException("Settings activity is not exported")
            }
        }

        openInstallPermissionSettings(context)
    }
}
