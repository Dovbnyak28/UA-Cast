package com.uacastplayer.ui.platform

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BackupPickerAvailabilityTest {
    private class Launcher : ActivityResultLauncher<String>() {
        var calls = 0
        override val contract: ActivityResultContract<String, *> =
            ActivityResultContracts.CreateDocument("application/octet-stream")
        override fun launch(input: String, options: ActivityOptionsCompat?) { calls++ }
        override fun unregister() = Unit
    }

    @Test fun onlyExactKnownStubIsBlocked() = checkHandler("com.google.android.tv.frameworkpackagestubs",
        "com.google.android.tv.frameworkpackagestubs.Stubs\$DocumentsStub", false)

    @Test fun systemDocumentsUiRemainsReachable() =
        checkHandler("com.android.documentsui", "com.android.documentsui.picker.PickActivity", true)

    @Test fun thirdPartyWithSimilarClassNameIsNotBlocked() =
        checkHandler("third.party.filemanager", "third.party.filemanager.DocumentsStub", true)

    private fun checkHandler(packageName: String, className: String, expected: Boolean) {
        val context: Context = ApplicationProvider.getApplicationContext()
            val launcher = Launcher()
            val component = ComponentName(packageName, className)
            val manager = shadowOf(context.packageManager)
            manager.addActivityIfNotPresent(component).apply {
                exported = true
                enabled = true
            }
            val filter = IntentFilter(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_OPENABLE)
                addDataType("application/octet-stream")
            }
            manager.addIntentFilterForActivity(component, filter)
            assertEquals(expected, launcher.launchBackupPickerIfSupported("fixture.uacast", "test export", context))
            assertEquals(if (expected) 1 else 0, launcher.calls)
    }
}
