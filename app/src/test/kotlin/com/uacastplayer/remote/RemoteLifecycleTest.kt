package com.uacastplayer.remote

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.core.remote.RemoteCommand
import com.uacastplayer.data.remote.PhoneRemoteClient
import com.uacastplayer.data.remote.TvRemoteServer
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteLifecycleTest {
    private val application get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `TV stop and ViewModel destruction retire queued commands and close connections`() = onMain {
        val store = ViewModelStore()
        val receiver = TvRemoteReceiverViewModel(application) { "127.0.0.1" }
        store.put("receiver", receiver)
        try {
            receiver.start()
            val ready = withTimeout(5_000) { receiver.state.first { it.endpoint != null } }
            PhoneRemoteClient().use { client ->
                withContext(Dispatchers.IO) { client.connect(checkNotNull(ready.endpoint), checkNotNull(ready.code)) }
                withTimeout(5_000) { receiver.state.first { it.paired } }
                withContext(Dispatchers.IO) { client.send(RemoteCommand.SELECT) }
                val event = withTimeout(5_000) { receiver.commands.first() }
                assertTrue(receiver.isCurrent(event))
                assertFalse(receiver.isCurrent(event.copy(createdAtNanos = System.nanoTime() - 2_000_000_000)))
                var applied = 0
                receiver.stop()
                receiver.consume(event) { applied++ }
                assertEquals(0, applied)
                assertEquals(TvRemoteState(), receiver.state.value)
                withContext(Dispatchers.IO) {
                    assertThrows(IOException::class.java) { client.send(RemoteCommand.BACK) }
                }
            }
        } finally { store.clear() }
    }

    @Test fun `phone disconnect cancels the socket job and cannot retain connected state`() = onMain {
        val store = ViewModelStore()
        val phone = PhoneRemoteViewModel()
        store.put("phone", phone)
        TvRemoteServer("127.0.0.1", { _, _ -> true }, { _, _ -> }).use { server ->
            try {
                phone.connect(server.endpoint.toString(), server.pairingCode)
                withTimeout(5_000) { phone.state.first { it.connected } }
                phone.disconnect()
                assertEquals(PhoneRemoteState(), phone.state.value)
                assertFalse(phone.send(RemoteCommand.SELECT))
                delay(100)
                assertEquals(PhoneRemoteState(), phone.state.value)
            } finally { store.clear() }
        }
    }

    @Test fun `receiver closing an idle socket clears phone connected state without a button press`() = onMain {
        val store = ViewModelStore()
        val phone = PhoneRemoteViewModel()
        store.put("phone", phone)
        TvRemoteServer("127.0.0.1", { _, _ -> error("No command should be sent") }, { _, _ -> }).use { server ->
            try {
                phone.connect(server.endpoint.toString(), server.pairingCode)
                withTimeout(5_000) { phone.state.first { it.connected } }
                server.close()
                val disconnected = withTimeout(2_000) { phone.state.first { it.failed } }
                assertFalse(disconnected.connected)
                assertFalse(disconnected.connecting)
                assertFalse(phone.send(RemoteCommand.SELECT))
            } finally { store.clear() }
        }
    }

    @Test fun `cancelled TV startup cannot publish a listening endpoint afterwards`() = onMain {
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val store = ViewModelStore()
        val receiver = TvRemoteReceiverViewModel(application) {
            entered.countDown()
            check(resume.await(5, TimeUnit.SECONDS))
            "127.0.0.1"
        }
        store.put("receiver", receiver)
        try {
            receiver.start()
            withContext(Dispatchers.IO) { assertTrue(entered.await(5, TimeUnit.SECONDS)) }
            receiver.stop()
            resume.countDown()
            delay(200)
            assertEquals(TvRemoteState(), receiver.state.value)
            receiver.start()
            withTimeout(5_000) { receiver.state.first { it.endpoint != null } }
            store.clear()
            assertEquals(TvRemoteState(), receiver.state.value)
        } finally { resume.countDown(); store.clear() }
    }

    @Test fun `old receiver EOF cannot clear a replacement phone connection`() = onMain {
        val store = ViewModelStore()
        val phone = PhoneRemoteViewModel()
        val delivered = CountDownLatch(1)
        store.put("phone", phone)
        TvRemoteServer("127.0.0.1", { _, _ -> true }, { _, _ -> }).use { first ->
            TvRemoteServer("127.0.0.1", { _, _ -> delivered.countDown(); true }, { _, _ -> }).use { second ->
                try {
                    phone.connect(first.endpoint.toString(), first.pairingCode)
                    withTimeout(5_000) { phone.state.first { it.connected } }
                    phone.connect(second.endpoint.toString(), second.pairingCode)
                    first.close()
                    val connected = withTimeout(5_000) { phone.state.first { it.connected } }
                    assertEquals(second.endpoint, connected.endpoint)
                    assertTrue(phone.send(RemoteCommand.SELECT))
                    withContext(Dispatchers.IO) { assertTrue(delivered.await(2, TimeUnit.SECONDS)) }
                    delay(100)
                    assertTrue(phone.state.value.connected)
                    assertFalse(phone.state.value.failed)
                } finally { store.clear() }
            }
        }
    }

    @Test fun `unavailable TV LAN and invalid phone input stay retryable`() = onMain {
        val store = ViewModelStore()
        val receiver = TvRemoteReceiverViewModel(application) { null }
        val phone = PhoneRemoteViewModel()
        store.put("receiver", receiver)
        store.put("phone", phone)
        try {
            receiver.start()
            withTimeout(5_000) { receiver.state.first { it.failed } }
            assertFalse(receiver.state.value.starting)
            phone.connect("https://example.test/", "12345678")
            assertTrue(phone.state.value.failed)
            assertFalse(phone.state.value.connecting)
            assertFalse(phone.send(RemoteCommand.SELECT))
        } finally { store.clear() }
    }

    private fun onMain(test: suspend () -> Unit) {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { main ->
            Dispatchers.setMain(main)
            try { runBlocking(main) { test() } } finally { Dispatchers.resetMain() }
        }
    }
}
