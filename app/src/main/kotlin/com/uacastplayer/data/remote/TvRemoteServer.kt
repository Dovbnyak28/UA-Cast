package com.uacastplayer.data.remote

import com.uacastplayer.core.remote.RemoteCommand
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/** Opt-in, app-only receiver. Binding, crypto and all reads run off Main; stop closes active AND pending clients. */
class TvRemoteServer(
    host: String,
    private val onCommand: (Long, RemoteCommand) -> Boolean,
    private val onConnectionChanged: (Long, Boolean) -> Unit,
    private val authTimeoutMillis: Int = RemoteWire.AUTH_TIMEOUT_MILLIS,
) : AutoCloseable {
    private val pairing = RemotePairingCode()
    private val closed = AtomicBoolean(false)
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val active = AtomicReference<Socket?>()
    private val ownership = Any()
    private val sessions = AtomicLong()
    private val workers = ThreadPoolExecutor(WORKERS, WORKERS, 0L, TimeUnit.MILLISECONDS, SynchronousQueue(),
        { task -> Thread(task, "UaCastRemoteClient").apply { isDaemon = true } })
    private val authDeadlines = ScheduledThreadPoolExecutor(1,
        { task -> Thread(task, "UaCastRemoteDeadline").apply { isDaemon = true } }).apply {
        removeOnCancelPolicy = true
    }
    private val listener = bind(host)
    val endpoint = RemoteEndpoint(host, listener.localPort)
    val pairingCode: String get() = pairing.value

    init { thread(name = "UaCastRemoteAccept", isDaemon = true) { acceptClients() } }

    private fun acceptClients() {
        while (!closed.get()) {
            val socket = try { listener.accept() } catch (_: IOException) { return }
            sockets.add(socket)
            if (closed.get()) {
                sockets.remove(socket)
                socket.close()
                return
            }
            try {
                workers.execute { serve(socket) }
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                sockets.remove(socket)
                socket.close()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "ReturnCount") // fail-closed guards retire only this untrusted LAN socket
    private fun serve(socket: Socket) {
        var session = 0L
        var deadline: ScheduledFuture<*>? = null
        try {
            socket.use {
                if (!pairing.allowAttempt()) return
                // SO_TIMEOUT is per-read, not an absolute handshake deadline: byte dribbling must
                // not occupy the sole remaining worker indefinitely while a phone is connected.
                deadline = authDeadlines.schedule({ runCatching { socket.close() } },
                    authTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
                socket.soTimeout = authTimeoutMillis
                socket.tcpNoDelay = true
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                val salt = RemoteCipher.salt()
                output.writeInt(RemoteWire.MAGIC)
                output.writeByte(RemoteWire.VERSION)
                output.write(salt)
                output.flush()
                val key = RemoteCipher.key(pairing.value, salt)
                if (!RemoteCipher.read(input, key).contentEquals(RemoteWire.PAIR)) return
                if (closed.get()) return
                RemoteCipher.write(output, key, RemoteWire.ACCEPTED)
                deadline?.cancel(false)
                synchronized(ownership) {
                    if (closed.get()) return
                    active.getAndSet(socket)?.let { runCatching { it.close() } }
                    session = sessions.incrementAndGet()
                    onConnectionChanged(session, true)
                }
                socket.soTimeout = IDLE_TIMEOUT_MILLIS
                var sequence = 1L
                while (!closed.get() && active.get() === socket) {
                    val command = RemoteWire.parseCommand(RemoteCipher.read(input, key), sequence)
                    val accepted = synchronized(ownership) {
                        !closed.get() && active.get() === socket && onCommand(session, command)
                    }
                    if (!accepted) return
                    RemoteCipher.write(output, key, RemoteWire.acknowledgement(sequence))
                    sequence++
                }
            }
        } catch (_: Exception) {
            // No code, key, plaintext or provider/channel details enter logs.
        } finally {
            deadline?.cancel(false)
            sockets.remove(socket)
            synchronized(ownership) {
                if (active.compareAndSet(socket, null)) onConnectionChanged(session, false)
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { listener.close() }
        sockets.forEach { socket -> runCatching { socket.close() } }
        active.set(null)
        workers.shutdownNow()
        authDeadlines.shutdownNow()
    }

    private companion object {
        const val WORKERS = 2
        const val IDLE_TIMEOUT_MILLIS = 300_000

        fun bind(host: String): ServerSocket {
            require(RemoteEndpoint.isLocalIpv4(host))
            val socket = ServerSocket()
            return try {
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(host, 0))
                socket
            } catch (error: IOException) { socket.close(); throw error }
        }
    }
}
