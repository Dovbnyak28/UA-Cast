package com.uacastplayer.backup

import com.uacastplayer.core.concurrent.runCatchingNonFatal
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
     * The whole body is guarded, not just the initial [JSONObject] construction: `runCatchingNonFatal`
     * around that alone left every accessor below it free to throw, and one did - see
     * [toObjectList]. This file is user-visible and meant to be hand-editable, so "unusable input
     * yields null" has to hold for the entire parse, not for its first line.
     */
    fun decode(text: String): BackupData? {
        if (text.isBlank()) return null
        return runCatchingNonFatal {
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

    /**
     * A field's value, or null when it is absent, explicitly `null`, or blank - the three things
     * this format treats identically.
     *
     * `optString` on its own is not enough, and the reason is that the two `org.json`
     * implementations in play here disagree. Measured directly on `{"a":null}`, same call:
     *
     * - the reference `org.json`, which `testImplementation(libs.org.json)` puts under the unit
     *   tests, answers `""`;
     * - Android's, which is what runs on a phone, answers `"null"` - four characters, because
     *   `optString` goes through `JSON.toString(opt(name))` and `JSONObject.NULL.toString()` is
     *   `"null"`.
     *
     * Every "is this field present" decision below is a blank check, so on a device those checks
     * were being handed a non-blank string for a field that was explicitly null: a favorite whose
     * `tvgId` is the word "null" matches no EPG channel, and - worse - a *required* field that is
     * explicitly null passed the check that exists to skip it, importing an entry whose stream URL
     * is the word "null". A backup this app writes never contains one, since `putOpt` drops nulls;
     * a hand-edited or third-party file is exactly what [decode] is written to survive.
     *
     * `isNull` is the one accessor both implementations agree on for this, and it is true for an
     * absent name as well as an explicit null.
     */
    private fun JSONObject.stringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).ifBlank { null }

    private fun sourceToJson(source: BackupPlaylistSource): JSONObject = JSONObject().apply {
        put("id", source.id)
        put("type", source.type)
        put("location", source.location)
        putOpt("displayName", source.displayName)
        put("addedAtEpochMillis", source.addedAtEpochMillis)
    }

    private fun sourceFromJson(json: JSONObject): BackupPlaylistSource? {
        val id = json.stringOrNull("id")
        val type = json.stringOrNull("type")
        val location = json.stringOrNull("location")
        if (id == null || type == null || location == null) return null
        return BackupPlaylistSource(
            id = id,
            type = type,
            location = location,
            displayName = json.stringOrNull("displayName"),
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
        val key = json.stringOrNull("key")
        val displayName = json.stringOrNull("displayName")
        val streamUrl = json.stringOrNull("streamUrl")
        if (key == null || displayName == null || streamUrl == null) return null
        return BackupFavorite(
            key = key,
            displayName = displayName,
            streamUrl = streamUrl,
            tvgId = json.stringOrNull("tvgId"),
            groupTitle = json.stringOrNull("groupTitle"),
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
        iconDisplayMode = json.stringOrNull("iconDisplayMode"),
        listDensity = json.stringOrNull("listDensity"),
        bufferSize = json.stringOrNull("bufferSize"),
        epgSourceId = json.stringOrNull("epgSourceId"),
        epgCustomUrl = json.stringOrNull("epgCustomUrl"),
    )
}
