package com.uacastplayer.cast

/** Keeps one proxy URL namespace for a CastSession across suspend/resume callbacks. */
internal object CastProxySessionTokenPolicy {
    fun select(
        previousSession: Any?,
        session: Any?,
        currentToken: String,
        newToken: () -> String,
    ): String = if (previousSession !== session && session != null || currentToken.isEmpty()) {
        newToken()
    } else {
        currentToken
    }
}
