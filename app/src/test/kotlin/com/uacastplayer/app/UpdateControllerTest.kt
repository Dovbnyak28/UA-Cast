package com.uacastplayer.app

import com.uacastplayer.update.AppVersion
import com.uacastplayer.update.GitHubRelease
import com.uacastplayer.update.ReleaseLookup
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

    private class FakeReleaseSource(var lookup: ReleaseLookup) : ReleaseSource {
        var calls = 0
        override suspend fun fetchLatestRelease(): ReleaseLookup {
            calls++
            return lookup
        }
    }

    private fun releaseOf(tag: String) = GitHubRelease(
        version = AppVersion.parse(tag)!!,
        tagName = tag,
        releaseUrl = "https://github.com/Dovbnyak28/UA-Cast/releases/tag/$tag",
    )

    private fun found(tag: String) = ReleaseLookup.Found(releaseOf(tag))

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
        val source = FakeReleaseSource(found("v1.0.0"))
        val storage = FakeStorage()
        val controller = controller(this, source, storage)

        controller.checkOnLaunch()
        testScheduler.advanceUntilIdle()

        assertEquals("v1.0.0", controller.state.value.availableRelease?.tagName)
        assertFalse(controller.state.value.isChecking)
    }

    @Test
    fun theSameVersionIsNotAnUpdate() = runTest {
        val source = FakeReleaseSource(found("v0.9.0"))
        val controller = controller(this, source, FakeStorage())

        controller.checkOnLaunch()
        testScheduler.advanceUntilIdle()

        assertNull(controller.state.value.availableRelease)
    }

    /** A CI build carries a run number (`0.9.0.42`), which is newer than the `0.9.0` release it came
     * from - it must not be told to "update" back to what it already contains. */
    @Test
    fun aCiBuildIsNotOfferedTheReleaseItWasBuiltFrom() = runTest {
        val source = FakeReleaseSource(found("v0.9.0"))
        val controller = controller(this, source, FakeStorage(), installed = "0.9.0.42")

        controller.checkOnLaunch()
        testScheduler.advanceUntilIdle()

        assertNull(controller.state.value.availableRelease)
    }

    @Test
    fun theAutomaticCheckStaysQuietWithinTheWeek() = runTest {
        val source = FakeReleaseSource(found("v1.0.0"))
        val storage = FakeStorage(lastUpdateCheckAtMillis = now - 1000)
        val controller = controller(this, source, storage)

        controller.checkOnLaunch()
        testScheduler.advanceUntilIdle()

        assertEquals(0, source.calls)
        assertNull(controller.state.value.availableRelease)
    }

    @Test
    fun theAutomaticCheckRunsOnceTheWeekHasPassed() = runTest {
        val source = FakeReleaseSource(found("v1.0.0"))
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
        val source = FakeReleaseSource(found("v1.0.0"))
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
        val silent = FakeReleaseSource(ReleaseLookup.Failed)
        val silentController = controller(this, silent, FakeStorage())
        silentController.checkOnLaunch()
        testScheduler.advanceUntilIdle()
        assertNull(silentController.state.value.lastOutcome)

        val loud = FakeReleaseSource(ReleaseLookup.Failed)
        val loudController = controller(this, loud, FakeStorage())
        loudController.checkNow()
        testScheduler.advanceUntilIdle()
        assertEquals(UpdateCheckOutcome.FAILED, loudController.state.value.lastOutcome)
    }

    /**
     * A repository with no published release answers the question rather than failing to: nothing
     * newer exists, so the installed build is the newest there is. This is the state every install
     * is in until the first release is cut, and `docs/RELEASING.md` has always said so - reporting
     * "could not check" here told the user to retry a condition retrying cannot change.
     */
    @Test
    fun nothingPublishedYetIsUpToDateRatherThanAFailedCheck() = runTest {
        val source = FakeReleaseSource(ReleaseLookup.NonePublished)
        val controller = controller(this, source, FakeStorage())

        controller.checkNow()
        testScheduler.advanceUntilIdle()

        assertEquals(UpdateCheckOutcome.UP_TO_DATE, controller.state.value.lastOutcome)
        assertNull(controller.state.value.availableRelease)
    }

    /**
     * The automatic path, which by design cannot tell the two behaviours apart: a silent check
     * reports nothing whether it decided "failed" or "up to date", so this **does not** prove the
     * fix - only the manual test above does. It is kept for what it does pin: the new case still
     * raises no banner, and still records the timestamp the weekly throttle reads, which a future
     * early return from `NonePublished` would quietly break.
     */
    @Test
    fun nothingPublishedYetRaisesNoBannerOnLaunch() = runTest {
        val source = FakeReleaseSource(ReleaseLookup.NonePublished)
        val storage = FakeStorage()
        val controller = controller(this, storage = storage, source = source)

        controller.checkOnLaunch()
        testScheduler.advanceUntilIdle()

        assertNull(controller.state.value.availableRelease)
        assertNull(controller.state.value.lastOutcome)
        assertEquals(now, storage.lastUpdateCheckAtMillis)
    }

    /** A release that exists but cannot be read is still a failure, not "nothing published" -
     * something is there and this build did not understand it, which is worth reporting. */
    @Test
    fun anUnreadableReleaseStaysAFailure() = runTest {
        val source = FakeReleaseSource(ReleaseLookup.Failed)
        val controller = controller(this, source, FakeStorage())

        controller.checkNow()
        testScheduler.advanceUntilIdle()

        assertEquals(UpdateCheckOutcome.FAILED, controller.state.value.lastOutcome)
    }

    @Test
    fun `an unexpected release source exception finishes the manual check as failed`() = runTest {
        val storage = FakeStorage()
        val source = object : ReleaseSource {
            override suspend fun fetchLatestRelease(): ReleaseLookup =
                throw IllegalStateException("provider failed")
        }
        val controller = UpdateController(
            releaseSource = source,
            storage = storage,
            scope = this,
            installedVersionName = "0.9.0",
            now = { now },
        )

        controller.checkNow()
        testScheduler.advanceUntilIdle()

        assertFalse(controller.state.value.isChecking)
        assertEquals(UpdateCheckOutcome.FAILED, controller.state.value.lastOutcome)
        assertEquals(now, storage.lastUpdateCheckAtMillis)
    }

    /** Even a failure records the timestamp: a device that is offline on every launch must not
     * re-request on every launch. */
    @Test
    fun aFailedCheckStillCountsAgainstTheWeeklyThrottle() = runTest {
        val source = FakeReleaseSource(ReleaseLookup.Failed)
        val storage = FakeStorage()
        val controller = controller(this, source, storage)

        controller.checkOnLaunch()
        testScheduler.advanceUntilIdle()

        assertEquals(now, storage.lastUpdateCheckAtMillis)
    }

    @Test
    fun dismissingRemembersTheTagAndHidesTheBanner() = runTest {
        val source = FakeReleaseSource(found("v1.0.0"))
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
        val source = FakeReleaseSource(found("v1.0.0"))
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
        val source = FakeReleaseSource(found("v1.1.0"))
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
        val source = FakeReleaseSource(found("v1.0.0"))
        val storage = FakeStorage(dismissedUpdateTag = "v1.0.0")
        val controller = controller(this, source, storage)

        controller.checkNow()
        testScheduler.advanceUntilIdle()

        assertEquals("v1.0.0", controller.state.value.availableRelease?.tagName)
    }

    /** Six taps must not become six requests - the same class of defect as F17. */
    @Test
    fun tapsWhileACheckIsRunningAreIgnored() = runTest {
        val source = FakeReleaseSource(found("v1.0.0"))
        val controller = controller(this, source, FakeStorage())

        repeat(6) { controller.checkNow() }
        testScheduler.advanceUntilIdle()

        assertEquals(1, source.calls)
    }

    @Test
    fun anUnparseableInstalledVersionFailsInsteadOfOfferingEveryRelease() = runTest {
        val source = FakeReleaseSource(found("v1.0.0"))
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
        val source = FakeReleaseSource(ReleaseLookup.Found(release))
        val controller = controller(this, source, FakeStorage())

        controller.checkNow()
        testScheduler.advanceUntilIdle()
        controller.clearLastOutcome()

        assertNull(controller.state.value.lastOutcome)
        assertSame(release, controller.state.value.availableRelease)
    }
}
