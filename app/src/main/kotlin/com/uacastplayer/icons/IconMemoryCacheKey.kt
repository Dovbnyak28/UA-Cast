package com.uacastplayer.icons

/** Pack edits invalidate the cache; within one source revision only the channel ID matters. */
object IconMemoryCacheKey {
    fun of(tvgId: String?): String = tvgId?.takeUnless(String::isBlank).orEmpty()
}
