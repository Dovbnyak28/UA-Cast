package com.uacastplayer.data.remote

import com.uacastplayer.core.remote.RemoteCommand
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** Sequence numbers are authenticated inside the frame and reject repeats within a connection. */
internal object RemoteWire {
    const val MAGIC = 0x55414352
    const val VERSION = 1
    // MiTV Android 9's BC provider needs ~13s per 160k-iteration derivation (26s for loopback peers).
    // This is an absolute handshake budget, not the command/connection timeout or an idle timeout.
    const val AUTH_TIMEOUT_MILLIS = 40_000
    val PAIR = "UA-CAST-PAIR-1".toByteArray(Charsets.US_ASCII)
    val ACCEPTED = "UA-CAST-OK-1".toByteArray(Charsets.US_ASCII)
    private const val COMMAND_BYTES = Long.SIZE_BYTES + Byte.SIZE_BYTES

    fun command(sequence: Long, command: RemoteCommand): ByteArray = ByteArrayOutputStream().also { buffer ->
        DataOutputStream(buffer).use { it.writeLong(sequence); it.writeByte(command.ordinal) }
    }.toByteArray()

    fun parseCommand(payload: ByteArray, expectedSequence: Long): RemoteCommand {
        if (payload.size != COMMAND_BYTES) throw IOException("Invalid remote command length")
        val input = DataInputStream(ByteArrayInputStream(payload))
        val sequence = input.readLong()
        return RemoteCommand.entries.getOrNull(input.readUnsignedByte())?.takeIf { sequence == expectedSequence }
            ?: throw IOException("Invalid remote sequence or command")
    }

    fun acknowledgement(sequence: Long): ByteArray = ByteArrayOutputStream().also { buffer ->
        DataOutputStream(buffer).use { it.writeLong(sequence) }
    }.toByteArray()
}
