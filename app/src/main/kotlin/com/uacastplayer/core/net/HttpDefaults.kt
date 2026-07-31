package com.uacastplayer.core.net

/**
 * Shared HTTP defaults for outbound requests to IPTV/EPG origins. Many origins reject requests
 * without a browser-looking User-Agent, or redirect across http/https or to a different host
 * entirely - every path that talks to an origin directly (playlist loader, local player data
 * source, cast proxy) needs the same treatment, so it lives in one place.
 */
object HttpDefaults {
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/128.0.0.0 Safari/537.36"
}
