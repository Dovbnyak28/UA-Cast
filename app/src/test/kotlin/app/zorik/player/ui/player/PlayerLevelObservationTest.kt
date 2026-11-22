package app.zorik.player.ui.player

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerLevelObservationTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun mainDispatcher() { Dispatchers.setMain(dispatcher) }
    @After fun resetDispatcher() { Dispatchers.resetMain() }

    @Test fun samplingPausesWithTheActivityAndStopsWhenTheSheetCloses() = runTest(dispatcher) {
        val owner = LevelLifecycleOwner()
        var volume = 0.2f
        var reads = 0
        val observed = mutableListOf<Float>()
        val job = launch {
            observeResumedPlayerLevels(owner.lifecycle) { reads++; observed.add(volume) }
        }
        runCurrent()
        assertEquals(0, reads)
        owner.registry.currentState = Lifecycle.State.RESUMED
        runCurrent()
        assertEquals(listOf(0.2f), observed)
        volume = 0.8f
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(0.2f, 0.8f), observed)
        owner.registry.currentState = Lifecycle.State.STARTED
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(2, reads)
        volume = 0.4f
        owner.registry.currentState = Lifecycle.State.RESUMED
        runCurrent()
        assertEquals(listOf(0.2f, 0.8f, 0.4f), observed)
        job.cancelAndJoin()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(3, reads)
    }

    @Test fun destroyingTheOwnerStopsSamplingWithoutAnExplicitSheetCancellation() = runTest(dispatcher) {
        val owner = LevelLifecycleOwner()
        var reads = 0
        val job = launch {
            observeResumedPlayerLevels(owner.lifecycle) { reads++ }
        }
        runCurrent()
        owner.registry.currentState = Lifecycle.State.RESUMED
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(2, reads)
        owner.registry.currentState = Lifecycle.State.DESTROYED
        job.join()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(2, reads)
    }
}

private class LevelLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry.createUnsafe(this)
    override val lifecycle: Lifecycle get() = registry
}
