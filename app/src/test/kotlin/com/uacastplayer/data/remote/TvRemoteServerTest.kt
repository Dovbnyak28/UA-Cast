package com.uacastplayer.data.remote

import com.uacastplayer.core.remote.RemoteCommand
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class TvRemoteServerTest {
    private class Fixture : AutoCloseable {
        val commands = CopyOnWriteArrayList<Pair<Long, RemoteCommand>>()
        val connections = CopyOnWriteArrayList<Pair<Long, Boolean>>()
        val server = TvRemoteServer("127.0.0.1", { session, command -> commands.add(session to command); true },
            { session, paired -> connections.add(session to paired) })
        fun connect(): PhoneRemoteClient = PhoneRemoteClient().also { it.connect(server.endpoint, server.pairingCode) }
        override fun close() = server.close()
    }
    @Test fun `all allowed commands arrive in order on a real encrypted socket`() {
        Fixture().use { fixture ->
            fixture.connect().use { client -> RemoteCommand.entries.forEach(client::send) }
            assertEquals(RemoteCommand.entries, fixture.commands.map { it.second })
            assertEquals(1, fixture.commands.map { it.first }.distinct().size)
        }
    }
    @Test fun `wrong pairing code never receives a session or delivers input`() {
        Fixture().use { fixture ->
            val wrong = if (fixture.server.pairingCode == "00000000") "11111111" else "00000000"
            PhoneRemoteClient().use { client ->
                assertThrows(IOException::class.java) { client.connect(fixture.server.endpoint, wrong) }
            }
            assertTrue(fixture.commands.isEmpty())
            assertTrue(fixture.connections.isEmpty())
            fixture.connect().use { it.send(RemoteCommand.SELECT) }
            assertEquals(listOf(RemoteCommand.SELECT), fixture.commands.map { it.second })
        }
    }
    @Test fun `replacement phone retires the old connection without stopping the new one`() {
        Fixture().use { fixture ->
            fixture.connect().use { first ->
                first.send(RemoteCommand.UP)
                fixture.connect().use { second ->
                    second.send(RemoteCommand.DOWN)
                    assertThrows(IOException::class.java) { first.send(RemoteCommand.LEFT) }
                    second.send(RemoteCommand.RIGHT)
                }
            }
            assertEquals(listOf(RemoteCommand.UP, RemoteCommand.DOWN, RemoteCommand.RIGHT),
                fixture.commands.map { it.second })
            assertNotEquals(fixture.commands[0].first, fixture.commands[1].first)
            assertEquals(fixture.commands[1].first, fixture.commands[2].first)
        }
    }
    @Test fun `stop interrupts authenticated sockets and frees the listening port`() {
        Fixture().use { fixture ->
            fixture.connect().use { client ->
                client.send(RemoteCommand.SELECT)
                fixture.server.close()
                fixture.server.close()
                assertThrows(IOException::class.java) { client.send(RemoteCommand.BACK) }
                assertThrows(IOException::class.java) {
                    Socket(fixture.server.endpoint.host, fixture.server.endpoint.port).close()
                }
            }
            assertEquals(listOf(RemoteCommand.SELECT), fixture.commands.map { it.second })
        }
    }
    @Test fun `replayed encrypted command closes only its own connection`() {
        Fixture().use { fixture ->
            Peer(fixture.server).use { peer ->
                RemoteCipher.write(peer.output, peer.key, RemoteWire.command(1, RemoteCommand.UP))
                assertArrayEquals(RemoteWire.acknowledgement(1), RemoteCipher.read(peer.input, peer.key))
                RemoteCipher.write(peer.output, peer.key, RemoteWire.command(1, RemoteCommand.DOWN))
                assertThrows(IOException::class.java) { RemoteCipher.read(peer.input, peer.key) }
            }
            fixture.connect().use { it.send(RemoteCommand.BACK) }
            assertEquals(listOf(RemoteCommand.UP, RemoteCommand.BACK), fixture.commands.map { it.second })
        }
    }
    @Test fun `oversized frame cannot allocate a large buffer or issue a command`() {
        Fixture().use { fixture ->
            Peer(fixture.server).use { peer ->
                peer.output.writeInt(Int.MAX_VALUE)
                peer.output.flush()
                assertEquals(-1, peer.input.read())
            }
            assertTrue(fixture.commands.isEmpty())
        }
    }
    @Test fun `stop also closes pending unauthenticated clients`() {
        Fixture().use { fixture ->
            Socket(fixture.server.endpoint.host, fixture.server.endpoint.port).use { socket ->
                socket.soTimeout = 5_000
                val input = DataInputStream(socket.getInputStream())
                assertEquals(RemoteWire.MAGIC, input.readInt())
                input.readUnsignedByte()
                input.readFully(ByteArray(RemoteCipher.SALT_BYTES))
                fixture.server.close()
                assertEquals(-1, input.read())
            }
            assertTrue(fixture.commands.isEmpty())
        }
    }
    @Test fun `slow dribbling cannot extend the absolute authentication deadline`() {
        TvRemoteServer("127.0.0.1", { _, _ -> error("Unauthenticated input") }, { _, _ -> },
            authTimeoutMillis = 500).use { server ->
            Socket(server.endpoint.host, server.endpoint.port).use { socket ->
                socket.soTimeout = 2_000
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                input.readInt()
                input.readUnsignedByte()
                input.readFully(ByteArray(RemoteCipher.SALT_BYTES))
                output.writeInt(64)
                output.flush()
                val dribbler = thread(isDaemon = true) {
                    try {
                        repeat(30) { output.writeByte(0); output.flush(); Thread.sleep(80) }
                    } catch (_: IOException) { /* deadline interrupts byte dribbling */ }
                }
                assertEquals(-1, input.read())
                dribbler.join(2_000)
                assertTrue(!dribbler.isAlive)
            }
        }
    }
    private class Peer(server: TvRemoteServer) : AutoCloseable {
        private val socket = Socket(server.endpoint.host, server.endpoint.port).apply { soTimeout = 5_000 }
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        val key: SecretKey
        init {
            assertEquals(RemoteWire.MAGIC, input.readInt())
            assertEquals(RemoteWire.VERSION, input.readUnsignedByte())
            val salt = ByteArray(RemoteCipher.SALT_BYTES).also(input::readFully)
            key = RemoteCipher.key(server.pairingCode, salt)
            RemoteCipher.write(output, key, RemoteWire.PAIR)
            assertArrayEquals(RemoteWire.ACCEPTED, RemoteCipher.read(input, key))
        }
        override fun close() = socket.close()
    }
}
