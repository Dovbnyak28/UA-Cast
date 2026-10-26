package com.uacastplayer.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.core.settings.IconDisplayMode
import com.uacastplayer.data.icons.IconPrefetcher
import com.uacastplayer.data.icons.IconRepository
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.playlist.M3uChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IconSourceRevisionTest {
    @Test fun `pack edits refresh displayed logos and removing the last pack closes its network watcher`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("custom_icon_sources", Application.MODE_PRIVATE).edit().clear().commit()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = IconRepository(app, dispatcher)
        var registrations = 0
        var closures = 0
        val controller = IconController(AppPreferences(app), repository, IconPrefetcher(app, repository), owner, {},
            maintenanceDispatcher = dispatcher, awaitNetwork = {
                registrations++
                AutoCloseable { closures++ }
            })
        try {
            controller.triggerPrefetch(listOf(M3uChannel("Channel", "https://unused.test/live", tvgId = "id")),
                IconDisplayMode.CACHE)
            assertEquals(0, registrations)
            assertFalse(controller.iconPrefetchState.value.updateReminderDue)
            val original = controller.iconPrefetchState.value.refreshKey
            controller.sources.add("https://icons.example.test/logos")
            val added = controller.iconPrefetchState.value.refreshKey
            assertNotEquals(original, added)
            assertEquals(1, registrations)
            controller.sources.add("https://icons.example.test/logos")
            assertEquals(added, controller.iconPrefetchState.value.refreshKey)
            controller.sources.remove("https://icons.example.test/logos")
            assertNotEquals(added, controller.iconPrefetchState.value.refreshKey)
            assertEquals(1, closures)
            assertEquals(1, registrations)
            assertFalse(controller.iconPrefetchState.value.isRunning)
        } finally {
            controller.dispose()
            owner.cancel()
        }
    }
}
