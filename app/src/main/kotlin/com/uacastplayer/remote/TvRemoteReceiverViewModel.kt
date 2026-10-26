package com.uacastplayer.remote

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uacastplayer.core.remote.RemoteCommand
import com.uacastplayer.data.remote.RemoteEndpoint
import com.uacastplayer.data.remote.RemoteLanAddress
import com.uacastplayer.data.remote.TvRemoteServer
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TvRemoteState(val endpoint: RemoteEndpoint? = null, val code: String? = null,
    val starting: Boolean = false, val paired: Boolean = false, val failed: Boolean = false)
data class TvRemoteEvent(val generation: Long, val connection: Long, val command: RemoteCommand,
    val createdAtNanos: Long = System.nanoTime())

/** Receiver lifetime is the visible TV Activity. Guards retire input after stop/replacement. */
class TvRemoteReceiverViewModel internal constructor(application: Application,
    private val lanAddress: () -> String?) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, { RemoteLanAddress.find(application) })
    private val mutableState = MutableStateFlow(TvRemoteState())
    val state = mutableState.asStateFlow()
    private val generation = AtomicLong()
    private val connection = AtomicLong()
    private val lifecycleLock = Any()
    private var server: TvRemoteServer? = null
    private var startJob: Job? = null
    private val commandQueue = Channel<TvRemoteEvent>(COMMAND_CAPACITY)
    val commands = commandQueue.receiveAsFlow()

    fun isCurrent(event: TvRemoteEvent): Boolean = event.generation == generation.get() &&
        event.connection == connection.get() && System.nanoTime() - event.createdAtNanos <= MAX_COMMAND_AGE_NANOS

    fun consume(event: TvRemoteEvent, action: (RemoteCommand) -> Unit) {
        synchronized(lifecycleLock) { if (isCurrent(event)) action(event.command) }
    }

    @Suppress("TooGenericExceptionCaught") // unavailable LAN/bind failures must leave TV playback functional
    fun start() {
        stop()
        val owner = generation.get()
        mutableState.value = TvRemoteState(starting = true)
        startJob = viewModelScope.launch {
            var candidate: TvRemoteServer? = null
            var committed = false
            try {
                withContext(Dispatchers.IO) {
                    val host = checkNotNull(lanAddress())
                    val created = TvRemoteServer(host,
                        onCommand = { session, command ->
                            synchronized(lifecycleLock) {
                                owner == generation.get() && connection.get() == session &&
                                    commandQueue.trySend(TvRemoteEvent(owner, session, command)).isSuccess
                            }
                        },
                        onConnectionChanged = { session, paired ->
                            synchronized(lifecycleLock) {
                                if (owner == generation.get()) {
                                    if (paired) connection.set(session) else connection.compareAndSet(session, 0L)
                                }
                            }
                            viewModelScope.launch {
                                if (owner == generation.get()) {
                                    mutableState.update { it.copy(paired = connection.get() != 0L) }
                                }
                            }
                        })
                    candidate = created
                    synchronized(lifecycleLock) {
                        if (owner == generation.get()) server = created else created.close()
                    }
                }
                if (owner != generation.get()) return@launch
                val running = checkNotNull(candidate)
                mutableState.value = TvRemoteState(running.endpoint, running.pairingCode,
                    paired = connection.get() != 0L)
                committed = true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (owner == generation.get()) mutableState.value = TvRemoteState(failed = true)
            } finally {
                if (!committed) candidate?.let { runCatching { it.close() } }
            }
        }
    }

    fun stop() {
        synchronized(lifecycleLock) {
            generation.incrementAndGet()
            connection.set(0L)
            server?.let { runCatching { it.close() } }
            server = null
        }
        startJob?.cancel()
        startJob = null
        while (commandQueue.tryReceive().isSuccess) { /* retire queued input */ }
        mutableState.value = TvRemoteState()
    }

    override fun onCleared() {
        stop()
        commandQueue.close()
        super.onCleared()
    }

    private companion object {
        const val COMMAND_CAPACITY = 16
        const val MAX_COMMAND_AGE_NANOS = 1_000_000_000L
    }
}
