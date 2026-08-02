package com.uacastplayer.data.icons

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray

/**
 * User-added base-URL icon sources (e.g. `https://mycdn.com/logos/`), tried before the built-in
 * CDN fallback - see [IconRepository]/[com.uacastplayer.icons.IconResolver]. Small, non-critical,
 * editable list, so a plain JSON-array-in-one-SharedPreferences-value is enough - unlike
 * [com.uacastplayer.data.favorites.FavoritesStore] this doesn't need AtomicFile crash-safety;
 * losing this list in a rare write race just means re-adding a couple of URLs, not losing user data.
 */
class CustomIconSourceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBaseUrls(): List<String> {
        val raw = preferences.getString(KEY_BASE_URLS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index -> array.getString(index) }
        }.getOrDefault(emptyList())
    }

    fun saveBaseUrls(urls: List<String>) {
        val array = JSONArray().apply { urls.forEach(::put) }
        preferences.edit { putString(KEY_BASE_URLS, array.toString()) }
    }

    private companion object {
        const val PREFS_NAME = "custom_icon_sources"
        const val KEY_BASE_URLS = "base_urls"
    }
}
