package com.uacastplayer.epg

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Accepts provider URLs without adding any publisher-owned default or exposing credentials. */
object EpgUrlPolicy {
    const val MAX_URL_LENGTH = 8_192

    fun normalize(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.length !in 1..MAX_URL_LENGTH || trimmed.any { it.isISOControl() }) return null
        return trimmed.toHttpUrlOrNull()?.toString()
    }
}
