package com.uacastplayer.data.remote

import com.uacastplayer.core.remote.RemoteCommand
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import javax.crypto.AEADBadTagException
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteProtocolTest {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    @Test fun `only LAN IPv4 endpoints are allowed`() {
        listOf("10.1.2.3:80", "172.16.0.1:65535", "172.31.255.255:1", "192.168.1.1:1234",
            "127.0.0.1:8080").forEach { assertNotNull(it, RemoteEndpoint.parse(it)) }
        listOf("8.8.8.8:80", "172.32.0.1:80", "192.169.1.1:80", "localhost:80", "10.1.1.1:0",
            "10.1.1.1:65536", "10.1.1.999:80", "http://10.1.1.1:80", "10.1.1.1:80/path",
            "user@10.1.1.1:80", "[::1]:80", "10.1.1.1", "10.01.1.1:80", "10.1.1.1:+80", "")
            .forEach { assertNull(it, RemoteEndpoint.parse(it)) }
    }
    @Test fun `encrypted frame round trips without containing plaintext`() {
        val bytes = frame(RemoteWire.PAIR)
        assertFalse(String(bytes, Charsets.US_ASCII).contains(String(RemoteWire.PAIR, Charsets.US_ASCII)))
        assertArrayEquals(RemoteWire.PAIR, RemoteCipher.read(input(bytes), key))
    }
    @Test fun `tampering with an authenticated frame is rejected`() {
        val bytes = frame(RemoteWire.command(1, RemoteCommand.PLAY_PAUSE))
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) { RemoteCipher.read(input(bytes), key) }
    }
    @Test fun `different nonces produce different ciphertext`() {
        assertFalse(frame(RemoteWire.PAIR).contentEquals(frame(RemoteWire.PAIR)))
    }
    @Test fun `oversized or negative frames fail before allocation`() {
        listOf(-1, 0, 27, 257, Int.MAX_VALUE).forEach { length ->
            val bytes = ByteArrayOutputStream().also { DataOutputStream(it).writeInt(length) }.toByteArray()
            assertThrows(IOException::class.java) { RemoteCipher.read(input(bytes), key) }
        }
    }
    @Test fun `replay and reordered commands are rejected`() {
        assertEquals(RemoteCommand.BACK, RemoteWire.parseCommand(RemoteWire.command(1, RemoteCommand.BACK), 1))
        assertThrows(IOException::class.java) { RemoteWire.parseCommand(RemoteWire.command(1, RemoteCommand.BACK), 2) }
        assertThrows(IOException::class.java) { RemoteWire.parseCommand(RemoteWire.command(3, RemoteCommand.BACK), 2) }
    }
    @Test fun `unknown commands and malformed command bodies are rejected`() {
        val bytes = RemoteWire.command(1, RemoteCommand.UP)
        bytes[bytes.lastIndex] = 127
        assertThrows(IOException::class.java) { RemoteWire.parseCommand(bytes, 1) }
        assertThrows(IOException::class.java) { RemoteWire.parseCommand(ByteArray(10), 1) }
    }
    @Test fun `pairing code attempts have a global budget and expire`() {
        var time = 0L
        val pairing = RemotePairingCode { time }
        assertTrue(pairing.value.matches(Regex("[0-9]{8}")))
        repeat(8) { assertTrue(pairing.allowAttempt()) }
        assertFalse(pairing.allowAttempt())
        time = 60_000_000_000L
        assertTrue(pairing.allowAttempt())
        time = 300_000_000_000L
        assertFalse(pairing.allowAttempt())
    }
    @Test fun `key derivation validates secret format and salt`() {
        assertThrows(IllegalArgumentException::class.java) { RemoteCipher.key("1234", ByteArray(16)) }
        assertThrows(IllegalArgumentException::class.java) { RemoteCipher.key("abcdefgh", ByteArray(16)) }
        assertThrows(IllegalArgumentException::class.java) { RemoteCipher.key("12345678", ByteArray(15)) }
    }
    private fun frame(payload: ByteArray): ByteArray = ByteArrayOutputStream().also {
        RemoteCipher.write(DataOutputStream(it), key, payload)
    }.toByteArray()
    private fun input(bytes: ByteArray) = DataInputStream(ByteArrayInputStream(bytes))
}
