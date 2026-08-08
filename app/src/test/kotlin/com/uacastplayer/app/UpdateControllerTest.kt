package com.uacastplayer.app

import com.uacastplayer.update.AppVersion
import com.uacastplayer.update.GitHubRelease
import com.uacastplayer.update.ReleaseSource
import com.uacastplayer.update.UpdateCheckOutcome
import com.uacastplayer.update.UpdateCheckSchedule
import com.uacastplayer.update.UpdateCheckStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateControllerTest {

    private class FakeStorage(
        override var lastUpdateCheckAtMillis: Long? = null,
        override var dismissedUpdateTag: String? = null,
    ) : UpdateCheckStorage

    private class FakeReleaseSource(var release: GitHubRelease?) : ReleaseSource {
        var calls = 0
        override suspend fun fetchLatestRelease(): GitHubRelease? {
            calls++
            return release
        }
    }

    private fun releaseOf(tag: String) = GitHubRelease(
        version = AppVersion.parse(tag)!!,
        tagName = tag,
        releaseUrl = "https://github.com/Dovbnyak28/UA-Cast/releases/tag/$tag",
    )

    private val now = 1_800_000_000_000L

    private fun controller(
        scope: TestScope,
        source: FakeReleaseSource,
        storage: FakeStorage,
        installed: String = "0.9.0",
    ) = UpdateController(
        releaseSource = source,
        storage = storage,
        scope = scope,
        installedVersionName = installed,
        now = { now },
    )

    @Test
    fun aNewerReleaseBecomesTheBanner() = runTest {
        val source = FakeReleaseSource(releaseOf("v1.0.0"))
        val storage = FakeStorage()
        val controller = controller(this, source, storage)

        controller.checkOnLaunch()
        testScheduler.advanceUntilIdle()

        assertEquals("v1.0.0", controller.state.value.availableRelease?.tagName)
        assertFalse(controller.state.value.isChecking)
    }

    @Test
    fun theSameVersionIsNotAnUpdate() = runTest {
        val source = FakeReleaseSource(releaseOf("v0.9.0"))
        val controller = controller(this, source, FakeStorage())

        controller.checkOnLaunch()
        testScheduler.advanceUntilIdle()

        assertNull(controller.state.value.availableRelease)
    }

    /** A CI build carries a run number (`0.9.0.42`), which is newer than the `0.9.0` release it came
     * from - it must not be told to "update" back to what it already contains. */
    @Test
    fun aCiBuildIsNotOfferedTheReleaseItWasBuiltFrom() = runTest {
        val source = FakeReleaseSource(releaseOf("v0.9.0"))
        val controller = controller(this, source, FakeStorage(), installed = "0.9.0.42")

        controller.checkOnLaunch()
        testScheduler.advanceUntilIdle()

        assertNull(controller.state.value.availableRelease)
    }

    @Test
    fun theAutomaticCheckStaysQuietWithinTheWeek() = runTest {
        val source = FakeReleaseSource(releaseOf("v1.0.0"))
        val storage = FakeStorage(lastUpdateCheckAtMillis = now - 1000)
        val controller = controller(this, source, storage)

        controller.checkOnLaunch()
        testScheduler.advanceUntilIdle()

        assertEquals(0, source.calls)
        assertNull(controller.state.value.availableRelease)
    }

    @Test
    fun theAutomaticCheckRunsOnceTheWeekHasPassed() = runTest {
        val source = FakeReleaseSource(releaseOf("v1.0.0"))
        val storage = FakeStorage(lastUpdateCheckAtMillis = now - UpdateCheckSchedule.INTERVAL_MILLIS)
        val controller = controller(this, source, storage)

        controller.checkOnLaunch()
        testScheduler.advanceUntilIdle()

        assertEquals(1, source.calls)
        assertEquals(now, storage.lastUpdateCheckAtMillis)
    }

    /** The manual button is the user asking a question; the throttle is only there to stop the app
     * asking one on its own. */
    @Test
    fun theManualCheckIgnoresTheThrottle() = runTest {
        val source = FakeReleaseSource(releaseOf("v1.0.0"))
        val storage = FakeStorage(lastUpdateCheckAtMillis = now - 1000)
        val controller = controller(this, source, storage)

        controller.checkNow()
        testScheduler.advanceUntilIdle()

        assertEquals(1, source.calls)
        assertEquals(UpdateCheckOutcome.UPDATE_AVAILABLE, controller.state.value.lastOutcome)
    }

    /** A failed check the app started by itself must leave no trace in the UI - nobody asked. */
    @Test
    fun anAutomaticFailureIsSilentWhileAManualOneIsReported() = runTest {
        val silent = FakeReleaseSource(release = null)
        val silentController = controller(this, silent, FakeStorage())
        silentController.checkOnLaunch()
        testScheduler.advanceUntilIdle()
        assertNull(silentController.state.value.lastOutcome)

        val loud = FakeReleaseSource(release = null)
        val loudController = controller(this, loud, FakeStorage())
        loudController.checkNow()
        testScheduler.advanceUntilIdle()
        assertEquals(UpdateCheckOutcome.FAILED, loudController.state.value.lastOutcome)
    }

    /** Even a failure records the timestamp: a device that is offline on every launch must not
     * re-request on every launch. */
    @Test
    fun aFailedCheckStillCountsAgainstTheWeeklyThrottle() = runTest {
        val source = FakeReleaseSource(release = null)
        val storage = FakeStorage()
        val controller = controller(this, source, storage)

        controller.checkOnLaunch()
        testScheduler.advanceUntilIdle()

        assertEquals(now, storage.lastUpdateCheckAtMillis)
    }

    @Test
    fun dismissingRemembersTheTagAndHidesTheBanner() = runTest {
        val source = FakeReleaseSource(releaseOf("v1.0.0"))
        val storage = FakeStorage()
        val controller = controller(this, source, storage)

        controller.checkOnLaunch()
        testScheduler.advanceUntilIdle()
        controller.dismissAvailableUpdate()

        assertEquals("v1.0.0", storage.dismissedUpdateTag)
        assertNull(controller.state.value.availableRelease)
    }

    @Test
    fun aDismissedVersionDoesNotComeBackOnTheNextAutomaticCheck() = runTest {
        val source = FakeReleaseSource(releaseOf("v1.0.0"))
        val storage = FakeStorage(dismissedUpdateTag = "v1.0.0")
        val controller = controller(this, source, storage)

        controller.checkOnLaunch()
        testScheduler.advanceUntilIdle()

        assertNull(controller.state.value.availableRelease)
    }

    /** Dismissing 1.0.0 must not silence 1.1.0 - which is why the tag is stored rather than a
     * boolean flag somebody would have to remember to reset. */
    @Test
    fun dismissingOneVersionDoesNotSilenceTheNext() = runTest {
        val source = FakeReleaseSource(releaseOf("v1.1.0"))
        val storage = FakeStorage(dismissedUpdateTag = "v1.0.0")
        val controller = controller(this, source, storage)

        controller.checkOnLaunch()
        testScheduler.advanceUntilIdle()

        assertEquals("v1.1.0", controller.state.value.availableRelease?.tagName)
    }

    /** Asking explicitly overrides an earlier dismissal: answering "nothing here" because the user
     * once closed that banner would be a lie. */
    @Test
    fun theManualCheckShowsAVersionTheUserHadDismissed() = runTest {
        val source = FakeReleaseSource(releaseOf("v1.0.0"))
        val storage = FakeStorage(dismissedUpdateTag = "v1.0.0")
        val controller = controller(this, source, storage)

        controller.checkNow()
        testScheduler.advanceUntilIdle()

        assertEquals("v1.0.0", controller.state.value.availableRelease?.tagName)
    }

    /** Six taps must not become six requests - the same class of defect as F17. */
    @Test
    fun tapsWhileACheckIsRunningAreIgnored() = runTest {
        val source = FakeReleaseSource(releaseOf("v1.0.0"))
        val controller = controller(this, source, FakeStorage())

        repeat(6) { controller.checkNow() }
        testScheduler.advanceUntilIdle()

        assertEquals(1, source.calls)
    }

    @Test
    fun anUnparseableInstalledVersionFailsInsteadOfOfferingEveryRelease() = runTest {
        val source = FakeReleaseSource(releaseOf("v1.0.0"))
        val controller = controller(this, source, FakeStorage(), installed = "not-a-version")

        controller.checkNow()
        testScheduler.advanceUntilIdle()

        assertEquals(0, source.calls)
        assertEquals(UpdateCheckOutcome.FAILED, controller.state.value.lastOutcome)
        assertNull(controller.state.value.availableRelease)
    }

    @Test
    fun clearingTheOutcomeLeavesTheBannerAlone() = runTest {
        val release = releaseOf("v1.0.0")
        val source = FakeReleaseSource(release)
        val controller = controller(this, source, FakeStorage())

        controller.checkNow()
        testScheduler.advanceUntilIdle()
        controller.clearLastOutcome()

        assertNull(controller.state.value.lastOutcome)
        assertSame(release, controller.state.value.availableRelease)
    }
}
