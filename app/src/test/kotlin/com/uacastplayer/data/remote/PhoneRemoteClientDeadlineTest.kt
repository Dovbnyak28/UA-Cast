package com.uacastplayer.data.remote

import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneRemoteClientDeadlineTest {
    @Test fun `receiver byte dribbling cannot extend the absolute client handshake deadline`() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
            listener.soTimeout = 2_000
            val sender = thread(isDaemon = true) {
                listener.accept().use { socket ->
                    try {
                        val output = DataOutputStream(socket.getOutputStream())
                        output.writeInt(RemoteWire.MAGIC)
                        output.writeByte(RemoteWire.VERSION)
                        output.flush()
                        repeat(RemoteCipher.SALT_BYTES) {
                            output.writeByte(0)
                            output.flush()
                            Thread.sleep(80)
                        }
                    } catch (_: IOException) { /* Absolute deadline closes the partial greeting. */ }
                }
            }
            try {
                PhoneRemoteClient(authTimeoutMillis = 500).use { client ->
                    val start = System.nanoTime()
                    assertThrows(IOException::class.java) {
                        client.connect(RemoteEndpoint("127.0.0.1", listener.localPort), "12345678")
                    }
                    assertTrue("Deadline must not reset on every received byte",
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2_000)
                }
            } finally { sender.join(2_000) }
            assertFalse(sender.isAlive)
        }
    }

    @Test fun `closing the client interrupts a pending handshake without waiting for the longer budget`() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
            listener.soTimeout = 2_000
            PhoneRemoteClient().use { client ->
                val finished = CountDownLatch(1)
                val failure = AtomicReference<Throwable?>()
                val connect = thread(isDaemon = true) {
                    try {
                        assertThrows(IOException::class.java) {
                            client.connect(RemoteEndpoint("127.0.0.1", listener.localPort), "12345678")
                        }
                    } catch (error: Throwable) { failure.set(error) } finally { finished.countDown() }
                }
                listener.accept().use { pending ->
                    pending.soTimeout = 2_000
                    client.close()
                    assertTrue(finished.await(2, TimeUnit.SECONDS))
                    assertTrue(pending.getInputStream().read() == -1)
                }
                connect.join(2_000)
                assertFalse(connect.isAlive)
                failure.get()?.let { throw AssertionError("Handshake worker failed", it) }
            }
        }
    }
}
