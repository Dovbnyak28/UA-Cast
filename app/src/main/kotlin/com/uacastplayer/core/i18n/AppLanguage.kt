package com.uacastplayer.core.i18n

/**
 * Supported UI languages. [code] is the BCP-47 / Android resource-qualifier language code.
 */
enum class AppLanguage(val code: String) {
    UKRAINIAN("uk"),
    ENGLISH("en"),
    RUSSIAN("ru"),
    SPANISH("es");

    companion object {
        val DEFAULT: AppLanguage = ENGLISH
    }
}
