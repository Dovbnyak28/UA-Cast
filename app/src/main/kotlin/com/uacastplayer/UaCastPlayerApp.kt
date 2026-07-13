package com.uacastplayer

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.uacastplayer.core.i18n.LanguageResolver
import com.uacastplayer.data.prefs.AppPreferences
import java.io.File

private const val COIL_DISK_CACHE_MAX_BYTES = 128L * 1024 * 1024

class UaCastPlayerApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Must run before any Activity is created in this process so the very first screen
        // (including the language picker on first run) already resolves resources correctly.
        applyStoredOrDeviceLanguage()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .diskCache {
            DiskCache.Builder()
                .directory(File(filesDir, "coil_cache"))
                .maxSizeBytes(COIL_DISK_CACHE_MAX_BYTES)
                .build()
        }
        .build()

    private fun applyStoredOrDeviceLanguage() {
        val prefs = AppPreferences(this)
        val language = if (prefs.hasChosenLanguage) {
            prefs.language
        } else {
            LanguageResolver.fromDeviceLocales(systemLocaleTags())
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.code))
    }

    private fun systemLocaleTags(): List<String> {
        val locales = LocaleListCompat.getAdjustedDefault()
        return (0 until locales.size()).mapNotNull { index ->
            locales[index]?.toLanguageTag()
        }
    }
}
