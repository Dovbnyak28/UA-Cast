package com.uacastplayer.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import android.content.Context

private const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/128.0.0.0 Safari/537.36"

/**
 * Many IPTV origins redirect across http/https or to a different host entirely, and some reject
 * requests without a browser-looking User-Agent - both are needed for streams to load at all.
 */
@UnstableApi
object PlayerDataSourceFactory {

    fun create(context: Context): DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(BROWSER_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        return DefaultDataSource.Factory(context, httpDataSourceFactory)
    }
}
