package com.uacastplayer.data.remote

import com.uacastplayer.core.remote.RemoteCommand
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.Timer
import kotlin.concurrent.thread
import kotlin.concurrent.timerTask
import javax.crypto.SecretKey
import kotlinx.coroutines.CompletableDeferred

/** A single owner sends commands serially. Closing interrupts connect, handshake, ACK reads and idle sockets. */
class PhoneRemoteClient(private val authTimeoutMillis: Int = RemoteWire.AUTH_TIMEOUT_MILLIS) : AutoCloseable {
    private val socket = Socket()
    private val closed = AtomicBoolean(false)
    private val disconnected = CompletableDeferred<Unit>()
    private val handshakeDeadline = AtomicReference<Timer?>()
    private val pendingReply = AtomicReference<CompletableFuture<ByteArray>?>()
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var key: SecretKey? = null
    private var sequence = 0L

    fun connect(endpoint: RemoteEndpoint, code: String) {
        check(!closed.get())
        require(authTimeoutMillis > 0)
        // Socket timeout alone resets on every read. The owned timer also bounds header/frame
        // dribbling and the platform's slow PBKDF2 without lowering its iteration count.
        val deadline = Timer("UaCastRemoteHandshake", true)
        handshakeDeadline.set(deadline)
        try {
            deadline.schedule(timerTask { runCatching { socket.close() } }, authTimeoutMillis.toLong())
            authenticate(endpoint, code)
        } finally { deadline.cancel(); handshakeDeadline.compareAndSet(deadline, null) }
        // Exactly one reader owns all post-pairing frames. Waiting without polling/heartbeats
        // detects EOF even when no buttons are pressed and preserves the TV's idle expiry.
        socket.soTimeout = 0
        thread(name = "UaCastRemoteReplies", isDaemon = true) { readReplies() }
    }

    private fun authenticate(endpoint: RemoteEndpoint, code: String) {
        socket.connect(InetSocketAddress(endpoint.host, endpoint.port), TIMEOUT_MILLIS)
        socket.soTimeout = authTimeoutMillis
        socket.tcpNoDelay = true
        val incoming = DataInputStream(socket.getInputStream())
        val outgoing = DataOutputStream(socket.getOutputStream())
        if (incoming.readInt() != RemoteWire.MAGIC || incoming.readUnsignedByte() != RemoteWire.VERSION) {
            throw IOException("Unsupported remote receiver")
        }
        val salt = ByteArray(RemoteCipher.SALT_BYTES).also(incoming::readFully)
        val sessionKey = RemoteCipher.key(code, salt)
        RemoteCipher.write(outgoing, sessionKey, RemoteWire.PAIR)
        if (!RemoteCipher.read(incoming, sessionKey).contentEquals(RemoteWire.ACCEPTED)) {
            throw IOException("Pairing failed")
        }
        input = incoming
        output = outgoing
        key = sessionKey
    }

    fun send(command: RemoteCommand) {
        requireOpenConnection()
        val outgoing = checkNotNull(output)
        val sessionKey = checkNotNull(key)
        val reply = CompletableFuture<ByteArray>()
        check(pendingReply.compareAndSet(null, reply)) { "Remote commands must be sent serially" }
        try {
            requireOpenConnection()
            sequence++
            RemoteCipher.write(outgoing, sessionKey, RemoteWire.command(sequence, command))
            if (!awaitReply(reply).contentEquals(RemoteWire.acknowledgement(sequence))) {
                throw IOException("Invalid command acknowledgement")
            }
        } catch (error: IOException) {
            close()
            throw error
        } finally { pendingReply.compareAndSet(reply, null) }
    }

    suspend fun awaitDisconnection() { disconnected.await() }

    private fun requireOpenConnection() {
        if (closed.get()) throw IOException("Remote connection closed")
    }

    private fun awaitReply(reply: CompletableFuture<ByteArray>): ByteArray = try {
        reply.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
    } catch (error: TimeoutException) {
        throw IOException("Remote acknowledgement timed out", error)
    } catch (error: ExecutionException) {
        throw IOException("Remote connection closed", error)
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()
        throw IOException("Remote command interrupted", error)
    }

    @Suppress("TooGenericExceptionCaught") // Any socket/framing/crypto failure retires this paired connection.
    private fun readReplies() {
        try {
            val incoming = checkNotNull(input)
            val sessionKey = checkNotNull(key)
            while (!closed.get()) {
                val payload = RemoteCipher.read(incoming, sessionKey)
                val reply = pendingReply.get() ?: throw IOException("Unexpected remote acknowledgement")
                if (!reply.complete(payload)) throw IOException("Duplicate remote acknowledgement")
            }
        } catch (_: Exception) {
            // No pairing secret or plaintext is logged; the owner observes disconnection below.
        } finally { runCatching { close() } }
    }

    override fun close() {
        closed.set(true)
        handshakeDeadline.getAndSet(null)?.cancel()
        try { socket.close() } finally {
            pendingReply.getAndSet(null)?.completeExceptionally(IOException("Remote connection closed"))
            disconnected.complete(Unit)
        }
    }

    private companion object { const val TIMEOUT_MILLIS = 5_000 }
}
