package com.uacastplayer.core.i18n

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Wraps [base] with a configuration pinned to [language]. The Application context never observes
 * the chosen language on its own, so every place that resolves string resources (Activities,
 * ViewModels that need a Context) must go through a context produced here rather than the raw
 * Application context.
 */
object LocaleContextWrapper {

    fun wrap(base: Context, language: AppLanguage): Context {
        val locale = Locale.Builder().setLanguage(language.code).build()
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }
}
