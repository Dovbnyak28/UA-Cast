package app.zorik.player.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.zorik.player.core.i18n.AppLanguage
import app.zorik.player.core.i18n.LanguageResolver
import app.zorik.player.core.settings.BufferSize
import app.zorik.player.core.settings.ChannelLayout
import app.zorik.player.core.settings.IconDisplayMode
import app.zorik.player.core.settings.ListDensity
import app.zorik.player.core.settings.PlayerResizeMode
import app.zorik.player.guidedtour.GuidedTourStorage
import app.zorik.player.parentalcontrol.ParentalControlPinStorage
import app.zorik.player.performance.HeapBudget
import app.zorik.player.core.security.LicenseRecordCodec
import app.zorik.player.data.security.LicenseIntegrity
import app.zorik.player.favorites.FavoritesSortOrder
import app.zorik.player.log.AppLog
import app.zorik.player.premium.License
import app.zorik.player.premium.LicenseStorage
import app.zorik.player.premium.LicenseTier

private const val TAG = "AppPreferences"

/**
 * Small, synchronous wrapper around the app's single general-settings [SharedPreferences] file.
 * Values here are all tiny scalars; there is no need for the AtomicFile/versioned-snapshot
 * machinery used by the playlist/EPG/favorites caches.
 */
class AppPreferences(
    context: Context,
    /**
     * Whether this device can tag a licence at all - the one fact that tells an untagged record
     * written here apart from one somebody stripped the tag off. Injected only so both answers can
     * be tested: no Keystore exists under Robolectric, so a test could otherwise never reach the
     * device-can-tag half of that decision. Every caller in the app takes the default.
     */
    private val canTagLicense: () -> Boolean = LicenseIntegrity::isAvailable,
    /**
     * The heap this app may grow into, for [effectiveBufferSize]. Injected for the same reason as
     * [canTagLicense]: the unit-test harness runs with a 512MB heap, so a test could otherwise never
     * reach the tight-heap branch at all. Every caller in the app takes the default.
     */
    private val maxHeapBytes: () -> Long = { Runtime.getRuntime().maxMemory() },
) : ParentalControlPinStorage,
    LicenseStorage,
    GuidedTourStorage {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var language: AppLanguage
        get() = LanguageResolver.fromStoredCode(prefs.getString(KEY_LANGUAGE, null))
        set(value) = prefs.edit { putString(KEY_LANGUAGE, value.code) }

    /** Persisted ID of the selected visual style. Mapping that ID to the UI's `AppTheme` belongs
     * to `ui.theme.AppThemePreference`, so the data layer never depends on UI types. */
    var appThemeId: String?
        get() = prefs.getString(KEY_APP_THEME, null)
        set(value) = prefs.edit { putString(KEY_APP_THEME, value) }

    val hasChosenLanguage: Boolean
        get() = prefs.contains(KEY_LANGUAGE)

    /** User-configured or playlist-provided guide. Legacy built-in preset IDs are ignored. */
    var customEpgUrl: String?
        get() = prefs.getString(KEY_CUSTOM_EPG_URL, null)
        set(value) = prefs.edit { putString(KEY_CUSTOM_EPG_URL, value) }

    /** True only once the user has deliberately configured an EPG URL or accepted a suggestion.
     * Distinct from merely
     * *having* an active source, since [app.zorik.player.epg.EpgSourceAutoDetect] can set one automatically
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

    /** Null follows the device tier until the user explicitly chooses. */
    var reducedVisualEffects: Boolean?
        get() = if (prefs.contains("reduced_visual_effects")) {
            prefs.getBoolean("reduced_visual_effects", false)
        } else null
        set(value) = prefs.edit {
            if (value == null) remove("reduced_visual_effects") else putBoolean("reduced_visual_effects", value)
        }

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

    /** Once true, the user's explicit choice above wins forever over the heap-computed default -
     * the same contract [hasChosenIconDisplayMode] and [hasChosenListDensity] have, and for the
     * same reason: see [app.zorik.player.performance.HeapBudget.defaultBufferSize], which now
     * computes one per device rather than every device getting MEDIUM. */
    val hasChosenBufferSize: Boolean
        get() = prefs.contains(KEY_BUFFER_SIZE)

    /**
     * The buffer size to actually *use* - the user's choice when they made one, and otherwise the
     * one this device's heap can afford.
     *
     * **Read this, not [bufferSize], anywhere the value is going to be acted on.** The raw property
     * above is the stored value and answers a different question: it is what the backup exports and
     * what `hasChosenBufferSize` is about. It falls back to a flat `MEDIUM` for every device,
     * because that is what "nothing stored" means in storage terms.
     *
     * The distinction is not academic - it was a live defect. When the heap-derived default was
     * introduced it was resolved inside `SettingsController`, so the Settings screen showed the
     * smaller buffer while `PlayerViewModel`, which reads its own value straight from preferences,
     * went on allocating the larger one. The device the default exists for kept the 16MB that
     * helped run it out of memory, and the screen said otherwise.
     */
    val effectiveBufferSize: BufferSize
        get() = if (hasChosenBufferSize) bufferSize else HeapBudget.defaultBufferSize(maxHeapBytes())

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

    /** Android delivers preference changes on Main. Closing also rejects an already posted callback. */
    fun observePlaybackChanges(onChange: () -> Unit): AutoCloseable {
        val active = java.util.concurrent.atomic.AtomicBoolean(true)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                null, KEY_WRAP_AROUND, KEY_AUTO_SKIP_DEAD -> if (active.get()) onChange()
                else -> Unit
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return AutoCloseable {
            active.set(false)
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

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
     * [app.zorik.player.app.GuidedTourController]. Read together with [guidedTourVersion], which is
     * what lets a later edition of the tour offer itself once to someone who already saw the old
     * one. */
    override var guidedTourCompleted: Boolean
        get() = prefs.getBoolean(KEY_GUIDED_TOUR_COMPLETED, false)
        set(value) = prefs.edit { putBoolean(KEY_GUIDED_TOUR_COMPLETED, value) }

    /** 0 on a device that has never seen the tour, and on every device that predates the feature -
     * both of which should be offered it, which is exactly what 0 means to
     * [app.zorik.player.guidedtour.GuidedTourAvailability]. */
    override var guidedTourVersion: Int
        get() = prefs.getInt(KEY_GUIDED_TOUR_VERSION, 0)
        set(value) = prefs.edit { putInt(KEY_GUIDED_TOUR_VERSION, value) }

    /** Copied out, never handed back as the set SharedPreferences returns: that instance must not be
     * modified, and a caller adding to it would corrupt the stored value without saving it. */
    override var guidedTipsSeen: Set<String>
        get() = prefs.getStringSet(KEY_GUIDED_TIPS_SEEN, null)?.toSet().orEmpty()
        set(value) = prefs.edit { putStringSet(KEY_GUIDED_TIPS_SEEN, value.toSet()) }

    /** Legacy single-playlist label, only still read once during
     * `PlaylistRepository.migrateLegacySnapshotIfNeeded` - display names now live per-source in
     * `app.zorik.player.playlist.PlaylistSource`. */
    var playlistDisplayName: String?
        get() = prefs.getString(KEY_PLAYLIST_DISPLAY_NAME, null)
        set(value) = prefs.edit { putString(KEY_PLAYLIST_DISPLAY_NAME, value) }

    /** Which saved [app.zorik.player.playlist.PlaylistSource] is currently active - the single
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

    /** [app.zorik.player.favorites.FavoriteKey] of the last channel played, for Home's "continue
     * watching" card - the same identifier favorites use, not the raw stream URL, so this stays
     * consistent with how [app.zorik.player.data.favorites.FavoritesStore] identifies channels. */
    var lastWatchedChannelKey: String?
        get() = prefs.getString(KEY_LAST_WATCHED_CHANNEL, null)
        set(value) = prefs.edit { putString(KEY_LAST_WATCHED_CHANNEL, value) }

    /** Salted PBKDF2 hash of the parental-control PIN (see
     * [app.zorik.player.core.security.PinHasher]) - null means no PIN is set, which is also how
     * `app/ParentalControlController` decides whether the feature is enabled at all. Never the
     * plaintext PIN. Always set together with [parentalControlPinSalt]. */
    override var parentalControlPinHash: String?
        get() = prefs.getString(KEY_PARENTAL_CONTROL_PIN_HASH, null)
        set(value) = prefs.edit { putString(KEY_PARENTAL_CONTROL_PIN_HASH, value) }

    override var parentalControlPinSalt: String?
        get() = prefs.getString(KEY_PARENTAL_CONTROL_PIN_SALT, null)
        set(value) = prefs.edit { putString(KEY_PARENTAL_CONTROL_PIN_SALT, value) }

    override fun setParentalControlPin(hash: String, salt: String) {
        prefs.edit {
            putString(KEY_PARENTAL_CONTROL_PIN_HASH, hash)
            putString(KEY_PARENTAL_CONTROL_PIN_SALT, salt)
        }
    }

    override fun clearParentalControlPin() {
        prefs.edit {
            remove(KEY_PARENTAL_CONTROL_PIN_HASH)
            remove(KEY_PARENTAL_CONTROL_PIN_SALT)
        }
    }

    /**
     * The last licence this device saw. Null means nothing has been stored. Unknown or corrupted
     * records resolve to Lite; valid legacy tier names are decoded before the repository migrates
     * them, so integrity verification always checks the original signed payload.
     */
    override var storedLicense: License?
        get() = readTaggedLicense() ?: adoptLegacyLicense()
        set(value) {
            if (value == null) {
                prefs.edit { remove(KEY_LICENSE_RECORD); removeLegacyLicenseKeys() }
                return
            }
            val payload = LicenseRecordCodec.payload(value.tier.name, value.expiresAtMillis, value.source)
            val mac = LicenseIntegrity.sign(payload)
            prefs.edit {
                // A device that cannot tag stores the payload with an empty tag rather than nothing:
                // the licence is what the user paid for, and a Keystore that will not co-operate is
                // not a reason to forget it. [licenseFrom] believes such a record back only while
                // this device still cannot tag - see the note there for why that condition is the
                // whole difference between an honest untagged record and a stripped tag.
                putString(KEY_LICENSE_RECORD, LicenseRecordCodec.encode(payload, mac.orEmpty()))
                removeLegacyLicenseKeys()
            }
        }

    /**
     * The licence, if the record on disk is one this app wrote.
     *
     * A record whose tag does not match is not repaired and not reported - it resolves to
     * [License.FREE], the same answer an unrecognised tier already gets. Silence is deliberate: the
     * person who edited the file is the only one who would read a message about it, and the honest
     * outcome for the app is simply not to believe it.
     */
    private fun readTaggedLicense(): License? =
        prefs.getString(KEY_LICENSE_RECORD, null)?.let(::licenseFrom)

    /**
     * One record, believed or not.
     *
     * The three ways it can fail to be this app's - not a record at all, too few fields, a tag that
     * does not match - are one decision rather than three, because they have one answer.
     *
     * **An empty tag is answered by asking the device, not by a fixed rule**, because the two things
     * it can mean pull in opposite directions. Written by the setter, it means this device could not
     * produce a tag, and refusing it would revoke a licence the user paid for - which
     * [LicenseIntegrity] states plainly is the worse bug of the two. Written by hand, it is the
     * cheapest attack there is: open the file, delete everything after the last separator, keep the
     * tier. [canTagLicense] is what separates them, and it is why [LicenseIntegrity.isAvailable]
     * exists. So on any device that can tag - which is very nearly all of them - a missing tag is
     * still a missing licence, and only a device that genuinely cannot tag believes an untagged one.
     *
     * The cost of drawing it here: a device that could not tag when the record was written and can
     * tag by the time it is read - a Keystore repaired by a system update - drops to the free tier,
     * and its owner restores the purchase to get it back. The alternative, re-signing whatever is
     * found untagged, would hand every stripped tag a fresh valid one, which is the whole attack.
     */
    private fun licenseFrom(stored: String): License {
        val decoded = LicenseRecordCodec.decode(stored)
        val fields = decoded?.first?.split('|').orEmpty()
        val tagIsGood = when {
            decoded == null -> false
            decoded.second.isEmpty() -> !canTagLicense()
            else -> LicenseIntegrity.verify(decoded.first, decoded.second)
        }
        val ours = tagIsGood && fields.size >= LICENSE_FIELD_COUNT
        if (!ours) {
            AppLog.w(TAG) { "The stored licence is not one this app wrote; treating it as free" }
            return License.FREE
        }
        val tier = LicenseTier.entries.firstOrNull { it.name == fields[0] } ?: LicenseTier.FREE
        return License(tier, fields[1].toLongOrNull(), fields[2].takeIf { it != "-" })
    }

    /**
     * A licence written before records were tagged, adopted once and rewritten as one.
     *
     * The alternative - refusing anything untagged - would drop every existing paying install to
     * the free tier on the launch after an update, which is a far worse bug than the tampering this
     * is meant to make expensive. After this runs once there is no untagged form left to fall back
     * to, so removing a tag from then on removes the licence with it.
     */
    private fun adoptLegacyLicense(): License? {
        val storedTier = prefs.getString(KEY_LICENSE_TIER, null) ?: return null
        val tier = LicenseTier.entries.firstOrNull { it.name == storedTier } ?: LicenseTier.FREE
        val expiry = if (prefs.contains(KEY_LICENSE_EXPIRY)) prefs.getLong(KEY_LICENSE_EXPIRY, 0L) else null
        return License(tier, expiry, prefs.getString(KEY_LICENSE_SOURCE, null)).also { storedLicense = it }
    }

    private fun SharedPreferences.Editor.removeLegacyLicenseKeys() {
        remove(KEY_LICENSE_TIER)
        remove(KEY_LICENSE_EXPIRY)
        remove(KEY_LICENSE_SOURCE)
    }

    /** See [LicenseStorage.clockHighWaterMark] - the newest time this app has ever seen. */
    override var clockHighWaterMark: Long
        get() = prefs.getLong(KEY_CLOCK_HIGH_WATER_MARK, 0L)
        set(value) = prefs.edit { putLong(KEY_CLOCK_HIGH_WATER_MARK, value) }

    private companion object {
        const val PREFS_NAME = "zorik_prefs"
        const val KEY_LANGUAGE = "language_code"
        const val KEY_APP_THEME = "app_theme"
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
        const val KEY_GUIDED_TIPS_SEEN = "guided_tips_seen"
        const val KEY_PLAYLIST_DISPLAY_NAME = "playlist_display_name"
        const val KEY_ACTIVE_PLAYLIST_SOURCE_ID = "active_playlist_source_id"
        const val KEY_LAST_WATCHED_CHANNEL = "last_watched_channel_key"
        const val KEY_RAW_TS_REMUX_ENABLED = "raw_ts_remux_enabled"
        const val KEY_PARENTAL_CONTROL_PIN_HASH = "parental_control_pin_hash"
        const val KEY_PARENTAL_CONTROL_PIN_SALT = "parental_control_pin_salt"
        const val KEY_LICENSE_TIER = "license_tier"
        const val KEY_LICENSE_EXPIRY = "license_expires_at"
        const val KEY_LICENSE_SOURCE = "license_source"
        const val KEY_LICENSE_RECORD = "license_record"
        const val LICENSE_FIELD_COUNT = 3
        const val KEY_CLOCK_HIGH_WATER_MARK = "clock_high_water_mark"
    }
}
