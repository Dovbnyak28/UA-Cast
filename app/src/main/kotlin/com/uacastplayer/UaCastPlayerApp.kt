package com.uacastplayer

import android.app.Application
import android.content.Context
import androidx.core.os.LocaleListCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.uacastplayer.core.i18n.LanguageResolver
import com.uacastplayer.core.i18n.LocaleContextWrapper
import com.uacastplayer.data.prefs.AppPreferences
import java.io.File

private const val COIL_DISK_CACHE_MAX_BYTES = 128L * 1024 * 1024

class UaCastPlayerApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .diskCache {
            DiskCache.Builder()
                .directory(File(filesDir, "coil_cache"))
                .maxSizeBytes(COIL_DISK_CACHE_MAX_BYTES)
                .build()
        }
        .build()

    override fun attachBaseContext(base: Context) {
        val prefs = AppPreferences(base)
        val language = if (prefs.hasChosenLanguage) {
            prefs.language
        } else {
            LanguageResolver.fromDeviceLocales(systemLocaleTags(base))
        }
        super.attachBaseContext(LocaleContextWrapper.wrap(base, language))
    }

    private fun systemLocaleTags(context: Context): List<String> {
        val locales = LocaleListCompat.getAdjustedDefault()
        return (0 until locales.size()).mapNotNull { index ->
            locales[index]?.toLanguageTag()
        }
    }
}
