package com.uacastplayer.app

import com.uacastplayer.core.security.PinHasher
import com.uacastplayer.parentalcontrol.LockedChannelsStorage
import com.uacastplayer.parentalcontrol.ParentalControlPinStorage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentalControlPinRaceTest {
    @Test fun `reset retires verification already dispatched for the previous PIN`() = runTest {
        val pins = Pins().apply { install("1234") }
        val controller = ParentalControlController(Locks, pins, backgroundScope,
            StandardTestDispatcher(testScheduler, "hashing"))
        val verification = async(start = CoroutineStart.UNDISPATCHED) { controller.verifyPin("1234") }
        controller.resetParentalControl()

        assertFalse(verification.await())
        assertFalse(controller.unlockedThisSession.value)
        assertNull(pins.parentalControlPinHash)
    }

    @Test fun `a replaced PIN record cannot be unlocked with the previous record`() = runTest {
        val pins = Pins().apply { install("1234") }
        val controller = ParentalControlController(Locks, pins, backgroundScope,
            StandardTestDispatcher(testScheduler, "hashing"))
        val verification = async(start = CoroutineStart.UNDISPATCHED) { controller.verifyPin("1234") }
        pins.install("5678")

        assertFalse(verification.await())
        assertFalse(controller.unlockedThisSession.value)
        assertTrue(controller.verifyPin("5678"))
    }

    @Test fun `reset wins over an in flight PIN creation`() = runTest {
        val pins = Pins()
        val controller = ParentalControlController(Locks, pins, backgroundScope,
            StandardTestDispatcher(testScheduler, "hashing"))
        val creation = async(start = CoroutineStart.UNDISPATCHED) { controller.setPin("1234") }
        controller.resetParentalControl()

        assertFalse(creation.await())
        assertFalse(controller.isPinSet.value)
        assertNull(pins.parentalControlPinHash)
        assertNull(pins.parentalControlPinSalt)
    }

    @Test fun `only the newest pending PIN change is acknowledged`() = runTest {
        val pins = Pins()
        val controller = ParentalControlController(Locks, pins, backgroundScope,
            StandardTestDispatcher(testScheduler, "hashing"))
        val first = async(start = CoroutineStart.UNDISPATCHED) { controller.setPin("1234") }
        val latest = async(start = CoroutineStart.UNDISPATCHED) { controller.setPin("5678") }

        assertFalse(first.await())
        assertTrue(latest.await())
        assertFalse(controller.verifyPin("1234"))
        assertTrue(controller.verifyPin("5678"))
    }

    private class Pins : ParentalControlPinStorage {
        override var parentalControlPinHash: String? = null
        override var parentalControlPinSalt: String? = null

        fun install(pin: String) {
            val salt = PinHasher.generateSalt()
            setParentalControlPin(PinHasher.hash(pin, salt), salt)
        }
    }

    private object Locks : LockedChannelsStorage {
        override suspend fun load(): Set<String> = emptySet()
        override suspend fun save(keys: Set<String>) = Unit
    }
}
