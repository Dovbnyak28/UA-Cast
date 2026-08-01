package com.uacastplayer.backup

import org.json.JSONArray
import org.json.JSONObject

/**
 * Hand-rolled versioned JSON (de)serializer for [BackupData] - JSON (not the binary format the
 * snapshot/source codecs use) specifically because this file is meant to be user-visible/portable
 * (shared, inspected, moved between devices), unlike the app-private caches those codecs cover.
 * Uses org.json (bundled with Android) rather than adding a serialization library for one file
 * format. [decode] returns null for anything not usable - blank input, malformed JSON, or an
 * unknown [CURRENT_VERSION] - callers treat that the same as "nothing to import" rather than
 * crashing on a hand-edited or future-version file.
 */
object BackupCodec {
    const val CURRENT_VERSION = 1

    fun encode(data: BackupData): String {
        val root = JSONObject()
        root.put("version", CURRENT_VERSION)
        root.put("sources", JSONArray(data.sources.map(::sourceToJson)))
        root.put("favorites", JSONArray(data.favorites.map(::favoriteToJson)))
        root.put("settings", settingsToJson(data.settings))
        return root.toString(2)
    }

    /**
     * The whole body is guarded, not just the initial [JSONObject] construction: `runCatching`
     * around that alone left every accessor below it free to throw, and one did - see
     * [toObjectList]. This file is user-visible and meant to be hand-editable, so "unusable input
     * yields null" has to hold for the entire parse, not for its first line.
     */
    fun decode(text: String): BackupData? {
        if (text.isBlank()) return null
        return runCatching {
            JSONObject(text)
                .takeIf { it.optInt("version", -1) == CURRENT_VERSION }
                ?.let { root ->
                    val sources = root.optJSONArray("sources")?.toObjectList()?.mapNotNull(::sourceFromJson).orEmpty()
                    val favorites =
                        root.optJSONArray("favorites")?.toObjectList()?.mapNotNull(::favoriteFromJson).orEmpty()
                    val settings = root.optJSONObject("settings")?.let(::settingsFromJson) ?: BackupSettings()
                    BackupData(sources, favorites, settings)
                }
        }.getOrNull()
    }

    /**
     * `optJSONObject`, not `getJSONObject`: the latter throws on an array element that is not an
     * object, and nothing between here and [com.uacastplayer.app.BackupController.importFrom]'s
     * `scope.launch` caught it - so importing a backup whose `sources` array had a stray string in
     * it took the app down instead of being ignored.
     *
     * Skipping the bad element rather than failing the whole parse also matches what the
     * `mapNotNull(::sourceFromJson)` at both call sites already does for an object that is merely
     * missing fields: one unusable row costs that row, not the import.
     */
    private fun JSONArray.toObjectList(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    private fun sourceToJson(source: BackupPlaylistSource): JSONObject = JSONObject().apply {
        put("id", source.id)
        put("type", source.type)
        put("location", source.location)
        putOpt("displayName", source.displayName)
        put("addedAtEpochMillis", source.addedAtEpochMillis)
    }

    private fun sourceFromJson(json: JSONObject): BackupPlaylistSource? {
        val id = json.optString("id")
        val type = json.optString("type")
        val location = json.optString("location")
        if (id.isBlank() || type.isBlank() || location.isBlank()) return null
        return BackupPlaylistSource(
            id = id,
            type = type,
            location = location,
            displayName = json.optString("displayName").ifBlank { null },
            addedAtEpochMillis = json.optLong("addedAtEpochMillis", 0L),
        )
    }

    private fun favoriteToJson(favorite: BackupFavorite): JSONObject = JSONObject().apply {
        put("key", favorite.key)
        put("displayName", favorite.displayName)
        put("streamUrl", favorite.streamUrl)
        putOpt("tvgId", favorite.tvgId)
        putOpt("groupTitle", favorite.groupTitle)
        put("addedAtMillis", favorite.addedAtMillis)
    }

    private fun favoriteFromJson(json: JSONObject): BackupFavorite? {
        val key = json.optString("key")
        val displayName = json.optString("displayName")
        val streamUrl = json.optString("streamUrl")
        if (key.isBlank() || displayName.isBlank() || streamUrl.isBlank()) return null
        return BackupFavorite(
            key = key,
            displayName = displayName,
            streamUrl = streamUrl,
            tvgId = json.optString("tvgId").ifBlank { null },
            groupTitle = json.optString("groupTitle").ifBlank { null },
            addedAtMillis = json.optLong("addedAtMillis", 0L),
        )
    }

    private fun settingsToJson(settings: BackupSettings): JSONObject = JSONObject().apply {
        putOpt("iconDisplayMode", settings.iconDisplayMode)
        putOpt("listDensity", settings.listDensity)
        putOpt("bufferSize", settings.bufferSize)
        putOpt("epgSourceId", settings.epgSourceId)
        putOpt("epgCustomUrl", settings.epgCustomUrl)
    }

    private fun settingsFromJson(json: JSONObject): BackupSettings = BackupSettings(
        iconDisplayMode = json.optString("iconDisplayMode").ifBlank { null },
        listDensity = json.optString("listDensity").ifBlank { null },
        bufferSize = json.optString("bufferSize").ifBlank { null },
        epgSourceId = json.optString("epgSourceId").ifBlank { null },
        epgCustomUrl = json.optString("epgCustomUrl").ifBlank { null },
    )
}
