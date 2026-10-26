package com.uacastplayer.remote

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.MainActivity
import com.uacastplayer.core.remote.RemoteCommand
import com.uacastplayer.data.remote.PhoneRemoteClient
import com.uacastplayer.data.remote.TvRemoteServer
import com.uacastplayer.data.remote.RemoteWire
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android Main dispatcher and Activity lifecycle; loopback fixtures never change stored user data. */
@RunWith(AndroidJUnit4::class)
class RemoteLifecycleInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun backgroundAndActivityRecreationEndControlWithoutAutomaticReconnection(): Unit = runBlocking {
        val phone = activityPhone()
        TvRemoteServer("127.0.0.1", { _, _ -> true }, { _, _ -> }).use { server ->
            try {
                connect(phone, server)
                rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
                assertEquals(PhoneRemoteState(), phone.state.value)
                assertFalse(phone.send(RemoteCommand.SELECT))
                rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
                assertEquals(PhoneRemoteState(), phone.state.value)
                connect(phone, server)
                rule.activityRule.scenario.recreate()
                assertEquals(PhoneRemoteState(), phone.state.value)
                assertFalse(phone.send(RemoteCommand.NEXT))
                assertTrue("Activity should retain the same disconnected ViewModel", phone === activityPhone())
            } finally { onMain { phone.disconnect() } }
        }
    }

    @Test fun cancelledHandshakeCannotClearTheReplacementConnection(): Unit = runBlocking {
        val phone = activityPhone()
        val received = Channel<RemoteCommand>(1)
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { stalled ->
            stalled.soTimeout = TIMEOUT_MILLIS.toInt()
            TvRemoteServer("127.0.0.1", { _, command -> received.trySend(command).isSuccess }, { _, _ -> }).use { server ->
                try {
                    onMain { phone.connect("127.0.0.1:${stalled.localPort}", "12345678") }
                    withContext(Dispatchers.IO) { stalled.accept() }.use { pending ->
                        assertTrue(phone.state.value.connecting)
                        onMain { phone.disconnect() }
                        connect(phone, server)
                        onMain { assertTrue(phone.send(RemoteCommand.SELECT)) }
                        assertEquals(RemoteCommand.SELECT, withTimeout(TIMEOUT_MILLIS) { received.receive() })
                        assertTrue(phone.state.value.connected)
                        pending.soTimeout = TIMEOUT_MILLIS.toInt()
                        assertEquals("Cancelled handshake must close its socket", -1,
                            withContext(Dispatchers.IO) { pending.getInputStream().read() })
                    }
                } finally { onMain { phone.disconnect() }; received.close() }
            }
        }
    }

    @Test fun receiverDisconnectIsRetryableAndDoesNotClaimCommandsWereDelivered(): Unit = runBlocking {
        val phone = activityPhone()
        val first = TvRemoteServer("127.0.0.1", { _, _ -> true }, { _, _ -> })
        val received = Channel<RemoteCommand>(1)
        try {
            connect(phone, first)
            first.close()
            // Receiver EOF must be observed without a button press; queue admission is not delivery.
            val failed = withTimeout(TIMEOUT_MILLIS) { phone.state.first { it.failed } }
            assertFalse(failed.connected)
            assertFalse(phone.send(RemoteCommand.NEXT))
            TvRemoteServer("127.0.0.1", { _, command -> received.trySend(command).isSuccess }, { _, _ -> }).use { next ->
                connect(phone, next)
                onMain { assertTrue(phone.send(RemoteCommand.NEXT)) }
                assertEquals(RemoteCommand.NEXT, withTimeout(TIMEOUT_MILLIS) { received.receive() })
            }
        } finally { first.close(); onMain { phone.disconnect() }; received.close() }
    }

    @Test fun replacementAndViewModelDestructionRetireOldCommandsAndSockets(): Unit = runBlocking {
        val store = ViewModelStore()
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val receiver = withContext(Dispatchers.Main.immediate) {
            TvRemoteReceiverViewModel(application) { "127.0.0.1" }.also { store.put("receiver", it); it.start() }
        }
        try {
            val ready = withTimeout(TIMEOUT_MILLIS) { receiver.state.first { it.endpoint != null } }
            PhoneRemoteClient().use { old ->
                withContext(Dispatchers.IO) {
                    old.connect(checkNotNull(ready.endpoint), checkNotNull(ready.code))
                    old.send(RemoteCommand.SELECT)
                }
                val oldEvent = withTimeout(TIMEOUT_MILLIS) { receiver.commands.first() }
                PhoneRemoteClient().use { replacement ->
                    withContext(Dispatchers.IO) {
                        replacement.connect(checkNotNull(ready.endpoint), checkNotNull(ready.code))
                        replacement.send(RemoteCommand.NEXT)
                    }
                    val latest = withTimeout(TIMEOUT_MILLIS) { receiver.commands.first() }
                    assertNotEquals(oldEvent.connection, latest.connection)
                    var applied = 0
                    onMain {
                        // Refresh only the test timestamp to prove rejection is by ownership, not just age.
                        receiver.consume(oldEvent.copy(createdAtNanos = System.nanoTime())) { applied++ }
                        receiver.consume(latest) { applied++ }
                    }
                    assertEquals(1, applied)
                    assertTrue(receiver.state.value.paired)
                    onMain { store.clear() }
                    assertEquals(TvRemoteState(), receiver.state.value)
                    onMain { receiver.consume(latest) { applied++ } }
                    assertEquals(1, applied)
                    withContext(Dispatchers.IO) {
                        assertThrows(IOException::class.java) { replacement.send(RemoteCommand.BACK) }
                    }
                }
            }
        } finally { onMain { store.clear() } }
    }

    private fun activityPhone(): PhoneRemoteViewModel {
        lateinit var phone: PhoneRemoteViewModel
        rule.activityRule.scenario.onActivity { phone = ViewModelProvider(it)[PhoneRemoteViewModel::class.java] }
        return phone
    }

    private suspend fun connect(phone: PhoneRemoteViewModel, server: TvRemoteServer) {
        onMain { phone.connect(server.endpoint.toString(), server.pairingCode) }
        // Only authentication has a larger production budget on old Android crypto providers.
        // Delivery, cancellation, socket EOF and state changes retain their original 8s assertions.
        withTimeout(RemoteWire.AUTH_TIMEOUT_MILLIS + TIMEOUT_MILLIS) { phone.state.first { it.connected } }
    }

    private suspend fun onMain(action: () -> Unit) = withContext(Dispatchers.Main.immediate) { action() }

    private companion object { const val TIMEOUT_MILLIS = 8_000L }
}
