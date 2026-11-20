package com.uacastplayer.data.remote

import com.uacastplayer.core.remote.RemoteCommand
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.SecretKey
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneRemoteReplyMonitoringTest {
    @Test fun `idle receiver EOF is observable without sending any command`() {
        Receiver { _, _, _ -> }.use { receiver ->
            receiver.connect().use { client ->
                awaitDisconnected(client)
                assertThrows(IOException::class.java) { client.send(RemoteCommand.SELECT) }
            }
        }
    }

    @Test fun `acknowledgement silence remains bounded after the idle reader is introduced`() {
        Receiver { input, _, key ->
            assertEquals(RemoteCommand.SELECT, RemoteWire.parseCommand(RemoteCipher.read(input, key), 1))
            assertEquals(-1, input.read())
        }.use { receiver ->
            receiver.connect().use { client ->
                val started = System.nanoTime()
                assertThrows(IOException::class.java) { client.send(RemoteCommand.SELECT) }
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 7_000)
                awaitDisconnected(client)
            }
        }
    }

    @Test fun `slow partial acknowledgement cannot keep the command alive indefinitely`() {
        Receiver { input, output, key ->
            RemoteWire.parseCommand(RemoteCipher.read(input, key), 1)
            val bytes = ByteArrayOutputStream().also {
                RemoteCipher.write(DataOutputStream(it), key, RemoteWire.acknowledgement(1))
            }.toByteArray()
            try {
                bytes.forEach { output.writeByte(it.toInt()); output.flush(); Thread.sleep(200) }
            } catch (_: IOException) { /* The absolute ACK wait closes this dribbling connection. */ }
        }.use { receiver ->
            receiver.connect().use { client ->
                val started = System.nanoTime()
                assertThrows(IOException::class.java) { client.send(RemoteCommand.SELECT) }
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 7_000)
                awaitDisconnected(client)
            }
        }
    }

    @Test fun `local close interrupts a pending acknowledgement without waiting five seconds`() {
        val received = CountDownLatch(1)
        Receiver { input, _, key ->
            RemoteWire.parseCommand(RemoteCipher.read(input, key), 1)
            received.countDown()
            assertEquals(-1, input.read())
        }.use { receiver ->
            receiver.connect().use { client ->
                val result = AtomicReference<Throwable?>()
                val sender = thread(isDaemon = true) {
                    try { assertThrows(IOException::class.java) { client.send(RemoteCommand.SELECT) } }
                    catch (error: Throwable) { result.set(error) }
                }
                try {
                    assertTrue(received.await(2, TimeUnit.SECONDS))
                    client.close()
                    sender.join(2_000)
                    assertFalse(sender.isAlive)
                    result.get()?.let { throw AssertionError("Command did not cancel safely", it) }
                    awaitDisconnected(client)
                } finally { client.close(); sender.join(2_000) }
            }
        }
    }

    @Test fun `an acknowledgement for another command fails closed`() {
        Receiver { input, output, key ->
            RemoteWire.parseCommand(RemoteCipher.read(input, key), 1)
            RemoteCipher.write(output, key, RemoteWire.acknowledgement(42))
            assertEquals(-1, input.read())
        }.use { receiver ->
            receiver.connect().use { client ->
                assertThrows(IOException::class.java) { client.send(RemoteCommand.SELECT) }
                awaitDisconnected(client)
            }
        }
    }

    @Test fun `unsolicited authenticated replies cannot leave a ghost connection`() {
        Receiver { input, output, key ->
            RemoteCipher.write(output, key, RemoteWire.acknowledgement(1))
            assertEquals(-1, input.read())
        }.use { receiver -> receiver.connect().use { awaitDisconnected(it) } }
    }

    @Test fun `closing repeated connections does not leave reply-reader threads alive`() {
        val existing = replyReaders()
        TvRemoteServer("127.0.0.1", { _, _ -> true }, { _, _ -> }).use { server ->
            // Stay within the real receiver's unchanged eight-attempt authentication budget.
            repeat(6) {
                PhoneRemoteClient().use { client ->
                    client.connect(server.endpoint, server.pairingCode)
                    client.send(RemoteCommand.UP)
                }
            }
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (replyReaders().any { it !in existing } && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue(replyReaders().all { it in existing })
    }

    private fun replyReaders(): Set<Thread> = Thread.getAllStackTraces().keys
        .filterTo(HashSet()) { it.name == "UaCastRemoteReplies" && it.isAlive }

    private fun awaitDisconnected(client: PhoneRemoteClient) = runBlocking {
        withTimeout(2_000) { client.awaitDisconnection() }
    }

    private class Receiver(private val script: (DataInputStream, DataOutputStream, SecretKey) -> Unit) : AutoCloseable {
        private val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val current = AtomicReference<Socket?>()
        private val failure = AtomicReference<Throwable?>()
        private val closing = AtomicBoolean()
        private val worker = thread(isDaemon = true) {
            try {
                listener.accept().use { socket ->
                    current.set(socket)
                    socket.soTimeout = 10_000
                    val input = DataInputStream(socket.getInputStream())
                    val output = DataOutputStream(socket.getOutputStream())
                    val salt = RemoteCipher.salt()
                    output.writeInt(RemoteWire.MAGIC)
                    output.writeByte(RemoteWire.VERSION)
                    output.write(salt)
                    output.flush()
                    val key = RemoteCipher.key(CODE, salt)
                    assertTrue(RemoteCipher.read(input, key).contentEquals(RemoteWire.PAIR))
                    RemoteCipher.write(output, key, RemoteWire.ACCEPTED)
                    script(input, output, key)
                }
            } catch (error: Throwable) {
                if (!closing.get() || error !is IOException) failure.set(error)
            }
        }

        fun connect(): PhoneRemoteClient = PhoneRemoteClient().also {
            it.connect(RemoteEndpoint("127.0.0.1", listener.localPort), CODE)
        }

        override fun close() {
            closing.set(true)
            current.get()?.close()
            listener.close()
            worker.join(2_000)
            assertFalse("Scripted receiver must terminate", worker.isAlive)
            failure.get()?.let { throw AssertionError("Receiver script failed", it) }
        }

        private companion object { const val CODE = "12345678" }
    }
}
