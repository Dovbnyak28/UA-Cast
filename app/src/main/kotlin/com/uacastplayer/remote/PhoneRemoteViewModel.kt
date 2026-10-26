package com.uacastplayer.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uacastplayer.core.remote.RemoteCommand
import com.uacastplayer.data.remote.PhoneRemoteClient
import com.uacastplayer.data.remote.RemoteEndpoint
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PhoneRemoteState(val endpoint: RemoteEndpoint? = null, val connecting: Boolean = false,
    val connected: Boolean = false, val failed: Boolean = false)

/** Owns one connection and a bounded, ordered command lane. Never retains an Activity. */
class PhoneRemoteViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(PhoneRemoteState())
    val state = mutableState.asStateFlow()
    private var client: PhoneRemoteClient? = null
    private var job: Job? = null
    private var commands: Channel<RemoteCommand>? = null

    // Socket/protocol/crypto failures all produce the same retryable pairing state.
    @Suppress("TooGenericExceptionCaught")
    fun connect(address: String, code: String) {
        disconnect()
        val endpoint = RemoteEndpoint.parse(address)
        if (endpoint == null || !code.matches(Regex("[0-9]{8}"))) {
            mutableState.value = PhoneRemoteState(failed = true)
            return
        }
        val owner = PhoneRemoteClient()
        val queue = Channel<RemoteCommand>(COMMAND_CAPACITY)
        client = owner
        commands = queue
        mutableState.value = PhoneRemoteState(endpoint, connecting = true)
        job = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { owner.connect(endpoint, code) }
                coroutineScope {
                    val monitor = launch {
                        owner.awaitDisconnection()
                        throw IOException("Remote receiver disconnected")
                    }
                    try {
                        mutableState.value = PhoneRemoteState(endpoint, connected = true)
                        for (command in queue) withContext(Dispatchers.IO) { owner.send(command) }
                    } finally { monitor.cancel() }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (client === owner) mutableState.value = PhoneRemoteState(endpoint, failed = true)
            } finally {
                runCatching { owner.close() }
                queue.close()
                if (client === owner) { client = null; commands = null }
            }
        }
    }

    fun send(command: RemoteCommand): Boolean = state.value.connected && commands?.trySend(command)?.isSuccess == true

    fun disconnect() {
        job?.cancel()
        job = null
        client?.let { runCatching { it.close() } }
        client = null
        commands?.close()
        commands = null
        mutableState.value = PhoneRemoteState()
    }

    override fun onCleared() { disconnect(); super.onCleared() }

    private companion object { const val COMMAND_CAPACITY = 16 }
}
