package com.uacastplayer.core.i18n

import java.util.Locale

/**
 * Supported UI languages. [code] is the BCP-47 / Android resource-qualifier language code.
 */
enum class AppLanguage(val code: String) {
    UKRAINIAN("uk"),
    ENGLISH("en"),
    RUSSIAN("ru"),
    SPANISH("es");

    /**
     * This language as a [Locale], for the code that sorts by an alphabet - see
     * [com.uacastplayer.favorites.FavoritesSorter] and
     * [com.uacastplayer.playlist.ChannelGrouper].
     *
     * `Locale.getDefault()` is the wrong answer for those, and specifically wrong in this app:
     * [withAppLocale] wraps a Configuration rather than calling `Locale.setDefault`, so the process
     * default stays whatever the *device* is set to no matter what the user picked here. Somebody
     * running a phone in English with this app in Ukrainian would have got English collation.
     */
    fun toLocale(): Locale = Locale.forLanguageTag(code)

    companion object {
        val DEFAULT: AppLanguage = ENGLISH
    }
}
