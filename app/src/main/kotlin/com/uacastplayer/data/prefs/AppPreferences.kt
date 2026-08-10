package com.uacastplayer.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.core.i18n.LanguageResolver
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.guidedtour.GuidedTourStorage
import com.uacastplayer.parentalcontrol.ParentalControlPinStorage
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.premium.License
import com.uacastplayer.premium.LicenseStorage
import com.uacastplayer.premium.LicenseTier
import com.uacastplayer.update.UpdateCheckStorage

/**
 * Small, synchronous wrapper around the app's single general-settings [SharedPreferences] file.
 * Values here are all tiny scalars; there is no need for the AtomicFile/versioned-snapshot
 * machinery used by the playlist/EPG/favorites caches.
 */
class AppPreferences(context: Context) :
    ParentalControlPinStorage,
    UpdateCheckStorage,
    LicenseStorage,
    GuidedTourStorage {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var language: AppLanguage
        get() = LanguageResolver.fromStoredCode(prefs.getString(KEY_LANGUAGE, null))
        set(value) = prefs.edit { putString(KEY_LANGUAGE, value.code) }

    /** Selectable visual style - see docs/DESIGN_SYSTEM.md "Themes". */
    var appTheme: AppTheme
        get() = AppTheme.fromId(prefs.getString(KEY_APP_THEME, null))
        set(value) = prefs.edit { putString(KEY_APP_THEME, value.name) }

    val hasChosenLanguage: Boolean
        get() = prefs.contains(KEY_LANGUAGE)

    var epgSource: EpgSource
        get() = EpgSource.fromId(prefs.getString(KEY_EPG_SOURCE, null))
        set(value) = prefs.edit { putString(KEY_EPG_SOURCE, value.id) }

    /** A playlist- or Xtream-provided EPG URL (see EpgSourceAutoDetect), active in place of
     * [epgSource] above when non-null. Cleared when the user picks one of the fixed [epgSource]
     * entries manually instead. */
    var customEpgUrl: String?
        get() = prefs.getString(KEY_CUSTOM_EPG_URL, null)
        set(value) = prefs.edit { putString(KEY_CUSTOM_EPG_URL, value) }

    /** True only once the user has deliberately picked an EPG source themselves - either a fixed
     * [epgSource] entry or by tapping "Use" on an auto-detected suggestion. Distinct from merely
     * *having* an active source, since [EpgSourceAutoDetect] can also set one automatically
     * without this becoming true, so it keeps auto-applying newly discovered URLs until the user
     * actually steps in. */
    var hasChosenEpgSource: Boolean
        get() = prefs.getBoolean(KEY_HAS_CHOSEN_EPG_SOURCE, false)
        set(value) = prefs.edit { putBoolean(KEY_HAS_CHOSEN_EPG_SOURCE, value) }

    var iconWifiOnly: Boolean
        get() = prefs.getBoolean(KEY_ICON_WIFI_ONLY, true)
        set(value) = prefs.edit { putBoolean(KEY_ICON_WIFI_ONLY, value) }

    var lastIconPrefetchAtMillis: Long?
        get() = if (prefs.contains(KEY_LAST_ICON_PREFETCH)) prefs.getLong(KEY_LAST_ICON_PREFETCH, 0L) else null
        set(value) {
            if (value == null) {
                prefs.edit { remove(KEY_LAST_ICON_PREFETCH) }
            } else {
                prefs.edit { putLong(KEY_LAST_ICON_PREFETCH, value) }
            }
        }

    var iconDisplayMode: IconDisplayMode
        get() = IconDisplayMode.fromId(prefs.getString(KEY_ICON_DISPLAY_MODE, null))
        set(value) = prefs.edit { putString(KEY_ICON_DISPLAY_MODE, value.name) }

    /** Once true, the user's explicit choice above wins forever over the device-tier-computed default. */
    val hasChosenIconDisplayMode: Boolean
        get() = prefs.contains(KEY_ICON_DISPLAY_MODE)

    var listDensity: ListDensity
        get() = ListDensity.fromId(prefs.getString(KEY_LIST_DENSITY, null))
        set(value) = prefs.edit { putString(KEY_LIST_DENSITY, value.name) }

    val hasChosenListDensity: Boolean
        get() = prefs.contains(KEY_LIST_DENSITY)

    var channelLayout: ChannelLayout
        get() = ChannelLayout.fromId(prefs.getString(KEY_CHANNEL_LAYOUT, null))
        set(value) = prefs.edit { putString(KEY_CHANNEL_LAYOUT, value.name) }

    var bufferSize: BufferSize
        get() = BufferSize.fromId(prefs.getString(KEY_BUFFER_SIZE, null))
        set(value) = prefs.edit { putString(KEY_BUFFER_SIZE, value.name) }

    /** Global video fit/fill/zoom preset - see [PlayerResizeMode]. */
    var playerResizeMode: PlayerResizeMode
        get() = PlayerResizeMode.fromId(prefs.getString(KEY_PLAYER_RESIZE_MODE, null))
        set(value) = prefs.edit { putString(KEY_PLAYER_RESIZE_MODE, value.name) }

    var favoritesSortOrder: FavoritesSortOrder
        get() = FavoritesSortOrder.fromId(prefs.getString(KEY_FAVORITES_SORT_ORDER, null))
        set(value) = prefs.edit { putString(KEY_FAVORITES_SORT_ORDER, value.name) }

    var wrapAroundEnabled: Boolean
        get() = prefs.getBoolean(KEY_WRAP_AROUND, true)
        set(value) = prefs.edit { putBoolean(KEY_WRAP_AROUND, value) }

    var autoSkipDeadEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SKIP_DEAD, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_SKIP_DEAD, value) }

    /** Once true, the automatic first-cast-session battery optimization hint never shows again on its own. */
    var hasSeenBatteryOptimizationHint: Boolean
        get() = prefs.getBoolean(KEY_SEEN_BATTERY_HINT, false)
        set(value) = prefs.edit { putBoolean(KEY_SEEN_BATTERY_HINT, value) }

    /** Once true, the Channels-tab banner explaining a tier-default PLACEHOLDERS icon mode never
     * shows again on its own - set either by dismissing it or by acting on it (see
     * [IconDisplayMode]/[hasChosenIconDisplayMode], which the "Enable icons" action also sets). */
    var hasSeenIconTierHint: Boolean
        get() = prefs.getBoolean(KEY_SEEN_ICON_TIER_HINT, false)
        set(value) = prefs.edit { putBoolean(KEY_SEEN_ICON_TIER_HINT, value) }

    /** Gates first launch, same as [hasChosenLanguage]; declining the terms exits the app instead of setting this. */
    var hasAcceptedTerms: Boolean
        get() = prefs.getBoolean(KEY_ACCEPTED_TERMS, false)
        set(value) = prefs.edit { putBoolean(KEY_ACCEPTED_TERMS, value) }

    /** Set when the guided tour is finished *or* skipped - see
     * [com.uacastplayer.app.GuidedTourController]. Read together with [guidedTourVersion], which is
     * what lets a later edition of the tour offer itself once to someone who already saw the old
     * one. */
    override var guidedTourCompleted: Boolean
        get() = prefs.getBoolean(KEY_GUIDED_TOUR_COMPLETED, false)
        set(value) = prefs.edit { putBoolean(KEY_GUIDED_TOUR_COMPLETED, value) }

    /** 0 on a device that has never seen the tour, and on every device that predates the feature -
     * both of which should be offered it, which is exactly what 0 means to
     * [com.uacastplayer.guidedtour.GuidedTourAvailability]. */
    override var guidedTourVersion: Int
        get() = prefs.getInt(KEY_GUIDED_TOUR_VERSION, 0)
        set(value) = prefs.edit { putInt(KEY_GUIDED_TOUR_VERSION, value) }

    /** Legacy single-playlist label, only still read once during
     * `PlaylistRepository.migrateLegacySnapshotIfNeeded` - display names now live per-source in
     * `com.uacastplayer.playlist.PlaylistSource`. */
    var playlistDisplayName: String?
        get() = prefs.getString(KEY_PLAYLIST_DISPLAY_NAME, null)
        set(value) = prefs.edit { putString(KEY_PLAYLIST_DISPLAY_NAME, value) }

    /** Which saved [com.uacastplayer.playlist.PlaylistSource] is currently active - the single
     * source of truth for "which per-source snapshot to restore on startup" now that there can be
     * more than one saved source. */
    var activePlaylistSourceId: String?
        get() = prefs.getString(KEY_ACTIVE_PLAYLIST_SOURCE_ID, null)
        set(value) = prefs.edit { putString(KEY_ACTIVE_PLAYLIST_SOURCE_ID, value) }

    /** Escape hatch for `data/cast/ProxyServer`'s raw-MPEG-TS-to-live-HLS remux (see
     * docs/PROXY_RULES.md "Raw TS remux") - on by default, but if keyframe detection turns out
     * unreliable on some real broadcast this can be switched off without a release, falling back
     * to the previous plain passthrough behavior for raw TS streams. */
    var rawTsRemuxEnabled: Boolean
        get() = prefs.getBoolean(KEY_RAW_TS_REMUX_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_RAW_TS_REMUX_ENABLED, value) }

    /** [com.uacastplayer.favorites.FavoriteKey] of the last channel played, for Home's "continue
     * watching" card - the same identifier favorites use, not the raw stream URL, so this stays
     * consistent with how [com.uacastplayer.data.favorites.FavoritesStore] identifies channels. */
    var lastWatchedChannelKey: String?
        get() = prefs.getString(KEY_LAST_WATCHED_CHANNEL, null)
        set(value) = prefs.edit { putString(KEY_LAST_WATCHED_CHANNEL, value) }

    /** Salted PBKDF2 hash of the parental-control PIN (see
     * [com.uacastplayer.core.security.PinHasher]) - null means no PIN is set, which is also how
     * `app/ParentalControlController` decides whether the feature is enabled at all. Never the
     * plaintext PIN. Always set together with [parentalControlPinSalt]. */
    override var parentalControlPinHash: String?
        get() = prefs.getString(KEY_PARENTAL_CONTROL_PIN_HASH, null)
        set(value) = prefs.edit { putString(KEY_PARENTAL_CONTROL_PIN_HASH, value) }

    override var parentalControlPinSalt: String?
        get() = prefs.getString(KEY_PARENTAL_CONTROL_PIN_SALT, null)
        set(value) = prefs.edit { putString(KEY_PARENTAL_CONTROL_PIN_SALT, value) }

    /** Wall clock of the last update check of either kind, so the automatic one can hold itself to
     * once a week (see [com.uacastplayer.update.UpdateCheckSchedule]). Null means never checked. */
    override var lastUpdateCheckAtMillis: Long?
        get() = if (prefs.contains(KEY_LAST_UPDATE_CHECK)) prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L) else null
        set(value) {
            if (value == null) {
                prefs.edit { remove(KEY_LAST_UPDATE_CHECK) }
            } else {
                prefs.edit { putLong(KEY_LAST_UPDATE_CHECK, value) }
            }
        }

    /** Release tag whose update banner the user closed, so it stays closed - for that version only.
     * Stored as the tag rather than a boolean: the next release has a different tag and so gets to
     * announce itself, which a "banner dismissed" flag would have to remember to reset. */
    override var dismissedUpdateTag: String?
        get() = prefs.getString(KEY_DISMISSED_UPDATE_TAG, null)
        set(value) = prefs.edit { putString(KEY_DISMISSED_UPDATE_TAG, value) }

    /**
     * The last license this device saw. Null means it has never held one, which is the single
     * condition under which [com.uacastplayer.data.premium.PremiumRepository] grants the
     * first-launch trial - so this staying non-null after the trial ends is what stops it being
     * granted again on every launch.
     *
     * An unrecognised tier resolves to [License.FREE] rather than null, deliberately: null would
     * read as "never held a license" and hand out a fresh trial, so a corrupted value must not be
     * more generous than a valid one.
     */
    override var storedLicense: License?
        get() {
            val storedTier = prefs.getString(KEY_LICENSE_TIER, null) ?: return null
            val tier = LicenseTier.entries.firstOrNull { it.name == storedTier } ?: LicenseTier.FREE
            val expiry = if (prefs.contains(KEY_LICENSE_EXPIRY)) prefs.getLong(KEY_LICENSE_EXPIRY, 0L) else null
            return License(tier, expiry, prefs.getString(KEY_LICENSE_SOURCE, null))
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove(KEY_LICENSE_TIER)
                remove(KEY_LICENSE_EXPIRY)
                remove(KEY_LICENSE_SOURCE)
            } else {
                putString(KEY_LICENSE_TIER, value.tier.name)
                putString(KEY_LICENSE_SOURCE, value.source)
                if (value.expiresAtMillis == null) {
                    remove(KEY_LICENSE_EXPIRY)
                } else {
                    putLong(KEY_LICENSE_EXPIRY, value.expiresAtMillis)
                }
            }
        }

    /** Set once, the first time a store answers with a non-empty catalogue, and never cleared -
     * see [LicenseStorage.storeHasEverOfferedProducts] for why it is remembered rather than asked. */
    override var storeHasEverOfferedProducts: Boolean
        get() = prefs.getBoolean(KEY_STORE_HAS_OFFERED, false)
        set(value) = prefs.edit { putBoolean(KEY_STORE_HAS_OFFERED, value) }

    /** See [LicenseStorage.clockHighWaterMark] - the newest time this app has ever seen. */
    override var clockHighWaterMark: Long
        get() = prefs.getLong(KEY_CLOCK_HIGH_WATER_MARK, 0L)
        set(value) = prefs.edit { putLong(KEY_CLOCK_HIGH_WATER_MARK, value) }

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
        // "seen_onboarding" was the three-card walkthrough the guided tour replaced. Deliberately
        // not migrated into guided_tour_completed: someone who saw those three cards has not seen
        // the tour, and offering it to them once is the point of removing them.
        const val KEY_GUIDED_TOUR_COMPLETED = "guided_tour_completed"
        const val KEY_GUIDED_TOUR_VERSION = "guided_tour_version"
        const val KEY_PLAYLIST_DISPLAY_NAME = "playlist_display_name"
        const val KEY_ACTIVE_PLAYLIST_SOURCE_ID = "active_playlist_source_id"
        const val KEY_LAST_WATCHED_CHANNEL = "last_watched_channel_key"
        const val KEY_RAW_TS_REMUX_ENABLED = "raw_ts_remux_enabled"
        const val KEY_PARENTAL_CONTROL_PIN_HASH = "parental_control_pin_hash"
        const val KEY_PARENTAL_CONTROL_PIN_SALT = "parental_control_pin_salt"
        const val KEY_LAST_UPDATE_CHECK = "last_update_check_at"
        const val KEY_DISMISSED_UPDATE_TAG = "dismissed_update_tag"
        const val KEY_LICENSE_TIER = "license_tier"
        const val KEY_LICENSE_EXPIRY = "license_expires_at"
        const val KEY_LICENSE_SOURCE = "license_source"
        const val KEY_STORE_HAS_OFFERED = "store_has_offered_products"
        const val KEY_CLOCK_HIGH_WATER_MARK = "clock_high_water_mark"
    }
}
