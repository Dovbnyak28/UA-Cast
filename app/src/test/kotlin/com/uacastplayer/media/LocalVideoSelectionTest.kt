package com.uacastplayer.media

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.data.media.LocalVideoReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LocalVideoSelectionTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val application get() = ApplicationProvider.getApplicationContext<Application>()
    private val first = LocalVideo("content://media/video/1", "First.mp4", "video/mp4")
    private val second = first.copy(uri = "content://media/video/2", displayName = "Second.mp4")

    @Before fun main() { Dispatchers.setMain(dispatcher) }
    @After fun clear() { store.clear(); Dispatchers.resetMain() }

    private fun owner(reader: LocalVideoReader): LocalVideoOpenViewModel =
        LocalVideoOpenViewModel(application, reader).also { store.put("video", it) }

    @Test fun aRapidReplacementCannotBeOverwrittenByTheOldRead() = runTest(dispatcher) {
        var cancelled = false
        val owner = owner(LocalVideoReader { uri, _ ->
            if (uri == first.uri) {
                try { delay(1000); first } finally { cancelled = true }
            } else second
        })
        owner.open(first.uri)
        runCurrent()
        owner.open(second.uri)
        advanceUntilIdle()
        assertTrue(cancelled)
        assertSame(second, owner.state.value.video)
        assertFalse(owner.state.value.loading)
        assertFalse(owner.state.value.error)
    }

    @Test fun userCancellationDoesNotBecomeAnErrorOrALateOpen() = runTest(dispatcher) {
        val owner = owner(LocalVideoReader { _, _ -> delay(1000); first })
        owner.open(first.uri)
        runCurrent()
        owner.cancelSelection()
        advanceUntilIdle()
        assertEquals(LocalVideoSelectionState(), owner.state.value)
    }

    @Test fun anUnreadableSourceFailsSafelyAndTheNextSelectionCanSucceed() = runTest(dispatcher) {
        val owner = owner(LocalVideoReader { uri, _ ->
            if (uri == first.uri) throw SecurityException("grant revoked") else second
        })
        owner.open(first.uri)
        advanceUntilIdle()
        assertTrue(owner.state.value.error)
        assertFalse(owner.state.value.loading)
        owner.open(second.uri)
        advanceUntilIdle()
        assertSame(second, owner.state.value.video)
        assertFalse(owner.state.value.error)
    }

    @Test fun consumingAnOldResultCannotEraseTheNewSelection() = runTest(dispatcher) {
        val owner = owner(LocalVideoReader { _, _ -> second })
        owner.open(second.uri)
        advanceUntilIdle()
        val previousRequest = owner.state.value.requestId
        owner.open(second.uri)
        advanceUntilIdle()
        owner.consumed(previousRequest)
        assertSame(second, owner.state.value.video)
        owner.consumed(owner.state.value.requestId)
        assertNull(owner.state.value.video)
    }

    @Test fun clearingTheViewModelCancelsItsOutstandingRead() = runTest(dispatcher) {
        var cancelled = false
        val owner = owner(LocalVideoReader { _, _ -> try { awaitCancellation() } finally { cancelled = true } })
        owner.open(first.uri)
        runCurrent()
        store.clear()
        runCurrent()
        assertTrue(cancelled)
        assertNull(owner.state.value.video)
    }
}
