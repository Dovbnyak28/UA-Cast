package com.uacastplayer.data.icons

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.icons.IconFailurePolicy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IconFailureStoreConcurrencyTest {
    private val application: Application get() = ApplicationProvider.getApplicationContext()
    private val preferences get() = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val now = IconFailurePolicy.PERMANENT_TTL_MILLIS + 1

    @Before fun resetFixture() {
        preferences.edit().clear().commit()
    }

    @Test fun pruningAnExpiredSnapshotMustNotDeleteARenewedFailure() {
        val snapshots = SnapshotPreferences(preferences)
        val store = IconFailureStore(FailureContext(application, snapshots))
        store.recordFailure(URL, isPermanent = true, nowMillis = 0)
        // Exactly the interleaving of a writer completing after getAll captured its old snapshot.
        // The wrapper changes no storage semantics: all reads/writes still delegate to real prefs.
        snapshots.afterSnapshot.set { store.recordFailure(URL, isPermanent = true, nowMillis = now) }

        store.pruneExpiredFailures(now)

        assertTrue("pruning must retain the fresh failure", store.shouldSkip(URL, now))
        assertEquals(now, preferences.getLong(Fingerprint.of(URL), -1))
    }

    @Test fun expiryLookupMustNotRemoveAPermanentFailureWrittenByAnotherThread() {
        val snapshots = SnapshotPreferences(preferences)
        val store = IconFailureStore(FailureContext(application, snapshots))
        val writerStore = IconFailureStore(FailureContext(application, snapshots))
        store.recordFailure(URL, isPermanent = true, nowMillis = 0)
        val readStarted = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        snapshots.afterLongRead.set {
            readStarted.countDown()
            check(releaseRead.await(5, TimeUnit.SECONDS))
        }
        val workers = Executors.newFixedThreadPool(2)
        try {
            val lookup = workers.submit<Boolean> { store.shouldSkip(URL, now) }
            assertTrue(readStarted.await(5, TimeUnit.SECONDS))
            val writeStarted = CountDownLatch(1)
            val writeFinished = CountDownLatch(1)
            val write = workers.submit {
                writeStarted.countDown()
                writerStore.recordFailure(URL, isPermanent = true, nowMillis = now)
                writeFinished.countDown()
            }
            assertTrue(writeStarted.await(5, TimeUnit.SECONDS))
            // Before the fix the writer finishes and the lookup subsequently removes its value.
            // With atomic expiry removal it waits; release the reader either way without sleeping.
            writeFinished.await(500, TimeUnit.MILLISECONDS)
            releaseRead.countDown()
            lookup.get(5, TimeUnit.SECONDS)
            write.get(5, TimeUnit.SECONDS)

            assertTrue("a concurrent fresh record must survive expired-record cleanup", store.shouldSkip(URL, now))
        } finally {
            releaseRead.countDown()
            workers.shutdownNow()
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test fun pruningDropsExpiredRecordsButPreservesFreshRecordsAndUnrelatedKeys() {
        val store = IconFailureStore(application)
        store.recordFailure(URL, isPermanent = true, nowMillis = 0)
        store.recordFailure(FRESH_URL, isPermanent = true, nowMillis = now)
        store.recordFailure(TRANSIENT_URL, isPermanent = false, nowMillis = 0)
        store.recordFailure(FRESH_TRANSIENT_URL, isPermanent = false, nowMillis = now)
        preferences.edit().putString("future_metadata", "keep").commit()

        store.pruneExpiredFailures(now)

        assertFalse(preferences.contains(Fingerprint.of(URL)))
        assertFalse(store.shouldSkip(TRANSIENT_URL, now))
        assertTrue(store.shouldSkip(FRESH_URL, now))
        assertTrue(store.shouldSkip(FRESH_TRANSIENT_URL, now))
        assertEquals("keep", preferences.getString("future_metadata", null))
        assertEquals(1, preferences.getInt("__schema_version__", 0))
    }

    @Test fun networkRecoveryClearsOnlyTransientFailures() {
        val store = IconFailureStore(application)
        store.recordFailure(URL, isPermanent = true, nowMillis = now)
        store.recordFailure(TRANSIENT_URL, isPermanent = false, nowMillis = now)

        store.clearTransientFailures()

        assertFalse(store.shouldSkip(TRANSIENT_URL, now))
        assertTrue(store.shouldSkip(URL, now))
        assertTrue(IconFailureStore(application).shouldSkip(URL, now))
        assertFalse(IconFailureStore(application).shouldSkip(TRANSIENT_URL, now))
    }

    @Test fun aFutureTimestampDoesNotKeepAnIconBlockedAfterClockCorrection() {
        val store = IconFailureStore(application)
        store.recordFailure(URL, isPermanent = true, nowMillis = now + 10_000)
        store.recordFailure(TRANSIENT_URL, isPermanent = false, nowMillis = now + 10_000)

        assertFalse(store.shouldSkip(URL, now))
        assertFalse(store.shouldSkip(TRANSIENT_URL, now))
        assertFalse(preferences.contains(Fingerprint.of(URL)))
        store.recordFailure(URL, isPermanent = true, nowMillis = now + 10_000)
        store.pruneExpiredFailures(now)
        assertFalse(preferences.contains(Fingerprint.of(URL)))
    }

    @Test fun schemaMigrationStillClearsHistoricalFailuresOnlyOnce() {
        val key = Fingerprint.of(URL)
        preferences.edit().putInt("__schema_version__", 0).putLong(key, now).commit()

        val store = IconFailureStore(application)

        assertFalse(store.shouldSkip(URL, now))
        assertEquals(1, preferences.getInt("__schema_version__", 0))
        store.recordFailure(URL, isPermanent = true, nowMillis = now)
        assertTrue(IconFailureStore(application).shouldSkip(URL, now))
    }

    @Test fun anOlderBuildDoesNotClearRecordsFromAFutureSchema() {
        val key = Fingerprint.of(URL)
        preferences.edit().putInt("__schema_version__", 2).putLong(key, now).commit()

        val store = IconFailureStore(application)

        assertTrue(store.shouldSkip(URL, now))
        assertEquals(2, preferences.getInt("__schema_version__", 0))
    }

    private class SnapshotPreferences(private val delegate: SharedPreferences) : SharedPreferences by delegate {
        val afterSnapshot = AtomicReference<(() -> Unit)?>(null)
        val afterLongRead = AtomicReference<(() -> Unit)?>(null)

        override fun getAll(): Map<String, *> = delegate.all.also { afterSnapshot.getAndSet(null)?.invoke() }

        override fun getLong(key: String?, defValue: Long): Long =
            delegate.getLong(key, defValue).also { afterLongRead.getAndSet(null)?.invoke() }
    }

    private class FailureContext(base: Context, private val preferences: SharedPreferences) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            if (name == PREFS_NAME) preferences else super.getSharedPreferences(name, mode)
    }

    private companion object {
        const val PREFS_NAME = "uacast_icon_failures"
        const val URL = "https://icons.test/expired.png"
        const val FRESH_URL = "https://icons.test/fresh.png"
        const val TRANSIENT_URL = "https://icons.test/transient.png"
        const val FRESH_TRANSIENT_URL = "https://icons.test/fresh-transient.png"
    }
}
