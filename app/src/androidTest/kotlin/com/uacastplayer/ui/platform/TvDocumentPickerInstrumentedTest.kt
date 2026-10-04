package com.uacastplayer.ui.platform

import android.net.Uri
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only OEM diagnosis: launches CreateDocument but never chooses or writes a user file. */
@RunWith(AndroidJUnit4::class)
class TvDocumentPickerInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun knownMiTvDocumentStubReturnsCancelledWithoutAFilePicker() {
        val contract = ActivityResultContracts.CreateDocument("application/octet-stream")
        val intent = contract.createIntent(rule.activity, "ua-cast-picker-probe.uacast")
        val resolved = rule.activity.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        assumeTrue(resolved?.activityInfo?.packageName == "com.google.android.tv.frameworkpackagestubs")
        val calls = AtomicInteger()
        val selected = AtomicReference<Uri?>()
        rule.runOnIdle {
            val launcher = rule.activity.activityResultRegistry.register("tv-documents-probe", contract) {
                selected.set(it)
                calls.incrementAndGet()
            }
            launcher.launch("ua-cast-picker-probe.uacast")
        }
        rule.waitUntil(15_000) { calls.get() > 0 }
        assertEquals(1, calls.get())
        assertNull(selected.get())
    }

    @Test fun applicationRejectsKnownStubBeforeLaunchingAndAllowsVisibleExplanation() {
        val documentContract = ActivityResultContracts.CreateDocument("application/octet-stream")
        val resolved = rule.activity.packageManager.resolveActivity(
            documentContract.createIntent(rule.activity, "probe.uacast"), PackageManager.MATCH_DEFAULT_ONLY,
        )
        assumeTrue(resolved?.activityInfo?.name ==
            "com.google.android.tv.frameworkpackagestubs.Stubs\$DocumentsStub")
        var launched = 0
        val launcher = object : ActivityResultLauncher<String>() {
            override val contract: ActivityResultContract<String, *> = documentContract
            override fun launch(input: String, options: ActivityOptionsCompat?) { launched++ }
            override fun unregister() = Unit
        }
        rule.runOnIdle {
            assertEquals(false, launcher.launchBackupPickerIfSupported("probe.uacast", "export backup", rule.activity))
        }
        assertEquals(0, launched)
    }
}
