package com.uacastplayer.core.i18n

/**
 * Pure resolution rules for turning a persisted preference or the device's locale list into a
 * supported [AppLanguage]. Contains no Android types so it can be unit tested directly.
 */
object LanguageResolver {

    /** Resolves a previously persisted language code, falling back to [AppLanguage.DEFAULT]. */
    fun fromStoredCode(code: String?): AppLanguage {
        if (code.isNullOrBlank()) return AppLanguage.DEFAULT
        return AppLanguage.entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?: AppLanguage.DEFAULT
    }

    /**
     * Picks the best supported language for a device given its ordered list of preferred locale
     * tags (most-preferred first, e.g. `["uk-UA", "en-US"]`). Falls back to [AppLanguage.DEFAULT]
     * when nothing matches.
     */
    fun fromDeviceLocales(localeTags: List<String>): AppLanguage {
        for (tag in localeTags) {
            val primary = tag.substringBefore('-').substringBefore('_')
            val match = AppLanguage.entries.firstOrNull { it.code.equals(primary, ignoreCase = true) }
            if (match != null) return match
        }
        return AppLanguage.DEFAULT
    }
}
