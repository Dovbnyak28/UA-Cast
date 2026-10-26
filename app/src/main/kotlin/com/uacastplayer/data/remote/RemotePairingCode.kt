package com.uacastplayer.data.remote

import java.security.SecureRandom

internal class RemotePairingCode(private val clock: () -> Long = System::nanoTime) {
    val value: String = SecureRandom().nextInt(CODE_SPACE).toString().padStart(DIGITS, '0')
    private val expiresAt = clock() + VALIDITY_NANOS
    private val attempts = ArrayDeque<Long>()

    /** Global budget also prevents several LAN addresses bypassing a per-IP authentication limit. */
    @Synchronized fun allowAttempt(): Boolean {
        val now = clock()
        while (attempts.isNotEmpty() && now - attempts.first() >= ATTEMPT_WINDOW_NANOS) attempts.removeFirst()
        if (now >= expiresAt || attempts.size >= ATTEMPT_LIMIT) return false
        attempts.addLast(now)
        return true
    }

    companion object {
        const val DIGITS = 8
        private const val CODE_SPACE = 100_000_000
        private const val VALIDITY_NANOS = 300_000_000_000L
        private const val ATTEMPT_WINDOW_NANOS = 60_000_000_000L
        private const val ATTEMPT_LIMIT = 8
    }
}
