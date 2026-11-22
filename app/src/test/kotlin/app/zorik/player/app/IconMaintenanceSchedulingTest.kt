package app.zorik.player.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.zorik.player.core.settings.IconDisplayMode
import app.zorik.player.data.icons.IconPrefetcher
import app.zorik.player.data.icons.IconRepository
import app.zorik.player.data.prefs.AppPreferences
import app.zorik.player.core.security.Fingerprint
import app.zorik.player.player.PlaybackActivity
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IconMaintenanceSchedulingTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        PlaybackActivity.setActive(false)
        application.getSharedPreferences("zorik_icon_failures", Application.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `failure pruning is scheduled off the trigger caller`() = runTest {
        val preferences = AppPreferences(application)
        val failurePreferences = application.getSharedPreferences("zorik_icon_failures", Application.MODE_PRIVATE)
        val repository = IconRepository(application)
        val expiredKey = Fingerprint.of("https://icons.example.com/expired.png")
        failurePreferences.edit().putLong(expiredKey, 0L).commit()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controllerScope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + dispatcher)
        val controller = IconController(
            preferences = preferences,
            iconRepository = repository,
            iconPrefetcher = IconPrefetcher(application, repository),
            scope = controllerScope,
            onPrefetchFinished = {},
            maintenanceDispatcher = dispatcher,
        )

        controller.triggerPrefetch(emptyList(), IconDisplayMode.PLACEHOLDERS)

        assertTrue(failurePreferences.contains(expiredKey))
        testScheduler.runCurrent()
        assertFalse(failurePreferences.contains(expiredKey))
        controllerScope.cancel()
    }
}
