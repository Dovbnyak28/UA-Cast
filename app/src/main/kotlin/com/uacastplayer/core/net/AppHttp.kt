package com.uacastplayer.core.net

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Shared base [OkHttpClient] for every repository that talks to an IPTV/EPG origin (playlist, EPG,
 * icons, cast proxy). Each call site still wants its own timeouts, but [OkHttpClient.newBuilder]
 * is documented to share the builder's [OkHttpClient.connectionPool] and [OkHttpClient.dispatcher]
 * with the client it was built from - so deriving from one lazily-created [base] instead of each
 * repository calling `OkHttpClient.Builder()` from scratch means they all share one connection
 * pool and one dispatcher (and its thread pool) to the same handful of real-world origins, instead
 * of four independent ones.
 */
object AppHttp {
    private val base by lazy { OkHttpClient.Builder().build() }

    fun client(connectTimeoutSeconds: Long, readTimeoutSeconds: Long): OkHttpClient =
        base.newBuilder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .build()
}
