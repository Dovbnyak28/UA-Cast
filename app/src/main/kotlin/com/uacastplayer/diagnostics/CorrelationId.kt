package com.uacastplayer.diagnostics

import com.uacastplayer.core.security.Fingerprint

/** Short, non-reversible identifier for joining related log lines without exposing a raw token. */
object CorrelationId {
    private const val FINGERPRINT_LENGTH = 10

    fun from(kind: String, rawId: String): String =
        "$kind-${Fingerprint.of(rawId).take(FINGERPRINT_LENGTH)}"
}
