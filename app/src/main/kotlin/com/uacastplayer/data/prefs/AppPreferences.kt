package com.uacastplayer.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.core.i18n.LanguageResolver
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.parentalcontrol.ParentalControlPinStorage
import com.uacastplayer.ui.theme.AppTheme

/**
 * Small, synchronous wrapper around the app's single general-settings [SharedPreferences] file.
 * Values here are all tiny scalars; there is no need for the AtomicFile/versioned-snapshot
 * machinery used by the playlist/EPG/favorites caches.
 */
class AppPreferences(context: Context) : ParentalControlPinStorage {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var language: AppLanguage
        get() = LanguageResolver.fromStoredCode(prefs.getString(KEY_LANGUAGE, null))
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value.code).apply()

    /** Selectable visual style - see docs/DESIGN_SYSTEM.md "Themes". */
    var appTheme: AppTheme
        get() = AppTheme.fromId(prefs.getString(KEY_APP_THEME, null))
        set(value) = prefs.edit().putString(KEY_APP_THEME, value.name).apply()

    val hasChosenLanguage: Boolean
        get() = prefs.contains(KEY_LANGUAGE)

    var epgSource: EpgSource
        get() = EpgSource.fromId(prefs.getString(KEY_EPG_SOURCE, null))
        set(value) = prefs.edit().putString(KEY_EPG_SOURCE, value.id).apply()

    /** A playlist- or Xtream-provided EPG URL (see EpgSourceAutoDetect), active in place of
     * [epgSource] above when non-null. Cleared when the user picks one of the fixed [epgSource]
     * entries manually instead. */
    var customEpgUrl: String?
        get() = prefs.getString(KEY_CUSTOM_EPG_URL, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_EPG_URL, value).apply()

    /** True only once the user has deliberately picked an EPG source themselves - either a fixed
     * [epgSource] entry or by tapping "Use" on an auto-detected suggestion. Distinct from merely
     * *having* an active source, since [EpgSourceAutoDetect] can also set one automatically
     * without this becoming true, so it keeps auto-applying newly discovered URLs until the user
     * actually steps in. */
    var hasChosenEpgSource: Boolean
        get() = prefs.getBoolean(KEY_HAS_CHOSEN_EPG_SOURCE, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_CHOSEN_EPG_SOURCE, value).apply()

    var iconWifiOnly: Boolean
        get() = prefs.getBoolean(KEY_ICON_WIFI_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_ICON_WIFI_ONLY, value).apply()

    var lastIconPrefetchAtMillis: Long?
        get() = if (prefs.contains(KEY_LAST_ICON_PREFETCH)) prefs.getLong(KEY_LAST_ICON_PREFETCH, 0L) else null
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_LAST_ICON_PREFETCH).apply()
            } else {
                prefs.edit().putLong(KEY_LAST_ICON_PREFETCH, value).apply()
            }
        }

    var iconDisplayMode: IconDisplayMode
        get() = IconDisplayMode.fromId(prefs.getString(KEY_ICON_DISPLAY_MODE, null))
        set(value) = prefs.edit().putString(KEY_ICON_DISPLAY_MODE, value.name).apply()

    /** Once true, the user's explicit choice above wins forever over the device-tier-computed default. */
    val hasChosenIconDisplayMode: Boolean
        get() = prefs.contains(KEY_ICON_DISPLAY_MODE)

    var listDensity: ListDensity
        get() = ListDensity.fromId(prefs.getString(KEY_LIST_DENSITY, null))
        set(value) = prefs.edit().putString(KEY_LIST_DENSITY, value.name).apply()

    val hasChosenListDensity: Boolean
        get() = prefs.contains(KEY_LIST_DENSITY)

    var channelLayout: ChannelLayout
        get() = ChannelLayout.fromId(prefs.getString(KEY_CHANNEL_LAYOUT, null))
        set(value) = prefs.edit().putString(KEY_CHANNEL_LAYOUT, value.name).apply()

    var bufferSize: BufferSize
        get() = BufferSize.fromId(prefs.getString(KEY_BUFFER_SIZE, null))
        set(value) = prefs.edit().putString(KEY_BUFFER_SIZE, value.name).apply()

    /** Global video fit/fill/zoom preset - see [PlayerResizeMode]. */
    var playerResizeMode: PlayerResizeMode
        get() = PlayerResizeMode.fromId(prefs.getString(KEY_PLAYER_RESIZE_MODE, null))
        set(value) = prefs.edit().putString(KEY_PLAYER_RESIZE_MODE, value.name).apply()

    var favoritesSortOrder: FavoritesSortOrder
        get() = FavoritesSortOrder.fromId(prefs.getString(KEY_FAVORITES_SORT_ORDER, null))
        set(value) = prefs.edit().putString(KEY_FAVORITES_SORT_ORDER, value.name).apply()

    var wrapAroundEnabled: Boolean
        get() = prefs.getBoolean(KEY_WRAP_AROUND, true)
        set(value) = prefs.edit().putBoolean(KEY_WRAP_AROUND, value).apply()

    var autoSkipDeadEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SKIP_DEAD, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SKIP_DEAD, value).apply()

    /** Once true, the automatic first-cast-session battery optimization hint never shows again on its own. */
    var hasSeenBatteryOptimizationHint: Boolean
        get() = prefs.getBoolean(KEY_SEEN_BATTERY_HINT, false)
        set(value) = prefs.edit().putBoolean(KEY_SEEN_BATTERY_HINT, value).apply()

    /** Once true, the Channels-tab banner explaining a tier-default PLACEHOLDERS icon mode never
     * shows again on its own - set either by dismissing it or by acting on it (see
     * [IconDisplayMode]/[hasChosenIconDisplayMode], which the "Enable icons" action also sets). */
    var hasSeenIconTierHint: Boolean
        get() = prefs.getBoolean(KEY_SEEN_ICON_TIER_HINT, false)
        set(value) = prefs.edit().putBoolean(KEY_SEEN_ICON_TIER_HINT, value).apply()

    /** Gates first launch, same as [hasChosenLanguage]; declining the terms exits the app instead of setting this. */
    var hasAcceptedTerms: Boolean
        get() = prefs.getBoolean(KEY_ACCEPTED_TERMS, false)
        set(value) = prefs.edit().putBoolean(KEY_ACCEPTED_TERMS, value).apply()

    /** Gates the one-time [com.uacastplayer.ui.onboarding.OnboardingScreen], shown right after
     * [hasAcceptedTerms] on first launch - set whether the user skips or completes it, either way
     * it never shows again on its own. */
    var hasSeenOnboarding: Boolean
        get() = prefs.getBoolean(KEY_SEEN_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_SEEN_ONBOARDING, value).apply()

    /** Legacy single-playlist label, only still read once during
     * `PlaylistRepository.migrateLegacySnapshotIfNeeded` - display names now live per-source in
     * `com.uacastplayer.playlist.PlaylistSource`. */
    var playlistDisplayName: String?
        get() = prefs.getString(KEY_PLAYLIST_DISPLAY_NAME, null)
        set(value) = prefs.edit().putString(KEY_PLAYLIST_DISPLAY_NAME, value).apply()

    /** Which saved [com.uacastplayer.playlist.PlaylistSource] is currently active - the single
     * source of truth for "which per-source snapshot to restore on startup" now that there can be
     * more than one saved source. */
    var activePlaylistSourceId: String?
        get() = prefs.getString(KEY_ACTIVE_PLAYLIST_SOURCE_ID, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_PLAYLIST_SOURCE_ID, value).apply()

    /** Escape hatch for `data/cast/ProxyServer`'s raw-MPEG-TS-to-live-HLS remux (see
     * docs/PROXY_RULES.md "Raw TS remux") - on by default, but if keyframe detection turns out
     * unreliable on some real broadcast this can be switched off without a release, falling back
     * to the previous plain passthrough behavior for raw TS streams. */
    var rawTsRemuxEnabled: Boolean
        get() = prefs.getBoolean(KEY_RAW_TS_REMUX_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_RAW_TS_REMUX_ENABLED, value).apply()

    /** [com.uacastplayer.favorites.FavoriteKey] of the last channel played, for Home's "continue
     * watching" card - the same identifier favorites use, not the raw stream URL, so this stays
     * consistent with how [com.uacastplayer.data.favorites.FavoritesStore] identifies channels. */
    var lastWatchedChannelKey: String?
        get() = prefs.getString(KEY_LAST_WATCHED_CHANNEL, null)
        set(value) = prefs.edit().putString(KEY_LAST_WATCHED_CHANNEL, value).apply()

    /** Salted PBKDF2 hash of the parental-control PIN (see
     * [com.uacastplayer.core.security.PinHasher]) - null means no PIN is set, which is also how
     * `app/ParentalControlController` decides whether the feature is enabled at all. Never the
     * plaintext PIN. Always set together with [parentalControlPinSalt]. */
    override var parentalControlPinHash: String?
        get() = prefs.getString(KEY_PARENTAL_CONTROL_PIN_HASH, null)
        set(value) = prefs.edit().putString(KEY_PARENTAL_CONTROL_PIN_HASH, value).apply()

    override var parentalControlPinSalt: String?
        get() = prefs.getString(KEY_PARENTAL_CONTROL_PIN_SALT, null)
        set(value) = prefs.edit().putString(KEY_PARENTAL_CONTROL_PIN_SALT, value).apply()

    private companion object {
        const val PREFS_NAME = "uacast_prefs"
        const val KEY_LANGUAGE = "language_code"
        const val KEY_APP_THEME = "app_theme"
        const val KEY_EPG_SOURCE = "epg_source_id"
        const val KEY_CUSTOM_EPG_URL = "custom_epg_url"
        const val KEY_HAS_CHOSEN_EPG_SOURCE = "has_chosen_epg_source"
        const val KEY_ICON_WIFI_ONLY = "icon_wifi_only"
        const val KEY_LAST_ICON_PREFETCH = "last_icon_prefetch_at"
        const val KEY_ICON_DISPLAY_MODE = "icon_display_mode"
        const val KEY_LIST_DENSITY = "list_density"
        const val KEY_CHANNEL_LAYOUT = "channel_layout"
        const val KEY_BUFFER_SIZE = "buffer_size"
        const val KEY_PLAYER_RESIZE_MODE = "player_resize_mode"
        const val KEY_FAVORITES_SORT_ORDER = "favorites_sort_order"
        const val KEY_WRAP_AROUND = "wrap_around_enabled"
        const val KEY_AUTO_SKIP_DEAD = "auto_skip_dead_enabled"
        const val KEY_SEEN_BATTERY_HINT = "seen_battery_optimization_hint"
        const val KEY_SEEN_ICON_TIER_HINT = "seen_icon_tier_hint"
        const val KEY_ACCEPTED_TERMS = "accepted_terms"
        const val KEY_SEEN_ONBOARDING = "seen_onboarding"
        const val KEY_PLAYLIST_DISPLAY_NAME = "playlist_display_name"
        const val KEY_ACTIVE_PLAYLIST_SOURCE_ID = "active_playlist_source_id"
        const val KEY_LAST_WATCHED_CHANNEL = "last_watched_channel_key"
        const val KEY_RAW_TS_REMUX_ENABLED = "raw_ts_remux_enabled"
        const val KEY_PARENTAL_CONTROL_PIN_HASH = "parental_control_pin_hash"
        const val KEY_PARENTAL_CONTROL_PIN_SALT = "parental_control_pin_salt"
    }
}
