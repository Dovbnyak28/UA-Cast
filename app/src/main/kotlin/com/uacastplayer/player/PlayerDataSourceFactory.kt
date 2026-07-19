package com.uacastplayer.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.uacastplayer.core.net.AppHttp
import com.uacastplayer.core.net.HttpDefaults

private const val CONNECT_TIMEOUT_SECONDS = 15L
private const val READ_TIMEOUT_SECONDS = 30L

/**
 * Many IPTV origins redirect across http/https or to a different host entirely, and some reject
 * requests without a browser-looking User-Agent - both are needed for streams to load at all.
 * Backed by [OkHttpDataSource] over an [AppHttp]-derived client rather than Media3's own
 * `DefaultHttpDataSource` (plain `HttpURLConnection`), so a channel switch reuses the connection
 * pool instead of paying a fresh TCP/TLS handshake to the same host every time - cross-protocol
 * redirects are still allowed, since OkHttp's `followSslRedirects`/`followRedirects` default to
 * true and [AppHttp] doesn't override them.
 *
 * Media3 has no per-[androidx.media3.common.MediaItem] header hook, so per-channel overrides (a
 * playlist's `#EXTVLCOPT:http-user-agent=`/`http-referrer=`) are applied by mutating the shared
 * [OkHttpDataSource.Factory] right before switching to that channel's [MediaItem] - safe here
 * because only one channel is ever loading at a time.
 */
@UnstableApi
class PlayerDataSourceFactory private constructor(
    private val httpDataSourceFactory: OkHttpDataSource.Factory,
    private val wrapped: DataSource.Factory,
) : DataSource.Factory by wrapped {

    fun setChannelHeaders(userAgent: String?, referrer: String?) {
        httpDataSourceFactory.setUserAgent(userAgent?.ifBlank { null } ?: HttpDefaults.BROWSER_USER_AGENT)
        val properties = mutableMapOf<String, String>()
        referrer?.ifBlank { null }?.let { properties["Referer"] = it }
        httpDataSourceFactory.setDefaultRequestProperties(properties)
    }

    companion object {
        fun create(context: Context): PlayerDataSourceFactory {
            val client = AppHttp.client(CONNECT_TIMEOUT_SECONDS, READ_TIMEOUT_SECONDS)
            val httpDataSourceFactory = OkHttpDataSource.Factory(client)
                .setUserAgent(HttpDefaults.BROWSER_USER_AGENT)
            val wrapped = DefaultDataSource.Factory(context, httpDataSourceFactory)
            return PlayerDataSourceFactory(httpDataSourceFactory, wrapped)
        }
    }
}
