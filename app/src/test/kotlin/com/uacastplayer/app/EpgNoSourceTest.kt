package com.uacastplayer.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.data.epg.EpgRepository
import com.uacastplayer.data.prefs.AppPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EpgNoSourceTest {
    @Test fun legacyPresetDoesNotStartNetworkOrEmptyGuideTicks() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("uacast_prefs", Context.MODE_PRIVATE).edit()
            .putString("epg_source_id", "epg_it999_pp").apply()
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor {
            requests++
            throw java.io.IOException("Unexpected network request")
        }.build()
        val controller = EpgController(AppPreferences(app),
            EpgRepository(app, StandardTestDispatcher(testScheduler), client), backgroundScope, {})
        val initialTime = controller.epgState.value.nowMillis
        repeat(3) { controller.startTicking(); controller.loadInitial(); controller.refresh() }
        runCurrent()
        assertEquals(1, backgroundScope.coroutineContext[Job]!!.children.count())
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(0, requests)
        assertEquals(initialTime, controller.epgState.value.nowMillis)
        assertFalse(controller.epgState.value.isLoading)
        assertFalse(controller.epgState.value.hasError)
        assertNull(controller.epgState.value.customUrl)
    }

    @Test fun invalidImportedUrlDoesNotReplaceUsersSource() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = AppPreferences(app).apply { customEpgUrl = "https://example.test/guide.xml" }
        val controller = EpgController(prefs, EpgRepository(app), backgroundScope, {})
        controller.setUserEpgUrl("file:///private.xml")
        assertEquals("https://example.test/guide.xml", controller.epgState.value.customUrl)
        assertEquals("https://example.test/guide.xml", prefs.customEpgUrl)
        assertFalse(controller.epgState.value.isLoading)
    }
}
