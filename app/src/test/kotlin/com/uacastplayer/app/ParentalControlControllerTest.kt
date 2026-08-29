package com.uacastplayer.app

import com.uacastplayer.core.security.PinHasher
import com.uacastplayer.parentalcontrol.LockedChannelsStorage
import com.uacastplayer.parentalcontrol.ParentalControlPinStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeLockedChannelsStorage(
    initial: Set<String> = emptySet(),
    private val loadGate: CompletableDeferred<Unit>? = null,
) : LockedChannelsStorage {
    var saved: Set<String> = initial
        private set

    override suspend fun load(): Set<String> {
        val snapshot = saved
        loadGate?.await()
        return snapshot
    }

    override suspend fun save(keys: Set<String>) {
        saved = keys
    }
}

private class FakePinStorage : ParentalControlPinStorage {
    override var parentalControlPinHash: String? = null
    override var parentalControlPinSalt: String? = null
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ParentalControlControllerTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun controller(
        storage: LockedChannelsStorage = FakeLockedChannelsStorage(),
        pins: ParentalControlPinStorage = FakePinStorage(),
    ) = ParentalControlController(storage, pins, TestScope(dispatcher), hashingDispatcher = dispatcher)

    @Test
    fun `late initial read cannot overwrite a lock made while disk IO was pending`() = runTest(dispatcher) {
        val loadGate = CompletableDeferred<Unit>()
        val storage = FakeLockedChannelsStorage(initial = setOf("old"), loadGate = loadGate)
        val controller = controller(storage)
        controller.loadInitial()

        controller.lockChannel("new")
        loadGate.complete(Unit)

        assertEquals(setOf("old", "new"), controller.lockedKeys.value)
        assertEquals(setOf("old", "new"), storage.saved)
    }

    @Test
    fun `locking a channel needs no PIN and persists immediately`() = runTest(dispatcher) {
        val storage = FakeLockedChannelsStorage()
        val controller = controller(storage)
        controller.loadInitial()

        controller.lockChannel("channel-1")

        assertTrue(controller.isLocked("channel-1"))
        assertEquals(setOf("channel-1"), storage.saved)
        // Locking is not an authorization boundary - only removing a lock is.
        assertFalse(controller.unlockedThisSession.value)
    }

    @Test
    fun `locking the same channel twice is a no-op`() = runTest(dispatcher) {
        val controller = controller()
        controller.loadInitial()

        controller.lockChannel("channel-1")
        controller.lockChannel("channel-1")

        assertEquals(setOf("channel-1"), controller.lockedKeys.value)
    }

    @Test
    fun `locked channels are restored on load`() = runTest(dispatcher) {
        val controller = controller(FakeLockedChannelsStorage(setOf("a", "b")))

        controller.loadInitial()

        assertEquals(setOf("a", "b"), controller.lockedKeys.value)
        assertTrue(controller.isLocked("a"))
        assertFalse(controller.isLocked("c"))
    }

    @Test
    fun `a correct PIN unlocks the session, a wrong one changes nothing`() = runTest(dispatcher) {
        val pins = FakePinStorage()
        val controller = controller(pins = pins)
        controller.loadInitial()
        assertTrue(controller.setPin("1234"))

        assertFalse(controller.verifyPin("9999"))
        assertFalse(controller.unlockedThisSession.value)

        assertTrue(controller.verifyPin("1234"))
        assertTrue(controller.unlockedThisSession.value)
    }

    /** Once entered, the PIN covers the rest of the process's lifetime - "until the app is closed"
     * per the feature's design - so a later wrong guess must not revoke it. */
    @Test
    fun `a wrong PIN after a correct one does not re-lock the session`() = runTest(dispatcher) {
        val controller = controller()
        controller.loadInitial()
        controller.setPin("1234")
        controller.verifyPin("1234")

        assertFalse(controller.verifyPin("0000"))

        assertTrue(controller.unlockedThisSession.value)
    }

    @Test
    fun `verifying fails closed when no PIN has ever been set`() = runTest(dispatcher) {
        val controller = controller()
        controller.loadInitial()

        assertFalse(controller.verifyPin("1234"))
        assertFalse(controller.unlockedThisSession.value)
    }

    @Test
    fun `a PIN that is not four digits is rejected and stores nothing`() = runTest(dispatcher) {
        val pins = FakePinStorage()
        val controller = controller(pins = pins)

        for (bad in listOf("", "12", "12345", "12a4", "abcd", " 123")) {
            assertFalse("expected '$bad' to be rejected", controller.setPin(bad))
        }

        assertNull(pins.parentalControlPinHash)
        assertNull(pins.parentalControlPinSalt)
        assertFalse(controller.isPinSet.value)
    }

    /** The PIN must never be recoverable from what is persisted, and two installs choosing the same
     * PIN must not produce the same stored hash. */
    @Test
    fun `the stored PIN is salted and never the plaintext`() = runTest(dispatcher) {
        val first = FakePinStorage()
        val second = FakePinStorage()
        controller(pins = first).setPin("1234")
        controller(pins = second).setPin("1234")

        assertTrue(first.parentalControlPinHash != null && first.parentalControlPinSalt != null)
        assertFalse(first.parentalControlPinHash!!.contains("1234"))
        assertTrue(
            "same PIN must not produce the same stored hash on two installs",
            first.parentalControlPinHash != second.parentalControlPinHash,
        )
        assertTrue(PinHasher.verify("1234", first.parentalControlPinSalt!!, first.parentalControlPinHash!!))
    }

    @Test
    fun `changing the PIN invalidates the old one`() = runTest(dispatcher) {
        val controller = controller()
        controller.setPin("1234")

        assertTrue(controller.setPin("5678"))

        assertFalse(controller.verifyPin("1234"))
        assertTrue(controller.verifyPin("5678"))
    }

    @Test
    fun `unlocking a channel permanently removes it from the locked set`() = runTest(dispatcher) {
        val storage = FakeLockedChannelsStorage(setOf("a", "b"))
        val controller = controller(storage)
        controller.loadInitial()

        controller.unlockChannelPermanently("a")

        assertEquals(setOf("b"), controller.lockedKeys.value)
        assertEquals(setOf("b"), storage.saved)
    }

    /** The "forgot PIN" escape hatch. It deliberately needs no PIN - but it must not leave locked
     * channels behind, or the user would be left with channels they can never unlock again. */
    @Test
    fun `reset clears the PIN and every locked channel`() = runTest(dispatcher) {
        val storage = FakeLockedChannelsStorage(setOf("a", "b"))
        val pins = FakePinStorage()
        val controller = controller(storage, pins)
        controller.loadInitial()
        controller.setPin("1234")
        controller.verifyPin("1234")

        controller.resetParentalControl()

        assertNull(pins.parentalControlPinHash)
        assertNull(pins.parentalControlPinSalt)
        assertFalse(controller.isPinSet.value)
        assertFalse(controller.unlockedThisSession.value)
        assertEquals(emptySet<String>(), controller.lockedKeys.value)
        assertEquals(emptySet<String>(), storage.saved)
    }

    /** unlockedThisSession is in-memory only by design - a fresh process starts locked again. This
     * stands in for that restart: a new controller over the same persisted state. */
    @Test
    fun `a new controller over the same storage starts locked again`() = runTest(dispatcher) {
        val storage = FakeLockedChannelsStorage()
        val pins = FakePinStorage()
        val first = controller(storage, pins)
        first.loadInitial()
        first.setPin("1234")
        first.lockChannel("a")
        first.verifyPin("1234")
        assertTrue(first.unlockedThisSession.value)

        val restarted = controller(storage, pins)
        restarted.loadInitial()

        assertFalse(restarted.unlockedThisSession.value)
        assertTrue(restarted.isPinSet.value)
        assertTrue(restarted.isLocked("a"))
    }
}
